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
        raise RuntimeError("MONGODB_URI environment variable is not set")

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


async def disconnect_db() -> None:
    global _client
    if _client:
        _client.close()
        logger.info("MongoDB disconnected gracefully")
