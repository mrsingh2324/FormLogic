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
        logger.error("MONGODB_URI environment variable is not set - app will run but database features unavailable")
        logger.error("Help: Add MONGODB_URI to Cloud Run: gcloud run services update formlogic-backend --region=asia-south1 --set-env-vars MONGODB_URI=your_uri")
        return

    try:
        _client = AsyncIOMotorClient(uri, maxPoolSize=10, serverSelectionTimeoutMS=5000)
        db_name = uri.rsplit("/", 1)[-1].split("?")[0] or "formlogic"
        db = _client[db_name]

        await init_beanie(
            database=db,
            document_models=[
                User, WorkoutSession, ExercisePlan,
                FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
            ],
        )
        logger.info("✅ MongoDB connected")
    except Exception as e:
        logger.error(f"Failed to connect to MongoDB: {e}")
        logger.error("Help: Check MongoDB Atlas Network Access allows 0.0.0.0/0 and URI is correct")
        _client = None


async def disconnect_db() -> None:
    global _client
    if _client:
        _client.close()
        logger.info("MongoDB disconnected gracefully")
