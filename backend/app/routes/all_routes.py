"""
All remaining routes — workout, user, nutrition, plan, achievement,
tracking, ai, privacy, social auth, notifications, upload, webhook
Converted from TypeScript controllers + routes
"""
from __future__ import annotations
import hashlib
import hmac
import os
from datetime import datetime, timedelta
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Request, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from beanie import PydanticObjectId

from app.middleware.auth_middleware import get_current_user, get_current_user_id
from app.models.models import (
    User, WorkoutSession, ExercisePerformed, ExercisePlan, FoodItem, MealLog,
    WaterLog, WeightLog, UserAchievement, ACHIEVEMENT_DEFINITIONS,
    FitnessLevel, MealType, Mood, NutritionInfo, WeekSchedule, DaySchedule, ExerciseEntry,
)
from app.utils.jwt_utils import generate_access_token, generate_refresh_token
from app.utils.logger import logger
from app.services.plan_generator import generate_workout_plan
from app.services.ai_coach_service import coach_chat
from app.services.plan_adaptive_service import regenerate_adaptive_plan


# ═══════════════════════════════════════════════════════════════════════════════
# WORKOUT ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
workout_router = APIRouter()


class ExerciseInput(BaseModel):
    exercise_id: str
    exercise_name: str
    reps: int = Field(ge=0)
    sets: int = Field(default=1, ge=1)
    form_scores: List[float] = []
    avg_form_score: float = 0.0
    duration: int = 0
    completed: bool = True
    notes: Optional[str] = None


class SaveSessionRequest(BaseModel):
    date: datetime = Field(default_factory=datetime.utcnow)
    duration: int = Field(ge=0)
    exercises: List[ExerciseInput]
    plan_id: Optional[str] = None
    notes: Optional[str] = None
    mood: Optional[Mood] = None


@workout_router.post("/sessions", status_code=201)
async def save_session(body: SaveSessionRequest, user_id: str = Depends(get_current_user_id)):
    exercises = [ExercisePerformed(**e.model_dump()) for e in body.exercises]
    session = WorkoutSession(
        user_id=user_id,
        date=body.date,
        duration=body.duration,
        exercises=exercises,
        plan_id=body.plan_id,
        notes=body.notes,
        mood=body.mood,
    )
    session.compute_aggregates()
    await session.insert()
    return {"success": True, "data": session.model_dump()}


