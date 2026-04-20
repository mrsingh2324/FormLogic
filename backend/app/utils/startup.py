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
        sys.exit(1)

    if len(os.getenv("JWT_SECRET", "")) < 32:
        logger.error("JWT_SECRET must be at least 32 characters")
        sys.exit(1)
    if len(os.getenv("JWT_REFRESH_SECRET", "")) < 32:
        logger.error("JWT_REFRESH_SECRET must be at least 32 characters")
        sys.exit(1)

    logger.info("✅ All required secrets validated")


async def get_health_status() -> dict:
    checks: dict = {}

    # MongoDB ping
    try:
        from motor.motor_asyncio import AsyncIOMotorClient
        client = AsyncIOMotorClient(os.getenv("MONGODB_URI", ""), serverSelectionTimeoutMS=3000)
        import time; start = time.monotonic()
        await client.admin.command("ping")
        checks["mongodb"] = {"status": "ok", "latency_ms": round((time.monotonic() - start) * 1000)}
        client.close()
    except Exception as e:
        checks["mongodb"] = {"status": f"down: {e}"}

    # Redis ping
    if os.getenv("REDIS_URL"):
        try:
            import redis.asyncio as redis; import time
            r = redis.from_url(os.getenv("REDIS_URL", ""), socket_connect_timeout=3)
            start = time.monotonic()
            await r.ping()
            checks["redis"] = {"status": "ok", "latency_ms": round((time.monotonic() - start) * 1000)}
            await r.aclose()
        except Exception as e:
            checks["redis"] = {"status": f"down: {e}"}

    all_ok = all(v["status"] == "ok" for v in checks.values())
    any_down = any(v["status"].startswith("down") for v in checks.values())

    return {
        "status": "ok" if all_ok else "down" if any_down else "degraded",
        "checks": checks,
    }
