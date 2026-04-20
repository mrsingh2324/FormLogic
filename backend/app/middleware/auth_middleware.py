"""Auth middleware / FastAPI dependency — replaces middleware/authMiddleware.ts"""
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from app.utils.jwt_utils import verify_access_token
from app.models.models import User

bearer_scheme = HTTPBearer()


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
) -> User:
    payload = verify_access_token(credentials.credentials)
    user = await User.get(payload["user_id"])
    if not user or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found or inactive")
    return user


async def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
) -> str:
    payload = verify_access_token(credentials.credentials)
    return payload["user_id"]
