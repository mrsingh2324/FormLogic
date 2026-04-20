"""
conftest.py — pytest fixtures for FormLogic backend tests
"""
import asyncio
import os
import pytest
import pytest_asyncio
from motor.motor_asyncio import AsyncIOMotorClient
from beanie import init_beanie

# Set test env vars before any app imports
os.environ.setdefault("MONGODB_URI", "mongodb://localhost:27017/formlogic_test")
os.environ.setdefault("JWT_SECRET",          "test-secret-that-is-at-least-32-chars-long!")
os.environ.setdefault("JWT_REFRESH_SECRET",  "test-refresh-that-is-at-least-32-chars!")
os.environ.setdefault("SMTP_USER",  "test@example.com")
os.environ.setdefault("SMTP_PASS",  "testpass")
os.environ.setdefault("REDIS_URL",  "redis://localhost:6379/0")
os.environ.setdefault("NODE_ENV",   "test")


# Note: event_loop fixture is handled by pytest-asyncio >= 0.23 based on mark/config.


@pytest_asyncio.fixture(autouse=True)
async def init_db():
    """Initialise Beanie with a test database, drop it after tests."""
    from app.models.models import (
        User, WorkoutSession, ExercisePlan,
        FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
    )
    uri = os.environ["MONGODB_URI"]
    client = AsyncIOMotorClient(uri)
    db_name = "formlogic_test"
    db = client[db_name]

    await init_beanie(database=db, document_models=[
        User, WorkoutSession, ExercisePlan,
        FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
    ])
    yield
    # Clean up test database
    await client.drop_database(db_name)
    client.close()


@pytest_asyncio.fixture(autouse=True)
async def clean_collections(init_db):
    """Wipe all collections between tests for isolation."""
    from app.models.models import (
        User, WorkoutSession, ExercisePlan,
        FoodItem, MealLog, WaterLog, WeightLog, UserAchievement,
    )
    yield
    for model in [User, WorkoutSession, ExercisePlan, FoodItem, MealLog, WaterLog, WeightLog, UserAchievement]:
        await model.find_all().delete()
