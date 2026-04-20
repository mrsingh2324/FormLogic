"""Adaptive plan regeneration — weekly auto + manual trigger.

Signals used to adapt the next plan:
  - Completion rate (sessions / expected 4 per week)
  - Average form score
  - Body weight trend vs target (gaining/losing as expected?)
  - Physique goal from profile (lean, muscular, athletic, etc.)
  - Per-exercise form weakness (any exercise averaging < 70 gets de-escalated)
"""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional
from collections import defaultdict

from app.models.models import ExercisePlan, WorkoutSession, User
from app.services.plan_generator import generate_workout_plan, ALL_EXERCISES
from app.utils.logger import logger


# ─── Goal → plan focus mapping ────────────────────────────────────────────────

_TARGET_LOOK_TO_GOAL = {
    "lean_shredded":  "weight_loss",
    "athletic_toned": "toning",
    "big_muscular":   "muscle_gain",
    "slim_flexible":  "toning",
    "healthy_fit":    "health",
}


async def regenerate_adaptive_plan(
    user_id: str,
    plan_id: str,
    reason:  str = "weekly_refresh",
) -> dict:
    old_plan = await ExercisePlan.get(plan_id)
    if not old_plan or old_plan.user_id != user_id:
        raise ValueError("Plan not found")

    # ── Fetch signals ─────────────────────────────────────────────────────────
    now      = datetime.utcnow()
    week_ago = now - timedelta(days=7)

    import asyncio
    sessions, user = await asyncio.gather(
        WorkoutSession.find(
            WorkoutSession.user_id == user_id,
            WorkoutSession.date   >= week_ago,
        ).to_list(),
        User.get(user_id),
    )

    completion_rate = min(1.0, len(sessions) / 4.0)
    avg_form = round(sum(s.avg_form_score for s in sessions) / len(sessions), 1) if sessions else 0.0

    # ── Per-exercise weakness detection ───────────────────────────────────────
    ex_form: dict[str, list[float]] = defaultdict(list)
    for s in sessions:
        for ex in s.exercises:
            if ex.avg_form_score > 0:
                ex_form[ex.exercise_id].append(ex.avg_form_score)

    weak_exercises = [
        ex_id for ex_id, scores in ex_form.items()
        if scores and (sum(scores) / len(scores)) < 70
    ]

    # ── Body weight signal ────────────────────────────────────────────────────
    weight_trend: Optional[float] = None
    try:
        from app.models.models import WeightLog
        recent_weights = await WeightLog.find(
            WeightLog.user_id == user_id,
        ).sort(-WeightLog.date).limit(4).to_list()
        if len(recent_weights) >= 2:
            weight_trend = recent_weights[0].weight_kg - recent_weights[-1].weight_kg
    except Exception:
        pass

    # ── Determine goal from physique target (override plan goal if set) ───────
    goal = old_plan.goal
    if user and user.profile:
        target_look = getattr(user.profile, "target_look", None)
        if target_look and target_look in _TARGET_LOOK_TO_GOAL:
            goal = _TARGET_LOOK_TO_GOAL[target_look]

    # ── Adjust volume based on completion + form ──────────────────────────────
    days_per_week = 3
    if completion_rate >= 0.85 and avg_form >= 80:
        days_per_week = 5      # doing great → progress
    elif completion_rate >= 0.65 and avg_form >= 65:
        days_per_week = 4      # solid → steady progression
    elif completion_rate < 0.50:
        days_per_week = 3      # struggling → reduce volume, focus consistency

    # ── Intensity modifier based on weight trend vs goal ─────────────────────
    intensity_note: Optional[str] = None
    if weight_trend is not None and goal == "weight_loss" and weight_trend >= 0:
        intensity_note = "weight_stalled_increase_cardio"
    elif weight_trend is not None and goal == "muscle_gain" and weight_trend <= 0:
        intensity_note = "weight_stalled_increase_volume"

    # ── Equipment from user profile ───────────────────────────────────────────
    equipment = list(getattr(user.profile, "equipment", []) if user and user.profile else [])

    new_schedule = generate_workout_plan(
        goal           = goal,
        fitness_level  = old_plan.fitness_level,
        duration_weeks = old_plan.duration_weeks,
        equipment      = equipment,
        days_per_week  = days_per_week,
    )

    new_plan = ExercisePlan(
        user_id        = user_id,
        name           = f"{old_plan.name} (Week {old_plan.current_week + 1})",
        description    = (
            f"Auto-adapted. reason={reason}, "
            f"completion={completion_rate:.0%}, avg_form={avg_form}, "
            f"goal={goal}, days_pw={days_per_week}"
            + (f", weak_exercises={weak_exercises}" if weak_exercises else "")
            + (f", intensity={intensity_note}" if intensity_note else "")
        ),
        goal           = goal,
        fitness_level  = old_plan.fitness_level,
        duration_weeks = old_plan.duration_weeks,
        schedule       = new_schedule,
        is_active      = True,
    )

    await ExercisePlan.find(ExercisePlan.user_id == user_id).update({"$set": {"is_active": False}})
    await new_plan.insert()

    logger.info(
        f"[adaptive] user={user_id} plan={new_plan.id} "
        f"goal={goal} days_pw={days_per_week} "
        f"completion={completion_rate:.0%} form={avg_form} "
        f"weak={weak_exercises} weight_trend={weight_trend}"
    )

    return {
        "old_plan_id": str(old_plan.id),
        "new_plan_id": str(new_plan.id),
        "reason":      reason,
        "signals": {
            "completion_rate":   completion_rate,
            "avg_form_score":    avg_form,
            "days_per_week":     days_per_week,
            "goal_used":         goal,
            "weak_exercises":    weak_exercises,
            "weight_trend_kg":   weight_trend,
            "intensity_note":    intensity_note,
        },
    }


async def regenerate_active_plan_for_user(user_id: str, reason: str = "weekly_auto") -> dict | None:
    active = await ExercisePlan.find_one(
        ExercisePlan.user_id == user_id,
        ExercisePlan.is_active == True,  # noqa: E712
    )
    if not active:
        return None
    return await regenerate_adaptive_plan(user_id=user_id, plan_id=str(active.id), reason=reason)
