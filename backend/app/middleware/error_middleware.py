"""Error handlers — replaces middleware/errorMiddleware.ts"""
from fastapi import Request, HTTPException
from fastapi.responses import JSONResponse
from app.utils.logger import logger
import os


class AppError(Exception):
    def __init__(self, message: str, status_code: int):
        super().__init__(message)
        self.status_code = status_code


async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"success": False, "error": exc.detail},
    )


async def generic_exception_handler(request: Request, exc: Exception):
    if isinstance(exc, AppError):
        return JSONResponse(
            status_code=exc.status_code,
            content={"success": False, "error": str(exc)},
        )
    logger.error(f"Unhandled error: {exc}", exc_info=True)
    content = {"success": False, "error": "Internal server error"}
    if os.getenv("NODE_ENV") == "development":
        content["detail"] = str(exc)
    return JSONResponse(status_code=500, content=content)
