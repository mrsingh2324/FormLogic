"""MongoDB connection via Beanie (Motor async driver) — replaces utils/database.ts"""
import os
from motor.motor_asyncio import AsyncIOMotorClient
from beanie import init_beanie

from app.utils.logger import logger
from app.models.models import (
    User, WorkoutSession, ExercisePlan,
    FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
)

from typing import Optional

_client: Optional[AsyncIOMotorClient] = None


async def connect_db() -> None:
    global _client
    uri = os.getenv("MONGODB_URI")
    if not uri:
        msg = ("MONGODB_URI is not set — database unavailable. "
               "Fix: gcloud run services update formlogic-backend "
               "--region=asia-south1 --set-env-vars MONGODB_URI=mongodb+srv://...")
        print(f"[DB] ❌ {msg}", flush=True)
        logger.error(msg)
        return

    # Show a safe URI preview (hide password)
    try:
        import re as _re
        safe_uri = _re.sub(r":[^:@]+@", ":***@", uri)
    except Exception:
        safe_uri = uri[:30] + "..."
    print(f"[DB] Connecting to MongoDB: {safe_uri}", flush=True)

    try:
        _client = AsyncIOMotorClient(uri, maxPoolSize=10, serverSelectionTimeoutMS=5000)
        db_name = uri.rsplit("/", 1)[-1].split("?")[0] or "formlogic"
        print(f"[DB] Using database: '{db_name}'", flush=True)
        db = _client[db_name]

        print("[DB] Running init_beanie with document models...", flush=True)
        await init_beanie(
            database=db,
            document_models=[
                User, WorkoutSession, ExercisePlan,
                FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
            ],
        )
        print("[DB] ✅ MongoDB connected and Beanie initialised", flush=True)
        logger.info("✅ MongoDB connected")
    except Exception as e:
        import traceback as _tb
        print(f"[DB] ❌ MongoDB connection FAILED: {e}", flush=True)
        print(f"[DB] Traceback:\n{_tb.format_exc()}", flush=True)
        logger.error(f"Failed to connect to MongoDB: {e}")
        logger.error("Help: Check MongoDB Atlas → Network Access → allow 0.0.0.0/0 "
                     "and verify MONGODB_URI is correct")
        _client = None


async def disconnect_db() -> None:
    global _client
    if _client:
        _client.close()
        logger.info("MongoDB disconnected gracefully")
