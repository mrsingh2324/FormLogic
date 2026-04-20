"""
Auth routes — replaces controllers/authController.ts + routes/authRoutes.ts
"""
import hashlib
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, HTTPException, Request, status
from pydantic import BaseModel, EmailStr, Field

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
from app.models.models import User
from app.utils.jwt_utils import generate_access_token, generate_refresh_token, verify_refresh_token
from app.middleware.error_middleware import AppError
from app.services.email_service import send_verification_email, send_password_reset_email, send_welcome_email
from app.utils.logger import logger

router = APIRouter()


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


# ─── Schemas ──────────────────────────────────────────────────────────────────

class RegisterRequest(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    age: Optional[int] = Field(None, ge=13, le=120)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class RefreshRequest(BaseModel):
    refresh_token: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ResetPasswordRequest(BaseModel):
    token: str
    password: str = Field(min_length=8)


# ─── Register ─────────────────────────────────────────────────────────────────

@router.post("/register", status_code=201)
@limiter.limit("10/15minutes")
async def register(request: Request, body: RegisterRequest):
    existing = await User.find_one(User.email == body.email)
    if existing:
        raise HTTPException(409, "An account with this email already exists.")

    user = User(
        name=body.name,
        email=body.email,
        password_hash=User.hash_password(body.password),
    )
    verify_token = user.generate_email_verification_token()
    await user.insert()

    try:
        await send_verification_email(body.email, body.name, verify_token)
    except Exception as e:
        logger.warning(f"Failed to send verification email to {body.email}: {e}")

    access_token = generate_access_token(str(user.id), user.email)
    refresh_token = generate_refresh_token(str(user.id), user.email)

    user.refresh_tokens.append(_hash_token(refresh_token))
    await user.save()

    return {
        "success": True,
        "data": {
            "user": user.safe_dict(),
            "access_token": access_token,
            "refresh_token": refresh_token,
            "message": "Please check your email to verify your account.",
        },
    }


# ─── Login ────────────────────────────────────────────────────────────────────

@router.post("/login")
@limiter.limit("10/15minutes")
async def login(request: Request, body: LoginRequest):
    user = await User.find_one(User.email == body.email, User.is_active == True)
    if not user:
        raise HTTPException(401, "Invalid email or password.")

    if user.is_locked():
        raise HTTPException(423, "Account temporarily locked due to multiple failed attempts. Try again in 30 minutes.")

    if not user.verify_password(body.password):
        # Increment login attempts
        user.login_attempts += 1
        from app.models.models import MAX_LOGIN_ATTEMPTS, LOCK_DURATION_MINUTES
        if user.login_attempts >= MAX_LOGIN_ATTEMPTS and not user.is_locked():
            from datetime import timedelta
            user.lock_until = datetime.utcnow() + timedelta(minutes=LOCK_DURATION_MINUTES)
        await user.save()
        raise HTTPException(401, "Invalid email or password.")

    user.login_attempts = 0
    user.lock_until = None
    user.last_active_at = datetime.utcnow()

    access_token = generate_access_token(str(user.id), user.email)
    refresh_token = generate_refresh_token(str(user.id), user.email)

    hashed_new = _hash_token(refresh_token)
    user.refresh_tokens = user.refresh_tokens[-4:] + [hashed_new]
    await user.save()

    return {
        "success": True,
        "data": {
            "user": user.safe_dict(),
            "access_token": access_token,
            "refresh_token": refresh_token,
        },
    }


# ─── Refresh token ────────────────────────────────────────────────────────────

@router.post("/refresh")
async def refresh(body: RefreshRequest):
    payload = verify_refresh_token(body.refresh_token)
    user = await User.get(payload["user_id"])
    if not user:
        raise HTTPException(401, "Invalid refresh token")

    hashed_incoming = _hash_token(body.refresh_token)
    if hashed_incoming not in user.refresh_tokens:
        user.refresh_tokens = []
        await user.save()
        raise HTTPException(401, "Refresh token reuse detected. Please log in again.")

    new_access = generate_access_token(str(user.id), user.email)
    new_refresh = generate_refresh_token(str(user.id), user.email)

    user.refresh_tokens = [t for t in user.refresh_tokens if t != hashed_incoming]
    user.refresh_tokens.append(_hash_token(new_refresh))
    await user.save()

    return {
        "success": True,
        "data": {
            "user": user.safe_dict(),
            "access_token": new_access,
            "refresh_token": new_refresh,
        },
    }


# ─── Logout ───────────────────────────────────────────────────────────────────

@router.post("/logout")
async def logout(body: RefreshRequest):
    try:
        payload = verify_refresh_token(body.refresh_token)
        user = await User.get(payload["user_id"])
        if user:
            hashed = _hash_token(body.refresh_token)
            user.refresh_tokens = [t for t in user.refresh_tokens if t != hashed]
            await user.save()
    except Exception:
        pass
    return {"success": True, "message": "Logged out."}


# ─── Verify email ─────────────────────────────────────────────────────────────

@router.get("/verify-email/{token}")
async def verify_email(token: str):
    import hashlib
    hashed = hashlib.sha256(token.encode()).hexdigest()
    user = await User.find_one(
        User.email_verification_token == hashed,
        User.email_verification_expires > datetime.utcnow(),
    )
    if not user:
        raise HTTPException(400, "Invalid or expired verification link. Please request a new one.")

    user.is_email_verified = True
    user.email_verification_token = None
    user.email_verification_expires = None
    await user.save()

    try:
        await send_welcome_email(user.email, user.name)
    except Exception:
        pass

    return {"success": True, "message": "Email verified! You can now log in."}


# ─── Resend verification ──────────────────────────────────────────────────────

class ResendRequest(BaseModel):
    email: EmailStr


@router.post("/resend-verification")
async def resend_verification(body: ResendRequest):
    user = await User.find_one(User.email == body.email, User.is_active == True)
    if not user:
        return {"success": True, "message": "If that email exists, a verification link has been sent."}
    if user.is_email_verified:
        return {"success": True, "message": "Email is already verified."}

    token = user.generate_email_verification_token()
    await user.save()
    await send_verification_email(user.email, user.name, token)
    return {"success": True, "message": "Verification email resent."}


# ─── Forgot password ──────────────────────────────────────────────────────────

@router.post("/forgot-password")
@limiter.limit("5/15minutes")
async def forgot_password(request: Request, body: ForgotPasswordRequest):
    success_msg = "If an account with that email exists, a reset link has been sent."
    user = await User.find_one(User.email == body.email, User.is_active == True)
    if not user:
        return {"success": True, "message": success_msg}

    token = user.generate_password_reset_token()
    await user.save()

    try:
        await send_password_reset_email(user.email, user.name, token)
    except Exception as e:
        logger.warning(f"Password reset email failed: {e}")

    return {"success": True, "message": success_msg}


# ─── Reset password ───────────────────────────────────────────────────────────

@router.post("/reset-password")
@limiter.limit("5/15minutes")
async def reset_password(request: Request, body: ResetPasswordRequest):
    import hashlib
    hashed = hashlib.sha256(body.token.encode()).hexdigest()
    user = await User.find_one(
        User.password_reset_token == hashed,
        User.password_reset_expires > datetime.utcnow(),
    )
    if not user:
        raise HTTPException(400, "Password reset link is invalid or has expired.")

    user.password_hash = User.hash_password(body.password)
    user.password_reset_token = None
    user.password_reset_expires = None
    user.refresh_tokens = []
    user.login_attempts = 0
    user.lock_until = None
    await user.save()

    return {"success": True, "message": "Password reset successfully. Please log in again."}