@workout_router.get("/sessions")
async def get_history(page: int = 1, limit: int = 20, user_id: str = Depends(get_current_user_id)):
    skip = (page - 1) * limit
    sessions = await WorkoutSession.find(WorkoutSession.user_id == user_id).sort(-WorkoutSession.date).skip(skip).limit(limit).to_list()
    total = await WorkoutSession.find(WorkoutSession.user_id == user_id).count()
    return {"success": True, "data": [s.model_dump() for s in sessions], "pagination": {"page": page, "limit": limit, "total": total, "pages": -(-total // limit)}}


@workout_router.get("/sessions/{session_id}")
async def get_session(session_id: str, user_id: str = Depends(get_current_user_id)):
    session = await WorkoutSession.get(session_id)
    if not session or session.user_id != user_id:
        raise HTTPException(404, "Session not found")
    return {"success": True, "data": session.model_dump()}


@workout_router.delete("/sessions/{session_id}")
async def delete_session(session_id: str, user_id: str = Depends(get_current_user_id)):
    session = await WorkoutSession.get(session_id)
    if not session or session.user_id != user_id:
        raise HTTPException(404, "Session not found")
    await session.delete()
    return {"success": True, "message": "Session deleted"}


@workout_router.get("/progress/weekly")
async def weekly_progress(weeks: int = 8, user_id: str = Depends(get_current_user_id)):
    start = datetime.utcnow() - timedelta(weeks=weeks)
    sessions = await WorkoutSession.find(WorkoutSession.user_id == user_id, WorkoutSession.date >= start).sort(WorkoutSession.date).to_list()
    weekly: dict = {}
    for s in sessions:
        week_start = s.date - timedelta(days=s.date.weekday())
        week_start = week_start.replace(hour=0, minute=0, second=0, microsecond=0)
        key = week_start.date().isoformat()
        if key not in weekly:
            weekly[key] = {"workouts": 0, "total_reps": 0, "avg_form_score": 0.0}
        weekly[key]["workouts"] += 1
        weekly[key]["total_reps"] += s.total_reps
        n = weekly[key]["workouts"]
        weekly[key]["avg_form_score"] = (weekly[key]["avg_form_score"] * (n - 1) + s.avg_form_score) / n
    return {"success": True, "data": weekly}


# ═══════════════════════════════════════════════════════════════════════════════
# USER ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
user_router = APIRouter()


class UpdateProfileRequest(BaseModel):
    name: Optional[str] = None
    profile: Optional[dict] = None


@user_router.get("/me")
async def get_profile(user: User = Depends(get_current_user)):
    return {"success": True, "data": user.safe_dict()}


@user_router.put("/me")
async def update_profile(body: UpdateProfileRequest, user: User = Depends(get_current_user)):
    if body.name:
        user.name = body.name
    if body.profile:
        for k, v in body.profile.items():
            setattr(user.profile, k, v)
    user.updated_at = datetime.utcnow()
    await user.save()
    return {"success": True, "data": user.safe_dict()}


@user_router.get("/me/stats")
async def get_stats(user: User = Depends(get_current_user)):
    sessions = await WorkoutSession.find(WorkoutSession.user_id == str(user.id)).sort(-WorkoutSession.date).to_list()
    total = len(sessions)
    total_reps = sum(s.total_reps for s in sessions)
    total_duration = sum(s.duration for s in sessions)
    total_calories = sum(s.calories_burned for s in sessions)
    avg_form = round(sum(s.avg_form_score for s in sessions) / total) if total else 0

    today = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    workout_dates = {s.date.replace(hour=0, minute=0, second=0, microsecond=0) for s in sessions}
    streak = 0
    for i in range(365):
        d = today - timedelta(days=i)
        if d in workout_dates:
            streak += 1
        elif i > 0:
            break

    return {"success": True, "data": {"total_workouts": total, "total_reps": total_reps, "total_duration": total_duration, "total_calories": total_calories, "avg_form_score": avg_form, "current_streak": streak}}


@user_router.delete("/me")
async def delete_account(user: User = Depends(get_current_user)):
    user.is_active = False
    await user.save()
    return {"success": True, "message": "Account deactivated successfully"}


# ═══════════════════════════════════════════════════════════════════════════════
# NUTRITION ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
nutrition_router = APIRouter()


@nutrition_router.get("/food/search")
async def search_food(q: str = ""):
    if len(q) < 2:
        return {"success": True, "data": []}
    foods = await FoodItem.find({"$text": {"$search": q}}).limit(20).to_list()
    if not foods:
        import re
        foods = await FoodItem.find({"name": {"$regex": q, "$options": "i"}}).limit(20).to_list()
    return {"success": True, "data": [f.model_dump() for f in foods]}


@nutrition_router.get("/food/barcode/{barcode}")
async def get_food_by_barcode(barcode: str):
    food = await FoodItem.find_one(FoodItem.barcode == barcode)
    if not food:
        raise HTTPException(404, "Food item not found")
    return {"success": True, "data": food.model_dump()}


@nutrition_router.get("/food/{food_id}")
async def get_food_by_id(food_id: str):
    food = await FoodItem.get(food_id)
    if not food:
        raise HTTPException(404, "Food item not found")
    return {"success": True, "data": food.model_dump()}


class LogMealRequest(BaseModel):
    date: datetime = Field(default_factory=datetime.utcnow)
    meal_type: MealType
    food_item_id: str
    quantity: float = Field(ge=0.1)
    notes: Optional[str] = None


@nutrition_router.post("/meals", status_code=201)
async def log_meal(body: LogMealRequest, user_id: str = Depends(get_current_user_id)):
    food = await FoodItem.get(body.food_item_id)
    if not food:
        raise HTTPException(404, "Food item not found")

    m = (body.quantity * food.serving_size) / 100
    calc = NutritionInfo(
        calories=round(food.nutrition.calories * m),
        protein=round(food.nutrition.protein * m, 1),
        carbs=round(food.nutrition.carbs * m, 1),
        fats=round(food.nutrition.fats * m, 1),
        fiber=round(food.nutrition.fiber * m, 1),
    )
    log = MealLog(user_id=user_id, date=body.date, meal_type=body.meal_type, food_item_id=body.food_item_id, food_name=food.name, quantity=body.quantity, calculated_nutrition=calc, notes=body.notes)
    await log.insert()
    return {"success": True, "data": log.model_dump()}


@nutrition_router.get("/meals/daily/{date}")
async def daily_summary(date: str, user_id: str = Depends(get_current_user_id)):
    start = datetime.fromisoformat(date).replace(hour=0, minute=0, second=0, microsecond=0)
    end = start.replace(hour=23, minute=59, second=59, microsecond=999999)
    logs = await MealLog.find(MealLog.user_id == user_id, MealLog.date >= start, MealLog.date <= end).sort(MealLog.date).to_list()
    summary = {"calories": 0, "protein": 0.0, "carbs": 0.0, "fats": 0.0, "fiber": 0.0}
    meals_by_type: dict = {}
    for log in logs:
        n = log.calculated_nutrition
        summary["calories"] += n.calories
        summary["protein"] += n.protein
        summary["carbs"] += n.carbs
        summary["fats"] += n.fats
        summary["fiber"] += n.fiber
        meals_by_type.setdefault(log.meal_type, []).append(log.model_dump())
    return {"success": True, "data": {"date": date, "summary": summary, "meals": meals_by_type, "total_entries": len(logs)}}


@nutrition_router.get("/meals/weekly")
async def weekly_nutrition(user_id: str = Depends(get_current_user_id)):
    start = datetime.utcnow() - timedelta(days=7)
    logs = await MealLog.find(MealLog.user_id == user_id, MealLog.date >= start).to_list()
    daily: dict = {}
    for log in logs:
        key = log.date.date().isoformat()
        if key not in daily:
            daily[key] = {"calories": 0, "protein": 0.0, "carbs": 0.0, "fats": 0.0}
        daily[key]["calories"] += log.calculated_nutrition.calories
        daily[key]["protein"] += log.calculated_nutrition.protein
        daily[key]["carbs"] += log.calculated_nutrition.carbs
        daily[key]["fats"] += log.calculated_nutrition.fats
    return {"success": True, "data": daily}


@nutrition_router.delete("/meals/{log_id}")
async def delete_meal_log(log_id: str, user_id: str = Depends(get_current_user_id)):
    log = await MealLog.get(log_id)
    if not log or log.user_id != user_id:
        raise HTTPException(404, "Meal log not found")
    await log.delete()
    return {"success": True, "message": "Meal log deleted"}


# ═══════════════════════════════════════════════════════════════════════════════
# PLAN ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
plan_router = APIRouter()


class GeneratePlanRequest(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    description: Optional[str] = None
    goal: str
    fitness_level: FitnessLevel
    duration_weeks: int = Field(default=4, ge=1, le=52)
    equipment: List[str] = []
    days_per_week: int = Field(default=3, ge=1, le=7)


@plan_router.post("/generate", status_code=201)
async def generate_plan(body: GeneratePlanRequest, user_id: str = Depends(get_current_user_id)):
    schedule = generate_workout_plan(
        goal=body.goal,
        fitness_level=body.fitness_level,
        duration_weeks=body.duration_weeks,
        equipment=body.equipment,
        days_per_week=body.days_per_week,
    )
    plan = ExercisePlan(user_id=user_id, name=body.name, description=body.description, goal=body.goal, fitness_level=body.fitness_level, duration_weeks=body.duration_weeks, schedule=schedule)
    await plan.insert()
    return {"success": True, "data": plan.model_dump()}


@plan_router.get("/")
async def get_plans(user_id: str = Depends(get_current_user_id)):
    plans = await ExercisePlan.find(ExercisePlan.user_id == user_id).sort(-ExercisePlan.created_at).to_list()
    return {"success": True, "data": [p.model_dump() for p in plans]}


@plan_router.get("/active")
async def get_active_plan(user_id: str = Depends(get_current_user_id)):
    plan = await ExercisePlan.find_one(ExercisePlan.user_id == user_id, ExercisePlan.is_active == True)
    return {"success": True, "data": plan.model_dump() if plan else None}


@plan_router.get("/{plan_id}")
async def get_plan(plan_id: str, user_id: str = Depends(get_current_user_id)):
    plan = await ExercisePlan.get(plan_id)
    if not plan or plan.user_id != user_id:
        raise HTTPException(404, "Plan not found")
    return {"success": True, "data": plan.model_dump()}


@plan_router.put("/{plan_id}/activate")
async def activate_plan(plan_id: str, user_id: str = Depends(get_current_user_id)):
    await ExercisePlan.find(ExercisePlan.user_id == user_id).update({"$set": {"is_active": False}})
    plan = await ExercisePlan.get(plan_id)
    if not plan or plan.user_id != user_id:
        raise HTTPException(404, "Plan not found")
    plan.is_active = True
    await plan.save()
    return {"success": True, "data": plan.model_dump()}


@plan_router.put("/{plan_id}/progress")
async def update_progress(plan_id: str, current_week: int, current_day: int, completion_percent: float, user_id: str = Depends(get_current_user_id)):
    plan = await ExercisePlan.get(plan_id)
    if not plan or plan.user_id != user_id:
        raise HTTPException(404, "Plan not found")
    plan.current_week = current_week
    plan.current_day = current_day
    plan.completion_percent = completion_percent
    await plan.save()
    return {"success": True, "data": plan.model_dump()}


@plan_router.delete("/{plan_id}")
async def delete_plan(plan_id: str, user_id: str = Depends(get_current_user_id)):
    plan = await ExercisePlan.get(plan_id)
    if not plan or plan.user_id != user_id:
        raise HTTPException(404, "Plan not found")
    await plan.delete()
    return {"success": True, "message": "Plan deleted"}


class RegeneratePlanRequest(BaseModel):
    plan_id: str
    reason: Optional[str] = "weekly_refresh"


@plan_router.post("/regenerate")
async def regenerate_plan(body: RegeneratePlanRequest, user_id: str = Depends(get_current_user_id)):
    """
    Immediate synchronous regeneration (existing behaviour).
    For large fleets, prefer /regenerate/async which enqueues a Celery task.
    """
    try:
        data = await regenerate_adaptive_plan(user_id=user_id, plan_id=body.plan_id, reason=body.reason or "weekly_refresh")
        return {"success": True, "data": data}
    except ValueError:
        raise HTTPException(404, "Plan not found")


@plan_router.post("/regenerate/async")
async def regenerate_plan_async(user_id: str = Depends(get_current_user_id)):
    """
    Enqueue a Celery task to regenerate the user's active plan in the background.
    Returns immediately with a task_id — poll /tasks/{task_id} for status.
    """
    try:
        from app.tasks import regen_plan_for_user
        task = regen_plan_for_user.delay(user_id=user_id, reason="api_trigger")
        return {"success": True, "task_id": task.id, "message": "Plan regeneration queued"}
    except Exception as exc:
        raise HTTPException(500, f"Could not enqueue task: {exc}")


@plan_router.get("/{plan_id}/diff")
async def plan_diff(plan_id: str, baseline_plan_id: Optional[str] = None, user_id: str = Depends(get_current_user_id)):
    current = await ExercisePlan.get(plan_id)
    if not current or current.user_id != user_id:
        raise HTTPException(404, "Plan not found")

    baseline = None
    if baseline_plan_id:
        baseline = await ExercisePlan.get(baseline_plan_id)
    else:
        prev = await ExercisePlan.find(ExercisePlan.user_id == user_id, ExercisePlan.id != current.id).sort(-ExercisePlan.created_at).limit(1).to_list()
        baseline = prev[0] if prev else None

    if not baseline:
        return {"success": True, "data": {"baseline_plan_id": None, "current_plan_id": str(current.id), "changes": ["No baseline plan found"]}}

    cur_days = sum(1 for w in current.schedule for d in w.days if not d.is_rest)
    base_days = sum(1 for w in baseline.schedule for d in w.days if not d.is_rest)
    cur_ex = sum(len(d.exercises) for w in current.schedule for d in w.days)
    base_ex = sum(len(d.exercises) for w in baseline.schedule for d in w.days)

    changes = []
    if cur_days != base_days:
        changes.append(f"Training days/week changed from {base_days} to {cur_days}")
    if cur_ex != base_ex:
        changes.append(f"Total exercise slots changed from {base_ex} to {cur_ex}")
    if not changes:
        changes.append("Plan structure is similar; progression likely changed within exercises/rest.")

    return {"success": True, "data": {"baseline_plan_id": str(baseline.id), "current_plan_id": str(current.id), "changes": changes}}


# ═══════════════════════════════════════════════════════════════════════════════
# ACHIEVEMENT ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
achievement_router = APIRouter()


@achievement_router.get("/")
async def get_achievements(user_id: str = Depends(get_current_user_id)):
    unlocked = await UserAchievement.find(UserAchievement.user_id == user_id).sort(-UserAchievement.unlocked_at).to_list()
    unlocked_ids = {a.achievement_id for a in unlocked}
    all_a = [{**d, "unlocked": d["id"] in unlocked_ids, "unlocked_at": next((u.unlocked_at.isoformat() for u in unlocked if u.achievement_id == d["id"]), None)} for d in ACHIEVEMENT_DEFINITIONS]
    return {"success": True, "data": all_a}


@achievement_router.post("/check")
async def check_and_unlock(user_id: str = Depends(get_current_user_id)):
    sessions = await WorkoutSession.find(WorkoutSession.user_id == user_id).to_list()
    total_reps = sum(s.total_reps for s in sessions)
    total_workouts = len(sessions)
    perfect_sessions = sum(1 for s in sessions if s.avg_form_score >= 95)

    today = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    dates = {s.date.replace(hour=0, minute=0, second=0, microsecond=0) for s in sessions}
    streak = 0
    for i in range(365):
        d = today - timedelta(days=i)
        if d in dates:
            streak += 1
        elif i > 0:
            break

    existing = await UserAchievement.find(UserAchievement.user_id == user_id).to_list()
    existing_ids = {a.achievement_id for a in existing}
    newly_unlocked = []

    for d in ACHIEVEMENT_DEFINITIONS:
        if d["id"] in existing_ids:
            continue
        ct = d["condition"]["type"]
        cv = d["condition"]["value"]
        should = (
            (ct == "total_workouts" and total_workouts >= cv) or
            (ct == "total_reps" and total_reps >= cv) or
            (ct == "streak_days" and streak >= cv) or
            (ct == "perfect_form_sessions" and perfect_sessions >= cv)
        )
        if should:
            await UserAchievement(user_id=user_id, achievement_id=d["id"], achievement_name=d["name"]).insert()
            newly_unlocked.append(d["id"])

    return {"success": True, "data": {"newly_unlocked": newly_unlocked}}


# ═══════════════════════════════════════════════════════════════════════════════
# TRACKING ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
tracking_router = APIRouter()


class WaterLogRequest(BaseModel):
    amount_ml: int = Field(ge=50, le=2000)
    date: datetime = Field(default_factory=datetime.utcnow)


@tracking_router.post("/water", status_code=201)
async def log_water(body: WaterLogRequest, user_id: str = Depends(get_current_user_id)):
    log = await WaterLog(user_id=user_id, **body.model_dump()).insert()
    return {"success": True, "data": log.model_dump()}


@tracking_router.get("/water/{date}")
async def get_water(date: str, user_id: str = Depends(get_current_user_id)):
    start = datetime.fromisoformat(date).replace(hour=0, minute=0, second=0)
    end = start.replace(hour=23, minute=59, second=59)
    logs = await WaterLog.find(WaterLog.user_id == user_id, WaterLog.date >= start, WaterLog.date <= end).to_list()
    total = sum(l.amount_ml for l in logs)
    return {"success": True, "data": {"logs": [l.model_dump() for l in logs], "total_ml": total}}


@tracking_router.delete("/water/{log_id}")
async def delete_water(log_id: str, user_id: str = Depends(get_current_user_id)):
    log = await WaterLog.get(log_id)
    if not log or log.user_id != user_id:
        raise HTTPException(404, "Log not found")
    await log.delete()
    return {"success": True}


class WeightLogRequest(BaseModel):
    weight_kg: float = Field(ge=20, le=300)
    body_fat_pct: Optional[float] = None
    notes: Optional[str] = None
    date: datetime = Field(default_factory=datetime.utcnow)


@tracking_router.post("/weight", status_code=201)
async def log_weight(body: WeightLogRequest, user_id: str = Depends(get_current_user_id)):
    log = await WeightLog(user_id=user_id, **body.model_dump()).insert()
    user = await User.get(user_id)
    if user:
        user.profile.weight = body.weight_kg
        await user.save()
    return {"success": True, "data": log.model_dump()}


@tracking_router.get("/weight")
async def get_weight(limit: int = 90, user_id: str = Depends(get_current_user_id)):
    logs = await WeightLog.find(WeightLog.user_id == user_id).sort(-WeightLog.date).limit(limit).to_list()
    return {"success": True, "data": [l.model_dump() for l in logs]}


@tracking_router.delete("/weight/{log_id}")
async def delete_weight(log_id: str, user_id: str = Depends(get_current_user_id)):
    log = await WeightLog.get(log_id)
    if not log or log.user_id != user_id:
        raise HTTPException(404, "Log not found")
    await log.delete()
    return {"success": True}


# ═══════════════════════════════════════════════════════════════════════════════
# AI ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
ai_router = APIRouter()
report_router = APIRouter()


class VerifyTaskRequest(BaseModel):
    prompt: str
    context: Optional[dict] = None


@ai_router.post("/verify")
async def verify_task(body: VerifyTaskRequest, user_id: str = Depends(get_current_user_id)):
    from app.services.ai_verification_service import verify_action
    result = await verify_action(body.prompt, body.context)
    return {"success": True, "data": {"verification": result}}


class ChatMessageIn(BaseModel):
    content: str = Field(min_length=1, max_length=2000)
    thread_id: Optional[str] = None
    context_tags: List[str] = []


class CreateThreadRequest(BaseModel):
    title: str = Field(min_length=1, max_length=120)


@ai_router.post("/chat/threads")
async def create_thread(body: CreateThreadRequest, user_id: str = Depends(get_current_user_id)):
    user = await User.get(user_id)
    if not user:
        raise HTTPException(404, "User not found")
    threads = list(getattr(user, "coach_threads", []) or [])
    thread = {
        "id": str(PydanticObjectId()),
        "title": body.title,
        "created_at": datetime.utcnow().isoformat(),
        "updated_at": datetime.utcnow().isoformat(),
        "messages": [],
    }
    user.coach_threads = [thread] + threads[:24]
    await user.save()
    return {"success": True, "data": thread}


@ai_router.get("/chat/threads")
async def get_threads(user_id: str = Depends(get_current_user_id)):
    user = await User.get(user_id)
    if not user:
        raise HTTPException(404, "User not found")
    threads = getattr(user, "coach_threads", []) or []
    slim = [
        {
            "id": t.get("id"),
            "title": t.get("title"),
            "updated_at": t.get("updated_at"),
            "created_at": t.get("created_at"),
        }
        for t in threads
    ]
    return {"success": True, "data": slim}


@ai_router.post("/chat")
async def chat(body: ChatMessageIn, user_id: str = Depends(get_current_user_id)):
    user = await User.get(user_id)
    if not user:
        raise HTTPException(404, "User not found")

    threads = list(getattr(user, "coach_threads", []) or [])
    thread = next((t for t in threads if t.get("id") == body.thread_id), None) if body.thread_id else None
    if thread is None:
        thread = {
            "id": str(PydanticObjectId()),
            "title": "Coach Session",
            "created_at": datetime.utcnow().isoformat(),
            "updated_at": datetime.utcnow().isoformat(),
            "messages": [],
        }
        threads = [thread] + threads

    history = thread.get("messages", [])[-10:]
    ai_out = await coach_chat(user_id=user_id, user_prompt=body.content, history=history)
    structured = ai_out.get("structured", {}) or {}

    # Apply AI-proposed updates to user/profile/plan so other app sections reflect them.
    proposed = structured.get("proposed_updates", {}) if isinstance(structured, dict) else {}
    active_plan = await ExercisePlan.find_one(ExercisePlan.user_id == user_id, ExercisePlan.is_active == True)
    target_days = proposed.get("target_workouts_per_week")
    target_protein = proposed.get("target_protein_g")
    next_focus = proposed.get("next_plan_focus")
    if isinstance(target_days, int) and 1 <= target_days <= 7:
        user.profile.preferred_workout_days = list(range(1, target_days + 1))
    if isinstance(target_protein, int) and target_protein > 0:
        user.profile.goals = sorted(set((user.profile.goals or []) + [f"protein_target_{target_protein}g"]))
    if active_plan and isinstance(next_focus, str) and next_focus.strip():
        active_plan.description = f"{(active_plan.description or '').strip()} | AI Focus: {next_focus.strip()}".strip(" |")
        active_plan.updated_at = datetime.utcnow()
        await active_plan.save()

    user_msg = {"role": "user", "content": body.content, "ts": datetime.utcnow().isoformat(), "context_tags": body.context_tags}
    asst_msg = {
        "role": "assistant",
        "content": ai_out["answer"],
        "ts": datetime.utcnow().isoformat(),
        "citations": ai_out.get("citations", []),
        "model": ai_out.get("model", ""),
        "structured": structured,
    }
    thread["messages"] = (thread.get("messages", []) + [user_msg, asst_msg])[-60:]
    thread["updated_at"] = datetime.utcnow().isoformat()
    user.coach_threads = threads[:25]
    user.updated_at = datetime.utcnow()
    await user.save()

    return {"success": True, "data": {"thread_id": thread["id"], "message": asst_msg, "streaming": False}}


@ai_router.post("/chat/stream")
async def chat_stream(body: ChatMessageIn, user_id: str = Depends(get_current_user_id)):
    """
    True streaming response using Gemini stream=True.
    Falls back to word-chunked simulation if Gemini is unavailable.
    Structured JSON is emitted as a final SSE event after the prose stream.
    """
    user = await User.get(user_id)
    if not user:
        raise HTTPException(404, "User not found")

    import os
    from app.services.ai_coach_service import _retrieve_user_context, _rank_chunks
    import json as _json

    chunks  = await _retrieve_user_context(user_id)
    selected = _rank_chunks(body.content, chunks, k=10)
    context_block = "\n".join([f"[{c.source}] {c.content}" for c in selected])
    history = []
    # Pull thread history if thread_id provided
    if body.thread_id:
        thread = next((t for t in user.coach_threads if str(t.get("id", "")) == body.thread_id), None)
        if thread:
            history = thread.get("messages", [])[-8:]
    convo_text = "\n".join([f"{m.get('role','user')}: {m.get('content','')}" for m in history])

    system_prompt = (
        "You are FormLogic Elite Coach. Give direct, warm, performance-focused coaching. "
        "Use retrieved context for specific advice. Never provide medical diagnoses."
    )
    final_prompt = (
        f"{system_prompt}\n\nContext:\n{context_block}\n\n"
        f"Conversation:\n{convo_text}\n\nUser: {body.content}\n\n"
        "Respond conversationally and helpfully. Be specific and actionable."
    )

    api_key = os.getenv("GEMINI_API_KEY", "")

    async def event_gen():
        if api_key:
            try:
                import google.generativeai as genai
                genai.configure(api_key=api_key)
                model = genai.GenerativeModel("gemini-2.5-flash")
                response = model.generate_content(final_prompt, stream=True)
                for chunk in response:
                    token = getattr(chunk, "text", "") or ""
                    if token:
                        yield f"data: {_json.dumps(token)}\n\n"
                yield "event: done\ndata: [DONE]\n\n"
                return
            except Exception:
                pass
        # Fallback: non-streaming
        from app.services.ai_coach_service import coach_chat, _safety_wrap, _fallback_structured, _format_answer
        ai_out = await coach_chat(user_id=user_id, user_prompt=body.content, history=history)
        text = ai_out["answer"]
        for word in text.split(" "):
            yield f"data: {_json.dumps(word + ' ')}\n\n"
        yield "event: done\ndata: [DONE]\n\n"

    return StreamingResponse(event_gen(), media_type="text/event-stream", headers={
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
    })


@report_router.get("/weekly")
async def weekly_report(user: User = Depends(get_current_user)):
    uid = str(user.id)
    now = datetime.utcnow()
    start = now - timedelta(days=7)
    sessions = await WorkoutSession.find(WorkoutSession.user_id == uid, WorkoutSession.date >= start).to_list()
    meals = await MealLog.find(MealLog.user_id == uid, MealLog.date >= start).to_list()
    weights = await WeightLog.find(WeightLog.user_id == uid, WeightLog.date >= start).to_list()

    workouts = len(sessions)
    reps = sum(s.total_reps for s in sessions)
    avg_form = round(sum(s.avg_form_score for s in sessions) / workouts, 1) if workouts else 0.0
    calories = sum(m.calculated_nutrition.calories for m in meals)
    protein = round(sum(m.calculated_nutrition.protein for m in meals), 1)
    adherence = min(100, int((workouts / 4.0) * 100))
    nutrition_score = min(100, int((protein / 700.0) * 100))

    coach_summary = await coach_chat(
        user_id=uid,
        user_prompt="Summarize my last week and provide elite trainer next actions.",
        history=[],
    )
    return {
        "success": True,
        "data": {
            "window": {"from": start.date().isoformat(), "to": now.date().isoformat()},
            "kpis": {
                "workouts": workouts,
                "total_reps": reps,
                "avg_form_score": avg_form,
                "calories_logged": calories,
                "protein_g": protein,
                "adherence_pct": adherence,
                "nutrition_score": nutrition_score,
                "weight_entries": len(weights),
            },
            "coach_summary": coach_summary["answer"],
            "coach_citations": coach_summary.get("citations", []),
            "coach_structured": coach_summary.get("structured", {}),
            "next_actions": [
                "Complete at least 4 sessions this week.",
                "Hit protein target daily and log post-workout nutrition.",
                "Regenerate plan if form drops for 2 sessions in a row.",
            ],
        },
    }


@report_router.get("/monthly")
async def monthly_report(user: User = Depends(get_current_user)):
    uid = str(user.id)
    now = datetime.utcnow()
    start = now - timedelta(days=30)
    sessions = await WorkoutSession.find(WorkoutSession.user_id == uid, WorkoutSession.date >= start).sort(WorkoutSession.date).to_list()
    meals = await MealLog.find(MealLog.user_id == uid, MealLog.date >= start).sort(MealLog.date).to_list()

    weekly_buckets = {}
    for s in sessions:
        wk = (s.date - timedelta(days=s.date.weekday())).date().isoformat()
        weekly_buckets.setdefault(wk, {"workouts": 0, "reps": 0, "form_sum": 0.0})
        weekly_buckets[wk]["workouts"] += 1
        weekly_buckets[wk]["reps"] += s.total_reps
        weekly_buckets[wk]["form_sum"] += s.avg_form_score

    trends = []
    for wk, row in sorted(weekly_buckets.items()):
        w = row["workouts"]
        trends.append({
            "week": wk,
            "workouts": w,
            "reps": row["reps"],
            "avg_form_score": round(row["form_sum"] / w, 1) if w else 0.0,
        })

    total_calories = sum(m.calculated_nutrition.calories for m in meals)
    total_protein = round(sum(m.calculated_nutrition.protein for m in meals), 1)

    return {
        "success": True,
        "data": {
            "window": {"from": start.date().isoformat(), "to": now.date().isoformat()},
            "summary": {
                "total_workouts": len(sessions),
                "total_reps": sum(s.total_reps for s in sessions),
                "avg_form_score": round(sum(s.avg_form_score for s in sessions) / len(sessions), 1) if sessions else 0.0,
                "calories_logged": total_calories,
                "protein_g": total_protein,
            },
            "weekly_trends": trends,
        },
    }


# ═══════════════════════════════════════════════════════════════════════════════
# PRIVACY ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
privacy_router = APIRouter()

CURRENT_PRIVACY_VERSION = "1.0.0"


class ConsentRequest(BaseModel):
    privacy_policy_version: str
    marketing_opt_in: bool = False
    analytics_opt_in: bool = False


@privacy_router.get("/info")
async def privacy_info():
    return {"success": True, "data": {"current_policy_version": CURRENT_PRIVACY_VERSION, "privacy_policy_url": "https://formlogic.ai/privacy", "terms_url": "https://formlogic.ai/terms", "data_controller": "FormLogic AI Private Limited", "grievance_officer": "privacy@formlogic.ai"}}


@privacy_router.post("/consent")
async def record_consent(body: ConsentRequest, user: User = Depends(get_current_user)):
    if body.privacy_policy_version != CURRENT_PRIVACY_VERSION:
        raise HTTPException(400, f"Please accept the current privacy policy (v{CURRENT_PRIVACY_VERSION})")
    user.consent.privacy_policy_version = body.privacy_policy_version
    user.consent.privacy_policy_accepted_at = datetime.utcnow()
    user.consent.marketing_opt_in = body.marketing_opt_in
    user.consent.analytics_opt_in = body.analytics_opt_in
    await user.save()
    return {"success": True, "message": "Consent recorded."}


@privacy_router.get("/export")
async def export_data(user: User = Depends(get_current_user)):
    uid = str(user.id)
    workouts = await WorkoutSession.find(WorkoutSession.user_id == uid).to_list()
    plans = await ExercisePlan.find(ExercisePlan.user_id == uid).to_list()
    meals = await MealLog.find(MealLog.user_id == uid).to_list()
    achievements = await UserAchievement.find(UserAchievement.user_id == uid).to_list()
    weights = await WeightLog.find(WeightLog.user_id == uid).to_list()
    water = await WaterLog.find(WaterLog.user_id == uid).to_list()
    from fastapi.responses import JSONResponse
    return JSONResponse({"exported_at": datetime.utcnow().isoformat(), "profile": user.safe_dict(), "workout_sessions": [w.model_dump() for w in workouts], "exercise_plans": [p.model_dump() for p in plans], "meal_logs": [m.model_dump() for m in meals], "weight_logs": [w.model_dump() for w in weights], "water_logs": [w.model_dump() for w in water], "achievements": [a.model_dump() for a in achievements]}, headers={"Content-Disposition": f"attachment; filename=formlogic-data-{uid}.json"})


class DeleteAccountRequest(BaseModel):
    confirm_phrase: str


@privacy_router.delete("/account")
@privacy_router.post("/account-delete")
async def delete_account_cascade(body: DeleteAccountRequest, user: User = Depends(get_current_user)):
    if body.confirm_phrase != "DELETE MY ACCOUNT":
        raise HTTPException(400, 'Please type "DELETE MY ACCOUNT" to confirm.')
    uid = str(user.id)
    await WorkoutSession.find(WorkoutSession.user_id == uid).delete()
    await ExercisePlan.find(ExercisePlan.user_id == uid).delete()
    await MealLog.find(MealLog.user_id == uid).delete()
    await WaterLog.find(WaterLog.user_id == uid).delete()
    await WeightLog.find(WeightLog.user_id == uid).delete()
    await UserAchievement.find(UserAchievement.user_id == uid).delete()
    await user.delete()
    logger.info(f"Account permanently deleted: {uid}")
    return {"success": True, "message": "Your account and all associated data have been permanently deleted. Goodbye."}


# ═══════════════════════════════════════════════════════════════════════════════
# SOCIAL AUTH ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
social_auth_router = APIRouter()

import secrets as _secrets
import hashlib as _hl


def _hash_token_local(t: str) -> str:
    return _hl.sha256(t.encode()).hexdigest()


async def _issue_tokens(user_id: str, email: str):
    access = generate_access_token(user_id, email)
    refresh = generate_refresh_token(user_id, email)
    user = await User.get(user_id)
    if user:
        user.refresh_tokens = user.refresh_tokens[-4:] + [_hash_token_local(refresh)]
        user.last_active_at = datetime.utcnow()
        await user.save()
    return access, refresh


class GoogleSignInRequest(BaseModel):
    id_token: str


@social_auth_router.post("/google")
async def google_sign_in(body: GoogleSignInRequest):
    from google.oauth2 import id_token as google_id_token
    from google.auth.transport import requests as google_requests
    try:
        audience = [os.getenv("GOOGLE_CLIENT_ID"), os.getenv("GOOGLE_CLIENT_ID_IOS"), os.getenv("GOOGLE_CLIENT_ID_ANDROID")]
        audience = [a for a in audience if a]
        idinfo = google_id_token.verify_firebase_token(body.id_token, google_requests.Request(), audience=audience)
    except Exception as e:
        raise HTTPException(400, f"Google token verification failed: {e}")

    email = idinfo.get("email")
    name = idinfo.get("name") or email.split("@")[0]
    picture = idinfo.get("picture")

    user = await User.find_one(User.email == email)
    if not user:
        user = User(name=name, email=email, password_hash=User.hash_password(_secrets.token_hex(32)), is_email_verified=True)
        if picture:
            user.profile.profile_picture_url = picture
        await user.insert()
    else:
        user.is_email_verified = True
        await user.save()

    access, refresh = await _issue_tokens(str(user.id), user.email)
    return {"success": True, "data": {"user": user.safe_dict(), "access_token": access, "refresh_token": refresh}}


class AppleSignInRequest(BaseModel):
    identity_token: str
    full_name: Optional[dict] = None
    email: Optional[str] = None


@social_auth_router.post("/apple")
async def apple_sign_in(body: AppleSignInRequest):
    import jose.jwt as _jwt
    decoded = _jwt.get_unverified_claims(body.identity_token)
    apple_id = decoded.get("sub")
    email = body.email or decoded.get("email")
    if not apple_id or not email:
        raise HTTPException(400, "Could not retrieve email from Apple token")

    display_name = email.split("@")[0]
    if body.full_name:
        given = body.full_name.get("givenName", "")
        family = body.full_name.get("familyName", "")
        if given:
            display_name = f"{given} {family}".strip()

    user = await User.find_one(User.email == email)
    if not user:
        user = User(name=display_name, email=email, password_hash=User.hash_password(_secrets.token_hex(32)), is_email_verified=True)
        await user.insert()
    else:
        user.is_email_verified = True
        await user.save()

    access, refresh = await _issue_tokens(str(user.id), user.email)
    return {"success": True, "data": {"user": user.safe_dict(), "access_token": access, "refresh_token": refresh}}


# ═══════════════════════════════════════════════════════════════════════════════
# NOTIFICATION ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
notification_router = APIRouter()


class TokenRequest(BaseModel):
    token: str


@notification_router.post("/token")
async def register_push_token(body: TokenRequest, user: User = Depends(get_current_user)):
    """Register or update device Expo push token."""
    if not body.token.startswith("ExponentPushToken[") and not body.token.startswith("ExpoPushToken["):
        raise HTTPException(400, "Invalid Expo push token format")
    user.expo_push_token = body.token
    await user.save()
    return {"success": True, "message": "Push token registered."}


@notification_router.delete("/token")
async def unregister_push_token(user: User = Depends(get_current_user)):
    """Unregister push notifications (user opts out or logs out)."""
    user.expo_push_token = None
    await user.save()
    return {"success": True}


class ReminderRequest(BaseModel):
    enabled: bool
    hour: Optional[int] = None
    minute: Optional[int] = None
    workout_name: Optional[str] = None


@notification_router.post("/reminder")
async def set_reminder(body: ReminderRequest, user_id: str = Depends(get_current_user_id)):
    """Schedule or cancel a daily workout reminder."""
    from app.services.notification_service import schedule_workout_reminder, cancel_workout_reminder
    if body.enabled:
        if body.hour is None or body.minute is None:
            raise HTTPException(400, "hour and minute required")
        await schedule_workout_reminder(user_id, body.workout_name or "your workout", body.hour, body.minute)
        return {"success": True, "message": f"Daily reminder set for {body.hour:02d}:{str(body.minute).zfill(2)}"}
    else:
        await cancel_workout_reminder(user_id)
        return {"success": True, "message": "Reminder cancelled."}


@notification_router.post("/test")
async def test_notification(user_id: str = Depends(get_current_user_id)):
    """Send a test push notification (dev/debug only)."""
    from app.services.notification_service import send_push_notification
    await send_push_notification(user_id, "🏋️ FormLogic Test", "Push notifications are working!")
    return {"success": True}


# ═══════════════════════════════════════════════════════════════════════════════
# UPLOAD ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
upload_router = APIRouter()

ALLOWED_MIME = {"image/jpeg", "image/jpg", "image/png", "image/webp"}


@upload_router.post("/profile-picture")
async def upload_profile_picture(image: UploadFile = File(...), user: User = Depends(get_current_user)):
    if image.content_type not in ALLOWED_MIME:
        raise HTTPException(400, "Only JPEG, PNG, and WebP images are allowed")
    from app.services.storage_service import upload_image
    data = await image.read()
    result = await upload_image(data, image.content_type, "profile")
    user.profile.profile_picture_url = result["public_url"]
    await user.save()
    return {"success": True, "data": {"image_url": result["public_url"]}}


@upload_router.post("/meal-photo")
async def upload_meal_photo(image: UploadFile = File(...), user_id: str = Depends(get_current_user_id)):
    if image.content_type not in ALLOWED_MIME:
        raise HTTPException(400, "Only JPEG, PNG, and WebP images are allowed")
    from app.services.storage_service import upload_image
    data = await image.read()
    result = await upload_image(data, image.content_type, "meals")
    return {"success": True, "data": {"image_url": result["public_url"]}}


# ═══════════════════════════════════════════════════════════════════════════════
# WEBHOOK ROUTER
# ═══════════════════════════════════════════════════════════════════════════════
webhook_router = APIRouter()


@webhook_router.post("/revenuecat")
async def revenuecat_webhook(request: Request):
    body = await request.body()
    sig = request.headers.get("x-revenuecat-signature", "")
    secret = os.getenv("REVENUECAT_WEBHOOK_SECRET", "")

    if os.getenv("NODE_ENV") == "production" and secret:
        expected = hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expected):
            raise HTTPException(401, "Invalid signature")

    data = await request.json()
    event = data.get("event", {})
    event_type = event.get("type")
    app_user_id = event.get("app_user_id")
    product_id = event.get("product_id")

    user = await User.get(app_user_id) if app_user_id else None
    if not user:
        return {"received": True}

    if event_type in ("INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE"):
        user.subscription.status = "active"
        user.subscription.product_id = product_id
        user.subscription.updated_at = datetime.utcnow()
    elif event_type in ("CANCELLATION", "EXPIRATION"):
        user.subscription.status = "cancelled"
        user.subscription.updated_at = datetime.utcnow()
    elif event_type == "BILLING_ISSUE":
        user.subscription.status = "billing_issue"
        user.subscription.updated_at = datetime.utcnow()

    await user.save()
    return {"received": True}
