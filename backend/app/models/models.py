"""
FormLogic AI - MongoDB Models (Beanie ODM)
Equivalent to TypeScript Mongoose models
"""

from __future__ import annotations
from datetime import datetime, timedelta
from typing import Optional, List
from enum import Enum
import hashlib
import secrets

import bcrypt
from beanie import Document, Indexed
from pydantic import BaseModel, Field, EmailStr
from pymongo import IndexModel, TEXT


# ─── Enums ────────────────────────────────────────────────────────────────────

class Gender(str, Enum):
    MALE = "male"
    FEMALE = "female"
    OTHER = "other"
    PREFER_NOT = "prefer_not_to_say"


class FitnessLevel(str, Enum):
    BEGINNER = "beginner"
    INTERMEDIATE = "intermediate"
    ADVANCED = "advanced"


class MealType(str, Enum):
    BREAKFAST = "breakfast"
    MORNING_SNACK = "morning_snack"
    LUNCH = "lunch"
    EVENING_SNACK = "evening_snack"
    DINNER = "dinner"
    POST_WORKOUT = "post_workout"


class Mood(str, Enum):
    GREAT = "great"
    GOOD = "good"
    OKAY = "okay"
    TIRED = "tired"
    BAD = "bad"


class AchievementRarity(str, Enum):
    COMMON = "common"
    RARE = "rare"
    EPIC = "epic"
    LEGENDARY = "legendary"


# ─── Embedded models ──────────────────────────────────────────────────────────

class UserProfile(BaseModel):
    age: Optional[int] = None
    weight: Optional[float] = None
    height: Optional[float] = None
    gender: Optional[Gender] = None
    fitness_level: FitnessLevel = FitnessLevel.BEGINNER
    goals: List[str] = []
    equipment: List[str] = []
    dietary_restrictions: List[str] = []
    preferred_workout_days: List[int] = []
    preferred_workout_time: Optional[str] = None
    profile_picture_url: Optional[str] = None
    timezone: str = "Asia/Kolkata"
    # Body composition self-assessment (captured during onboarding)
    current_physique_description: Optional[str] = None
    target_physique_description: Optional[str] = None
    current_body_type: Optional[str] = None
    target_body_type: Optional[str] = None
    estimated_body_fat_pct: Optional[float] = None


class SubscriptionInfo(BaseModel):
    status: str = "free"
    product_id: Optional[str] = None
    updated_at: Optional[datetime] = None


class ConsentInfo(BaseModel):
    privacy_policy_version: Optional[str] = None
    privacy_policy_accepted_at: Optional[datetime] = None
    marketing_opt_in: bool = False
    analytics_opt_in: bool = False


# ─── User Document ────────────────────────────────────────────────────────────

MAX_LOGIN_ATTEMPTS = 5
LOCK_DURATION_MINUTES = 30



class ReminderConfig(BaseModel):
    enabled: bool = False
    hour: int = 8
    minute: int = 0
    workout_name: str = "your workout"

class User(Document):
    name: str
    email: Indexed(EmailStr, unique=True)
    password_hash: str
    profile: UserProfile = Field(default_factory=UserProfile)
    refresh_tokens: List[str] = []
    is_active: bool = True
    is_email_verified: bool = False
    email_verification_token: Optional[str] = None
    email_verification_expires: Optional[datetime] = None
    password_reset_token: Optional[str] = None
    password_reset_expires: Optional[datetime] = None
    login_attempts: int = 0
    lock_until: Optional[datetime] = None
    last_active_at: Optional[datetime] = None
    expo_push_token: Optional[str] = None
    coach_threads: List[dict] = []
    reminder: ReminderConfig = Field(default_factory=ReminderConfig)
    subscription: SubscriptionInfo = Field(default_factory=SubscriptionInfo)
    consent: ConsentInfo = Field(default_factory=ConsentInfo)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "users"

    def verify_password(self, candidate: str) -> bool:
        return bcrypt.checkpw(candidate.encode(), self.password_hash.encode())

    @staticmethod
    def hash_password(password: str) -> str:
        return bcrypt.hashpw(password.encode(), bcrypt.gensalt(12)).decode()

    def is_locked(self) -> bool:
        return bool(self.lock_until and self.lock_until > datetime.utcnow())

    def generate_email_verification_token(self) -> str:
        raw = secrets.token_hex(32)
        self.email_verification_token = hashlib.sha256(raw.encode()).hexdigest()
        self.email_verification_expires = datetime.utcnow() + timedelta(hours=24)
        return raw

    def generate_password_reset_token(self) -> str:
        raw = secrets.token_hex(32)
        self.password_reset_token = hashlib.sha256(raw.encode()).hexdigest()
        self.password_reset_expires = datetime.utcnow() + timedelta(hours=1)
        return raw

    def safe_dict(self) -> dict:
        """Return user data without sensitive fields."""
        return {
            "id": str(self.id),
            "name": self.name,
            "email": self.email,
            "profile": self.profile.model_dump(),
            "is_email_verified": self.is_email_verified,
            "is_active": self.is_active,
            "created_at": self.created_at.isoformat(),
        }


# ─── WorkoutSession Document ──────────────────────────────────────────────────

class ExercisePerformed(BaseModel):
    exercise_id: str
    exercise_name: str
    reps: int = 0
    sets: int = 1
    form_scores: List[float] = []
    avg_form_score: float = 0.0
    duration: int = 0  # seconds
    completed: bool = False
    notes: Optional[str] = None


