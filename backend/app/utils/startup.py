"""Startup validation & health checks — replaces utils/startup.ts"""
import os
import sys
from app.utils.logger import logger

REQUIRED_SECRETS = ["MONGODB_URI", "JWT_SECRET", "JWT_REFRESH_SECRET", "SMTP_USER", "SMTP_PASS"]
REQUIRED_IN_PRODUCTION = ["GCS_BUCKET_NAME", "GCS_PROJECT_ID", "REDIS_URL"]


def validate_secrets() -> None:
    missing = [k for k in REQUIRED_SECRETS if not os.getenv(k)]
    if os.getenv("NODE_ENV") == "production":
        missing += [k for k in REQUIRED_IN_PRODUCTION if not os.getenv(k)]

    if missing:
        logger.error(f"❌ Missing required environment variables:\n  " + "\n  ".join(missing))
        logger.error("⚠️  App will continue but features requiring these vars will fail")
        # Don't exit - let the app start and report errors via /health
        return

    if len(os.getenv("JWT_SECRET", "")) < 32:
        logger.error("JWT_SECRET must be at least 32 characters")
        return
    if len(os.getenv("JWT_REFRESH_SECRET", "")) < 32:
        logger.error("JWT_REFRESH_SECRET must be at least 32 characters")
        return

    logger.info("✅ All required secrets validated")


async def get_health_status() -> dict:
    import time
    checks: dict = {}

    # MongoDB ping
    mongodb_uri = os.getenv("MONGODB_URI", "")
    if not mongodb_uri:
        checks["mongodb"] = {
            "status": "down",
            "reason": "MONGODB_URI environment variable is not set",
            "help": "Add to Cloud Run: gcloud run services update formlogic-backend --region=asia-south1 --set-env-vars MONGODB_URI=your_mongodb_uri"
        }
    else:
        try:
            from motor.motor_asyncio import AsyncIOMotorClient
            client = AsyncIOMotorClient(mongodb_uri, serverSelectionTimeoutMS=3000)
            start = time.monotonic()
            await client.admin.command("ping")
            checks["mongodb"] = {"status": "ok", "latency_ms": round((time.monotonic() - start) * 1000)}
            client.close()
        except Exception as e:
            checks["mongodb"] = {
                "status": "down",
                "reason": f"Cannot connect to MongoDB: {type(e).__name__}: {e}",
                "mongodb_uri_preview": mongodb_uri[:50] + "..." if len(mongodb_uri) > 50 else mongodb_uri,
                "help": "1) Check MongoDB Atlas Network Access allows 0.0.0.0/0, 2) Verify URI is correct, 3) Ensure cluster is running"
            }

    # Redis ping
    redis_url = os.getenv("REDIS_URL", "")
    if not redis_url:
        checks["redis"] = {
            "status": "not_configured",
            "reason": "REDIS_URL not set (optional - Celery background tasks will be disabled)",
            "note": "This is not critical - main API will work fine without Redis"
        }
    else:
        try:
            import redis.asyncio as redis
            r = redis.from_url(redis_url, socket_connect_timeout=3)
            start = time.monotonic()
            await r.ping()
            checks["redis"] = {"status": "ok", "latency_ms": round((time.monotonic() - start) * 1000)}
            await r.aclose()
        except Exception as e:
            checks["redis"] = {
                "status": "down",
                "reason": f"Cannot connect to Redis: {type(e).__name__}: {e}",
                "help": "Check Redis URL and ensure Redis is accessible from Cloud Run"
            }

    all_ok = all(v["status"] == "ok" for v in checks.values())
    any_down = any(v["status"].startswith("down") for v in checks.values())

    return {
        "status": "ok" if all_ok else "down" if any_down else "degraded",
        "checks": checks,
        "timestamp": time.time(),
    }
