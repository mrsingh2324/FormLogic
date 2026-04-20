"""JWT token generation and verification — Python equivalent of utils/jwt.ts"""
import os
from datetime import datetime, timedelta
from typing import TypedDict

from jose import JWTError, jwt
from fastapi import HTTPException, status


class TokenPayload(TypedDict):
    user_id: str
    email: str


def _access_secret() -> str:
    s = os.getenv("JWT_SECRET", "")
    if not s:
        raise RuntimeError("JWT_SECRET not configured")
    return s


def _refresh_secret() -> str:
    s = os.getenv("JWT_REFRESH_SECRET", "")
    if not s:
        raise RuntimeError("JWT_REFRESH_SECRET not configured")
    return s


def _parse_duration(val: str, default_minutes: int) -> timedelta:
    """Parse '15m', '7d', '1h' style strings."""
    try:
        if val.endswith("m"):
            return timedelta(minutes=int(val[:-1]))
        if val.endswith("h"):
            return timedelta(hours=int(val[:-1]))
        if val.endswith("d"):
            return timedelta(days=int(val[:-1]))
    except Exception:
        pass
    return timedelta(minutes=default_minutes)


def generate_access_token(user_id: str, email: str) -> str:
    expires_in = os.getenv("JWT_EXPIRES_IN", "15m")
    expire = datetime.utcnow() + _parse_duration(expires_in, 15)
    return jwt.encode(
        {"user_id": user_id, "email": email, "exp": expire},
        _access_secret(),
        algorithm="HS256",
    )


def generate_refresh_token(user_id: str, email: str) -> str:
    expires_in = os.getenv("JWT_REFRESH_EXPIRES_IN", "7d")
    expire = datetime.utcnow() + _parse_duration(expires_in, 10080)
    return jwt.encode(
        {"user_id": user_id, "email": email, "exp": expire},
        _refresh_secret(),
        algorithm="HS256",
    )


def verify_access_token(token: str) -> TokenPayload:
    try:
        payload = jwt.decode(token, _access_secret(), algorithms=["HS256"])
        return {"user_id": payload["user_id"], "email": payload["email"]}
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        )


def verify_refresh_token(token: str) -> TokenPayload:
    try:
        payload = jwt.decode(token, _refresh_secret(), algorithms=["HS256"])
        return {"user_id": payload["user_id"], "email": payload["email"]}
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
        )
