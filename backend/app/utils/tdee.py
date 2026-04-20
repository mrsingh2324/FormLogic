"""TDEE utils — Python port of utils/tdee.ts (also used directly by tests)"""
from dataclasses import dataclass, field
from typing import List
import math


@dataclass
class UserMetrics:
    weight_kg: float = 70.0
    height_cm: float = 165.0
    age_years: int = 30
    gender: str = "other"
    fitness_level: str = "beginner"
    goals: List[str] = field(default_factory=list)
    days_per_week: int = 3


@dataclass
class NutritionTargets:
    calories: int
    protein: int
    carbs: int
    fats: int
    fiber: int
    water_ml: int


@dataclass
class BmiResult:
    bmi: float
    category: str
    color_hex: str


def _activity_multiplier(level: str, days: int) -> float:
    if level == "advanced" or days >= 5: return 1.725
    if level == "intermediate" or days >= 3: return 1.55
    return 1.375


def _bmr(m: UserMetrics) -> float:
    male   = 10 * m.weight_kg + 6.25 * m.height_cm - 5 * m.age_years + 5
    female = 10 * m.weight_kg + 6.25 * m.height_cm - 5 * m.age_years - 161
    if m.gender == "male": return male
    if m.gender == "female": return female
    return (male + female) / 2


def calculate_nutrition_targets(m: UserMetrics) -> NutritionTargets:
    tdee = _bmr(m) * _activity_multiplier(m.fitness_level, m.days_per_week)
    goal = (m.goals[0].lower() if m.goals else "")
    if "weight_loss" in goal or "loss" in goal: raw = tdee - 400
    elif "muscle_gain" in goal or "gain" in goal: raw = tdee + 250
    else: raw = tdee
    cal = round(max(1200, min(raw, 4000)) / 50) * 50

    if "muscle_gain" in goal:   p, c, f = 0.30, 0.45, 0.25
    elif "weight_loss" in goal: p, c, f = 0.35, 0.35, 0.30
    elif "endurance" in goal:   p, c, f = 0.20, 0.55, 0.25
    else:                        p, c, f = 0.25, 0.50, 0.25

    return NutritionTargets(
        calories = cal,
        protein  = round(cal * p / 4),
        carbs    = round(cal * c / 4),
        fats     = round(cal * f / 9),
        fiber    = min(38, round(cal / 1000 * 14)),
        water_ml = round(m.weight_kg * 35),
    )


def calculate_bmi(weight_kg: float, height_cm: float) -> BmiResult:
    h = height_cm / 100
    bmi = round(weight_kg / (h * h), 1)
    if bmi < 18.5: return BmiResult(bmi, "Underweight", "#4FC3F7")
    if bmi < 25.0: return BmiResult(bmi, "Healthy",     "#43D9AD")
    if bmi < 30.0: return BmiResult(bmi, "Overweight",  "#FFB547")
    return BmiResult(bmi, "Obese", "#FF4757")
