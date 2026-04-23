"""
FormLogic AI — Python/FastAPI Backend
Converted from Node.js/Express/TypeScript
"""
import os
from dotenv import load_dotenv

load_dotenv()  # Load environment variables from .env

from contextlib import asynccontextmanager
import time
import uuid
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse
import uvicorn
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from app.utils.database import connect_db, disconnect_db
from app.utils.startup import validate_secrets, get_health_status
from app.utils.logger import logger
from app.middleware.error_middleware import generic_exception_handler
from app.routes import (
    auth_router, social_auth_router, user_router, workout_router,
    nutrition_router, plan_router, achievement_router, upload_router,
    privacy_router, tracking_router, notification_router, webhook_router, ai_router, report_router,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    validate_secrets()
    await connect_db()
    # Rebuild notification reminder schedule from DB
    try:
        from app.services.notification_service import rebuild_reminder_schedule
        await rebuild_reminder_schedule()
    except Exception as e:
        logger.warning(f"Could not rebuild reminder schedule: {e}")
    # Log Celery beat status — beat runs as a separate process (see app/worker.py)
    redis_url = os.getenv("REDIS_URL", "")
    if redis_url:
        logger.info(f"Celery broker configured at {redis_url[:20]}... — start beat with: celery -A app.worker beat")
    else:
        logger.warning("REDIS_URL not set — Celery beat scheduling disabled")
    logger.info("🚀 FormLogic API v1 started")
    yield
    await disconnect_db()
    logger.info("FormLogic API shut down")


app = FastAPI(
    title="FormLogic AI API",
    version="1.0.0",
    description="Real-time AI personal trainer API",
    docs_url="/api/docs",
    openapi_url="/api/docs.json",
    lifespan=lifespan,
)

app.state.metrics = {
    "requests_total": 0,
    "requests_4xx": 0,
    "requests_5xx": 0,
    "latency_ms_sum": 0.0,
}

limiter = Limiter(key_func=get_remote_address, default_limits=["200/15minutes"])
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ─── CORS ─────────────────────────────────────────────────────────────────────
cors_origins = os.getenv("CORS_ORIGIN", "*").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(GZipMiddleware, minimum_size=1000)
app.add_exception_handler(Exception, generic_exception_handler)

@app.middleware("http")
async def request_trace_middleware(request: Request, call_next):
    started = time.perf_counter()
    rid = request.headers.get("x-request-id") or str(uuid.uuid4())
    app.state.metrics["requests_total"] += 1
    try:
        response = await call_next(request)
    except Exception:
        app.state.metrics["requests_5xx"] += 1
        raise
    elapsed_ms = (time.perf_counter() - started) * 1000
    app.state.metrics["latency_ms_sum"] += elapsed_ms
    if 400 <= response.status_code < 500:
        app.state.metrics["requests_4xx"] += 1
    if response.status_code >= 500:
        app.state.metrics["requests_5xx"] += 1
    response.headers["x-request-id"] = rid
    response.headers["x-response-time-ms"] = f"{elapsed_ms:.2f}"
    logger.info(f"[{rid}] {request.method} {request.url.path} -> {response.status_code} in {elapsed_ms:.1f}ms")
    return response

# ─── Health ───────────────────────────────────────────────────────────────────
@app.get("/health", tags=["health"])
async def health_check():
    """Always return 200 - actual status in JSON body.
    Cloud Run requires 200 to route traffic to container."""
    try:
        health = await get_health_status()
        return JSONResponse(content=health, status_code=200)
    except Exception as e:
        from app.utils.logger import logger
        import traceback
        logger.error(f"Health check error: {e}\n{traceback.format_exc()}")
        return JSONResponse(
            content={
                "status": "down",
                "error": str(e),
                "help": "Check Cloud Run logs"
            },
            status_code=200
        )

@app.get("/ops/metrics", tags=["ops"])
async def ops_metrics():
    m = app.state.metrics
    total = max(1, m["requests_total"])
    return {
        "requests_total": m["requests_total"],
        "requests_4xx": m["requests_4xx"],
        "requests_5xx": m["requests_5xx"],
        "avg_latency_ms": round(m["latency_ms_sum"] / total, 2),
    }

# ─── Routes ───────────────────────────────────────────────────────────────────
app.include_router(auth_router,          prefix="/api/v1/auth",          tags=["auth"])
app.include_router(social_auth_router,   prefix="/api/v1/auth/social",   tags=["social-auth"])
app.include_router(user_router,          prefix="/api/v1/users",         tags=["users"])
app.include_router(workout_router,       prefix="/api/v1/workouts",      tags=["workouts"])
app.include_router(nutrition_router,     prefix="/api/v1/nutrition",     tags=["nutrition"])
app.include_router(plan_router,          prefix="/api/v1/plans",         tags=["plans"])
app.include_router(achievement_router,   prefix="/api/v1/achievements",  tags=["achievements"])
app.include_router(upload_router,        prefix="/api/v1/upload",        tags=["upload"])
app.include_router(privacy_router,       prefix="/api/v1/privacy",       tags=["privacy"])
app.include_router(tracking_router,      prefix="/api/v1/tracking",      tags=["tracking"])
app.include_router(notification_router,  prefix="/api/v1/notifications", tags=["notifications"])
app.include_router(ai_router,            prefix="/api/v1/ai",            tags=["ai"])
app.include_router(report_router,        prefix="/api/v1/reports",       tags=["reports"])
app.include_router(webhook_router,       prefix="/webhooks",             tags=["webhooks"])

if __name__ == "__main__":
    port = int(os.getenv("PORT", "8080"))
    uvicorn.run("app.main:app", host="0.0.0.0", port=port, reload=True)