class WorkoutSession(Document):
    user_id: str
    date: datetime = Field(default_factory=datetime.utcnow)
    duration: int  # total seconds
    exercises: List[ExercisePerformed] = []
    total_reps: int = 0
    avg_form_score: float = 0.0
    calories_burned: int = 0
    plan_id: Optional[str] = None
    notes: Optional[str] = None
    mood: Optional[Mood] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "workout_sessions"

    def compute_aggregates(self):
        if self.exercises:
            self.total_reps = sum(e.reps for e in self.exercises)
            valid = [e for e in self.exercises if e.avg_form_score > 0]
            self.avg_form_score = (
                round(sum(e.avg_form_score for e in valid) / len(valid))
                if valid else 0.0
            )
            self.calories_burned = round(5 * 70 * (self.duration / 3600) * 1.05)


# ─── ExercisePlan Document ────────────────────────────────────────────────────

class ExerciseEntry(BaseModel):
    exercise_id: str
    exercise_name: str
    target_sets: int = 3
    target_reps: int = 10
    rest_seconds: int = 60
    notes: Optional[str] = None
    progression_rule: Optional[str] = None


class DaySchedule(BaseModel):
    day: int  # 1=Monday, 7=Sunday
    day_name: str
    is_rest: bool = False
    exercises: List[ExerciseEntry] = []
    focus: Optional[str] = None


class WeekSchedule(BaseModel):
    week: int
    days: List[DaySchedule]


class ExercisePlan(Document):
    user_id: str
    name: str
    description: Optional[str] = None
    goal: str
    fitness_level: FitnessLevel
    duration_weeks: int = 4
    schedule: List[WeekSchedule] = []
    is_active: bool = False
    is_template: bool = False
    current_week: int = 1
    current_day: int = 1
    completion_percent: float = 0.0
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "exercise_plans"


# ─── Nutrition Documents ──────────────────────────────────────────────────────

class NutritionInfo(BaseModel):
    calories: float
    protein: float
    carbs: float
    fats: float
    fiber: float = 0.0
    sodium: Optional[float] = None
    sugar: Optional[float] = None


class FoodItem(Document):
    name: str
    name_hindi: Optional[str] = None
    category: str
    subcategory: Optional[str] = None
    nutrition: NutritionInfo  # per 100g
    serving_size: float = 100.0  # grams
    serving_unit: str = "g"
    indian_region: Optional[str] = None
    is_vegetarian: bool = True
    is_vegan: bool = False
    is_jain: bool = False
    is_gluten_free: bool = False
    barcode: Optional[str] = None
    image_url: Optional[str] = None
    tags: List[str] = []

    class Settings:
        name = "food_items"
        indexes = [
            IndexModel([("name", TEXT), ("name_hindi", TEXT), ("tags", TEXT)], name="food_text_search_idx")
        ]


class MealLog(Document):
    user_id: str
    date: datetime
    meal_type: MealType
    food_item_id: str
    food_name: str
    quantity: float
    calculated_nutrition: NutritionInfo
    image_url: Optional[str] = None
    notes: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "meal_logs"


# ─── Tracking Documents ───────────────────────────────────────────────────────

class WaterLog(Document):
    user_id: str
    date: datetime
    amount_ml: int
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "water_logs"


class WeightLog(Document):
    user_id: str
    date: datetime
    weight_kg: float
    body_fat_pct: Optional[float] = None
    notes: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Settings:
        name = "weight_logs"


# ─── Achievement Documents ────────────────────────────────────────────────────

class UserAchievement(Document):
    user_id: str
    achievement_id: str
    achievement_name: str
    unlocked_at: datetime = Field(default_factory=datetime.utcnow)
    progress: Optional[int] = None
    max_progress: Optional[int] = None

    class Settings:
        name = "user_achievements"


ACHIEVEMENT_DEFINITIONS = [
    {"id": "first_workout",   "name": "First Step",       "description": "Complete your first workout",            "icon": "🏃", "category": "workout",   "condition": {"type": "total_workouts",          "value": 1},     "xp_reward": 100,  "rarity": "common"},
    {"id": "reps_100",        "name": "Century",           "description": "Complete 100 total reps",                "icon": "💯", "category": "workout",   "condition": {"type": "total_reps",              "value": 100},   "xp_reward": 200,  "rarity": "common"},
    {"id": "reps_1000",       "name": "Rep Machine",       "description": "Complete 1,000 total reps",             "icon": "🔥", "category": "workout",   "condition": {"type": "total_reps",              "value": 1000},  "xp_reward": 500,  "rarity": "rare"},
    {"id": "reps_10000",      "name": "Iron Legend",       "description": "Complete 10,000 total reps",            "icon": "🏆", "category": "workout",   "condition": {"type": "total_reps",              "value": 10000}, "xp_reward": 2000, "rarity": "legendary"},
    {"id": "streak_7",        "name": "Week Warrior",      "description": "7-day workout streak",                  "icon": "📅", "category": "streak",    "condition": {"type": "streak_days",             "value": 7},     "xp_reward": 300,  "rarity": "common"},
    {"id": "streak_30",       "name": "Monthly Monster",   "description": "30-day workout streak",                 "icon": "🗓️","category": "streak",    "condition": {"type": "streak_days",             "value": 30},    "xp_reward": 1000, "rarity": "epic"},
    {"id": "perfect_form_10", "name": "Perfectionist",     "description": "Score 95%+ form in 10 sessions",        "icon": "✨", "category": "form",      "condition": {"type": "perfect_form_sessions",   "value": 10},    "xp_reward": 500,  "rarity": "rare"},
    {"id": "nutrition_7",     "name": "Nutrition Ninja",   "description": "Log meals for 7 consecutive days",      "icon": "🥗", "category": "nutrition", "condition": {"type": "nutrition_streak_days",   "value": 7},     "xp_reward": 300,  "rarity": "common"},
]
