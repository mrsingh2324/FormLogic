"""Plan generator — replaces services/planGeneratorService.ts"""
from __future__ import annotations
from typing import List, Dict, Optional
from app.models.models import WeekSchedule, DaySchedule, ExerciseEntry, FitnessLevel

DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

ALL_EXERCISES: Dict[str, List[dict]] = {
    "lower": [
        {"exercise_id": "squat",        "exercise_name": "Bodyweight Squat", "target_sets": 3, "target_reps": 15, "rest_seconds": 60, "equipment": ["bodyweight"]},
        {"exercise_id": "lunge",        "exercise_name": "Forward Lunge",    "target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["bodyweight"]},
        {"exercise_id": "glute_bridge", "exercise_name": "Glute Bridge",     "target_sets": 3, "target_reps": 15, "rest_seconds": 45, "equipment": ["bodyweight"]},
        {"exercise_id": "goblet_squat", "exercise_name": "Goblet Squat",     "target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["dumbbells"]},
        {"exercise_id": "rdl",          "exercise_name": "Romanian Deadlift","target_sets": 3, "target_reps": 12, "rest_seconds": 75, "equipment": ["dumbbells"]},
    ],
    "upper": [
        {"exercise_id": "pushup",     "exercise_name": "Push-Up",        "target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["bodyweight"]},
        {"exercise_id": "tricep_dip", "exercise_name": "Chair Tricep Dip","target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["bodyweight"]},
        {"exercise_id": "pullup",     "exercise_name": "Pull-Up",        "target_sets": 3, "target_reps":  6, "rest_seconds": 90, "equipment": ["pullup_bar"]},
        {"exercise_id": "db_press",   "exercise_name": "Dumbbell Press", "target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["dumbbells"]},
        {"exercise_id": "db_row",     "exercise_name": "Dumbbell Row",   "target_sets": 3, "target_reps": 12, "rest_seconds": 60, "equipment": ["dumbbells"]},
    ],
    "core": [
        {"exercise_id": "plank",          "exercise_name": "Plank (30s)",      "target_sets": 3, "target_reps":  1, "rest_seconds": 45, "equipment": ["bodyweight"], "notes": "Hold 30s"},
        {"exercise_id": "crunch",         "exercise_name": "Crunch",           "target_sets": 3, "target_reps": 20, "rest_seconds": 30, "equipment": ["bodyweight"]},
        {"exercise_id": "mountain_climber","exercise_name": "Mountain Climber","target_sets": 3, "target_reps": 20, "rest_seconds": 45, "equipment": ["bodyweight"]},
    ],
    "cardio": [
        {"exercise_id": "jumping_jack", "exercise_name": "Jumping Jacks", "target_sets": 3, "target_reps": 30, "rest_seconds": 30, "equipment": ["bodyweight"]},
        {"exercise_id": "high_knees",   "exercise_name": "High Knees",    "target_sets": 3, "target_reps": 30, "rest_seconds": 30, "equipment": ["bodyweight"]},
        {"exercise_id": "burpee",       "exercise_name": "Burpee",        "target_sets": 3, "target_reps": 10, "rest_seconds": 60, "equipment": ["bodyweight"]},
    ],
}

FOCUS_MAPS = {
    "weight_loss": [["cardio","rest","cardio","rest","cardio","rest","rest"],["cardio","full_body","rest","cardio","full_body","rest","cardio"],["cardio","lower","upper","rest","cardio","full_body","rest"]],
    "muscle_gain": [["push","rest","pull","rest","lower","rest","rest"],["push","pull","rest","lower","push","rest","rest"],["push","pull","lower","rest","upper","lower","rest"]],
    "endurance":   [["cardio","rest","cardio","rest","cardio","rest","rest"],["cardio","full_body","cardio","rest","cardio","core","rest"],["cardio","lower","cardio","upper","cardio","core","rest"]],
    "toning":      [["full_body","rest","full_body","rest","full_body","rest","rest"],["upper","lower","rest","upper","lower","rest","rest"],["upper","lower","core","upper","lower","cardio","rest"]],
}


def _filter_equipment(exercises: List[dict], user_equipment: List[str]) -> List[dict]:
    equip = set(user_equipment) if user_equipment else set()
    equip.add("bodyweight")
    return [e for e in exercises if any(eq in equip for eq in e["equipment"])]


def _adapt_reps(ex: dict, week: int, level: str) -> dict:
    mult = {"beginner": 0.8, "intermediate": 1.0, "advanced": 1.25}.get(level, 1.0)
    week_mult = 1 + (week - 1) * 0.05
    return {
        **ex,
        "target_sets": min(round(ex["target_sets"] * mult), 5),
        "target_reps": min(max(round(ex["target_reps"] * mult * week_mult), 5), 40),
    }


def _pick_exercises(focus: str, equipment: List[str], level: str, week: int) -> List[dict]:
    n = {"advanced": 5, "intermediate": 4}.get(level, 3)
    lower = _filter_equipment(ALL_EXERCISES["lower"], equipment)
    upper = _filter_equipment(ALL_EXERCISES["upper"], equipment)
    core = _filter_equipment(ALL_EXERCISES["core"], equipment)
    cardio = _filter_equipment(ALL_EXERCISES["cardio"], equipment)

    if focus == "full_body":
        pool = lower[:2] + upper[:2] + core[:1]
    elif focus == "lower":
        pool = lower[:n]
    elif focus in ("upper", "push"):
        pool = [e for e in upper if "push" in e["exercise_id"] or "press" in e["exercise_id"] or "dip" in e["exercise_id"]][:3] + core[:1]
    elif focus == "pull":
        pool = [e for e in upper if "pull" in e["exercise_id"] or "row" in e["exercise_id"] or "curl" in e["exercise_id"]][:3] + core[:1]
    elif focus == "core":
        pool = core[:n]
    elif focus == "cardio":
        pool = cardio[:3] + lower[:1]
    else:
        pool = []

    return [_adapt_reps(e, week, level) for e in pool]


def generate_workout_plan(goal: str, fitness_level, duration_weeks: int, equipment: List[str], days_per_week: int) -> List[WeekSchedule]:
    level = fitness_level.value if hasattr(fitness_level, "value") else str(fitness_level)
    goal_key = goal.lower().replace(" ", "_")
    focus_maps = FOCUS_MAPS.get(goal_key, FOCUS_MAPS["toning"])
    focus_by_day = focus_maps[min(max(days_per_week - 3, 0), len(focus_maps) - 1)]

    weeks = []
    for wi in range(duration_weeks):
        days = []
        for di, focus in enumerate(focus_by_day):
            exercises = []
            if focus != "rest":
                raw = _pick_exercises(focus, equipment, level, wi + 1)
                exercises = [ExerciseEntry(exercise_id=e["exercise_id"], exercise_name=e["exercise_name"], target_sets=e["target_sets"], target_reps=e["target_reps"], rest_seconds=e.get("rest_seconds", 60), notes=e.get("notes")) for e in raw]
            days.append(DaySchedule(day=di + 1, day_name=DAY_NAMES[di], is_rest=(focus == "rest"), focus="Rest & Recovery" if focus == "rest" else focus.replace("_", " ").title(), exercises=exercises))
        weeks.append(WeekSchedule(week=wi + 1, days=days))
    return weeks
