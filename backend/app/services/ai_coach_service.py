"""AI coach chat service with deep RAG over user data + curated knowledge.

RAG depth improvements over v1:
  - Per-exercise form history (last 30 sessions, per-exercise avg + trend)
  - Body composition self-assessment stored in UserProfile
  - Week-over-week trend analysis (reps, form score, completion rate)
  - Adaptive plan context: what changed week-to-week
"""
from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import List, Tuple, Optional
import json
import re
from collections import defaultdict

from app.models.models import WorkoutSession, ExercisePlan, MealLog, User, WeightLog
from app.utils.logger import logger


CURATED_KNOWLEDGE = [
    "Progressive overload should be gradual: increase reps, load, or density by ~5-10% each week.",
    "Protein target for muscle gain is typically 1.6–2.2 g per kg bodyweight spread evenly across the day.",
    "For fat loss, preserve muscle by keeping resistance training intensity high and protein adequate.",
    "Recovery indicators include sleep consistency, soreness trend, and declining form score under same volume.",
    "If adherence drops below 60% for a week, reduce complexity and focus on consistency before progression.",
    "Form score below 70 on compound lifts (squat, hinge, push, pull) signals a technique bottleneck — reduce load.",
    "Week-over-week improvement of 2–5% in form score on the same exercise indicates good neural adaptation.",
    "Body recomposition (simultaneous fat loss + muscle gain) is achievable for beginners with 0.5–1 % caloric surplus and high protein.",
    "Sleep deprivation (< 6 h) reduces strength output by ~8–10% and impairs form on technical exercises.",
    "Tracking consistency (logging > 80% of sessions) is the single best predictor of goal achievement.",
]


@dataclass
class RetrievalChunk:
    source: str
    content: str


def _safety_wrap(answer: str) -> str:
    disclaimer = (
        "\n\nSafety note: This is fitness coaching, not medical advice. "
        "If you have pain, dizziness, injury history, or a health condition, consult a clinician."
    )
    return answer.strip() + disclaimer


def _extract_json_object(raw: str) -> Optional[dict]:
    text = (raw or "").strip()
    if not text:
        return None
    fenced = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, flags=re.S)
    if fenced:
        text = fenced.group(1)
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    try:
        return json.loads(text[start : end + 1])
    except Exception:
        return None


def _validate_structured(payload: dict) -> dict:
    return {
        "diagnosis": str(payload.get("diagnosis", "")).strip(),
        "weekly_actions": [str(x).strip() for x in payload.get("weekly_actions", []) if str(x).strip()],
        "nutrition_corrections": [str(x).strip() for x in payload.get("nutrition_corrections", []) if str(x).strip()],
        "recovery_corrections": [str(x).strip() for x in payload.get("recovery_corrections", []) if str(x).strip()],
        "plan_updates": [str(x).strip() for x in payload.get("plan_updates", []) if str(x).strip()],
        "progress_updates": [str(x).strip() for x in payload.get("progress_updates", []) if str(x).strip()],
        "nutrition_updates": [str(x).strip() for x in payload.get("nutrition_updates", []) if str(x).strip()],
        "proposed_updates": {
            "target_workouts_per_week": payload.get("proposed_updates", {}).get("target_workouts_per_week"),
            "target_protein_g": payload.get("proposed_updates", {}).get("target_protein_g"),
            "next_plan_focus": payload.get("proposed_updates", {}).get("next_plan_focus"),
        },
    }


def _fallback_structured() -> dict:
    return {
        "diagnosis": "Consistency is low and logged training/nutrition data is limited this week.",
        "weekly_actions": [
            "Complete at least 3 workouts this week.",
            "Log every workout session immediately after training.",
            "Keep session intensity moderate and prioritize form quality.",
        ],
        "nutrition_corrections": ["Track at least one protein-focused meal daily."],
        "recovery_corrections": ["Keep a fixed sleep schedule for 7 days."],
        "plan_updates": ["Reduce plan complexity until adherence improves."],
        "progress_updates": ["Focus on weekly adherence and average form score."],
        "nutrition_updates": ["Increase daily protein consistency."],
        "proposed_updates": {
            "target_workouts_per_week": 3,
            "target_protein_g": 110,
            "next_plan_focus": "foundation_technique",
        },
    }


# ─── Deep RAG retrieval ───────────────────────────────────────────────────────

async def _retrieve_user_context(user_id: str) -> List[RetrievalChunk]:
    now = datetime.utcnow()
    week_ago  = now - timedelta(days=7)
    two_weeks = now - timedelta(days=14)
    month_ago = now - timedelta(days=30)

    # Fetch all data concurrently via async gather pattern
    import asyncio
    sessions_week, sessions_prev_week, sessions_month, meals, active_plan, user, weight_logs = await asyncio.gather(
        WorkoutSession.find(
            WorkoutSession.user_id == user_id,
            WorkoutSession.date >= week_ago,
        ).sort(-WorkoutSession.date).limit(20).to_list(),
        WorkoutSession.find(
            WorkoutSession.user_id == user_id,
            WorkoutSession.date >= two_weeks,
            WorkoutSession.date < week_ago,
        ).sort(-WorkoutSession.date).limit(20).to_list(),
        WorkoutSession.find(
            WorkoutSession.user_id == user_id,
            WorkoutSession.date >= month_ago,
        ).sort(-WorkoutSession.date).limit(60).to_list(),
        MealLog.find(
            MealLog.user_id == user_id,
            MealLog.date >= week_ago,
        ).sort(-MealLog.date).limit(40).to_list(),
        ExercisePlan.find_one(
            ExercisePlan.user_id == user_id,
            ExercisePlan.is_active == True,  # noqa: E712
        ),
        User.get(user_id),
        WeightLog.find(
            WeightLog.user_id == user_id,
        ).sort(-WeightLog.date).limit(12).to_list(),
    )

    chunks: List[RetrievalChunk] = []

    # ── 1. User profile + body composition ───────────────────────────────────
    if user:
        p = user.profile
        body_desc = getattr(p, "current_physique_description", "") or ""
        target_desc = getattr(p, "target_physique_description", "") or ""
        bmi = None
        if p.weight and p.height:
            bmi = round(p.weight / ((p.height / 100) ** 2), 1)
        chunks.append(RetrievalChunk(
            source="profile",
            content=(
                f"goals={p.goals}, fitness_level={p.fitness_level}, equipment={p.equipment}, "
                f"dietary_restrictions={p.dietary_restrictions}, "
                f"weight={p.weight}kg, height={p.height}cm, age={p.age}, gender={p.gender}, "
                f"bmi={bmi}, "
                f"current_physique='{body_desc}', target_physique='{target_desc}'"
            ),
        ))

    # ── 2. Active plan context ────────────────────────────────────────────────
    if active_plan:
        chunks.append(RetrievalChunk(
            source="active_plan",
            content=(
                f"name={active_plan.name}, goal={active_plan.goal}, "
                f"fitness_level={active_plan.fitness_level}, "
                f"duration_weeks={active_plan.duration_weeks}, "
                f"current_week={active_plan.current_week}, "
                f"completion_percent={active_plan.completion_percent:.1f}%"
            ),
        ))

    # ── 3. This-week summary ──────────────────────────────────────────────────
    tw_reps = sum(s.total_reps for s in sessions_week)
    tw_form = round(sum(s.avg_form_score for s in sessions_week) / len(sessions_week), 1) if sessions_week else 0.0
    chunks.append(RetrievalChunk(
        source="weekly_workout_summary",
        content=(
            f"This week: sessions={len(sessions_week)}, total_reps={tw_reps}, "
            f"avg_form_score={tw_form}. "
            f"Most recent session reps={sessions_week[0].total_reps if sessions_week else 0}, "
            f"duration={sessions_week[0].duration if sessions_week else 0}s."
        ),
    ))

    # ── 4. Week-over-week trend ───────────────────────────────────────────────
    pw_reps = sum(s.total_reps for s in sessions_prev_week)
    pw_form = round(sum(s.avg_form_score for s in sessions_prev_week) / len(sessions_prev_week), 1) if sessions_prev_week else 0.0
    rep_delta  = tw_reps - pw_reps
    form_delta = round(tw_form - pw_form, 1)
    session_delta = len(sessions_week) - len(sessions_prev_week)
    chunks.append(RetrievalChunk(
        source="week_over_week_trend",
        content=(
            f"WoW change: sessions={session_delta:+d}, reps={rep_delta:+d}, "
            f"avg_form={form_delta:+.1f}. "
            f"Prev week had {len(sessions_prev_week)} sessions, {pw_reps} reps, form={pw_form}."
        ),
    ))

    # ── 5. Per-exercise form history (last 30 days) ───────────────────────────
    ex_form: dict[str, list[float]] = defaultdict(list)
    ex_reps: dict[str, list[int]]   = defaultdict(list)
    for s in sessions_month:
        for ex in s.exercises:
            if ex.avg_form_score > 0:
                ex_form[ex.exercise_name].append(ex.avg_form_score)
            if ex.reps > 0:
                ex_reps[ex.exercise_name].append(ex.reps)

    form_summaries = []
    for ex_name, scores in ex_form.items():
        if len(scores) >= 2:
            trend = round(scores[-1] - scores[0], 1)
            avg   = round(sum(scores) / len(scores), 1)
            form_summaries.append(
                f"{ex_name}: avg_form={avg}, trend={trend:+.1f} over {len(scores)} sessions"
            )
        elif len(scores) == 1:
            form_summaries.append(f"{ex_name}: form={scores[0]} (1 session)")

    if form_summaries:
        chunks.append(RetrievalChunk(
            source="per_exercise_form_history",
            content="Per-exercise form (last 30 days): " + "; ".join(form_summaries[:8]),
        ))

    # ── 6. Exercise volume trend ──────────────────────────────────────────────
    volume_summaries = []
    for ex_name, reps_list in ex_reps.items():
        if len(reps_list) >= 2:
            delta = reps_list[-1] - reps_list[0]
            volume_summaries.append(f"{ex_name}: {reps_list[0]}→{reps_list[-1]} reps ({delta:+d})")

    if volume_summaries:
        chunks.append(RetrievalChunk(
            source="exercise_volume_trend",
            content="Volume progression (first→last session in 30 days): " + "; ".join(volume_summaries[:6]),
        ))

    # ── 7. Nutrition summary ──────────────────────────────────────────────────
    total_calories = sum(m.calculated_nutrition.calories for m in meals)
    total_protein  = round(sum(m.calculated_nutrition.protein for m in meals), 1)
    avg_protein_day = round(total_protein / 7, 1)
    chunks.append(RetrievalChunk(
        source="weekly_nutrition_summary",
        content=(
            f"Last 7 days: meal_logs={len(meals)}, total_calories={total_calories}, "
            f"total_protein_g={total_protein}, avg_protein_per_day={avg_protein_day}g."
        ),
    ))

    # ── 8. Body weight trend ─────────────────────────────────────────────────
    if weight_logs:
        weights = [w.weight_kg for w in weight_logs if w.weight_kg]
        if len(weights) >= 2:
            delta = round(weights[0] - weights[-1], 1)   # most recent minus oldest
            trend_str = f"{delta:+.1f} kg" if delta != 0 else "stable"
            chunks.append(RetrievalChunk(
                source="body_weight_trend",
                content=(
                    f"Body weight: current={weights[0]}kg, "
                    f"{len(weights)} logs over last {len(weight_logs)} entries, "
                    f"trend={trend_str}. "
                    f"Target from profile: {getattr(user.profile if user else None, 'target_physique_description', '') or 'not set'}."
                ),
            ))

    # ── 9. Curated coach knowledge ────────────────────────────────────────────
    for fact in CURATED_KNOWLEDGE:
        chunks.append(RetrievalChunk(source="coach_knowledge", content=fact))

    return chunks


def _rank_chunks(query: str, chunks: List[RetrievalChunk], k: int = 10) -> List[RetrievalChunk]:
    """BM25-lite: term frequency overlap with source-type boosting."""
    terms = [t.lower() for t in query.split() if len(t) > 2]
    HIGH_PRIORITY = {"weekly_workout_summary", "week_over_week_trend", "active_plan", "per_exercise_form_history", "body_weight_trend"}
    MEDIUM_PRIORITY = {"weekly_nutrition_summary", "exercise_volume_trend", "profile"}

    scored: List[Tuple[int, RetrievalChunk]] = []
    for ch in chunks:
        text  = ch.content.lower()
        score = sum(1 for t in terms if t in text)
        if ch.source in HIGH_PRIORITY:
            score += 3
        elif ch.source in MEDIUM_PRIORITY:
            score += 1
        scored.append((score, ch))
    scored.sort(key=lambda x: x[0], reverse=True)
    top = [c for _, c in scored[:k]]
    return top or chunks[:k]


# ─── Public API ───────────────────────────────────────────────────────────────

async def coach_chat(user_id: str, user_prompt: str, history: List[dict] | None = None) -> dict:
    chunks   = await _retrieve_user_context(user_id)
    selected = _rank_chunks(user_prompt, chunks, k=10)
    citations = [{"source": c.source, "snippet": c.content[:200]} for c in selected]

    context_block = "\n".join([f"[{c.source}] {c.content}" for c in selected])
    convo = history or []
    convo_text = "\n".join([f"{m.get('role','user')}: {m.get('content','')}" for m in convo[-8:]])

    system_prompt = (
        "You are FormLogic Elite Coach — a data-driven, performance-focused AI personal trainer. "
        "Use the retrieved context (training history, form data, nutrition, trend signals) "
        "to give specific, actionable coaching. "
        "Tone: direct, concise, warm. Never diagnose or treat medical conditions."
    )

    final_prompt = (
        f"{system_prompt}\n\nRetrieved context:\n{context_block}\n\n"
        f"Conversation history:\n{convo_text}\n\nUser: {user_prompt}\n\n"
        "Respond ONLY as strict JSON with this schema:\n"
        "{\n"
        "  \"diagnosis\": \"string\",\n"
        "  \"weekly_actions\": [\"string\"],\n"
        "  \"nutrition_corrections\": [\"string\"],\n"
        "  \"recovery_corrections\": [\"string\"],\n"
        "  \"plan_updates\": [\"string\"],\n"
        "  \"progress_updates\": [\"string\"],\n"
        "  \"nutrition_updates\": [\"string\"],\n"
        "  \"proposed_updates\": {\n"
        "    \"target_workouts_per_week\": 0,\n"
        "    \"target_protein_g\": 0,\n"
        "    \"next_plan_focus\": \"string\"\n"
        "  }\n"
        "}\n"
        "No markdown. No prose outside JSON."
    )

    api_key = os.getenv("GEMINI_API_KEY", "")
    if not api_key:
        structured = _fallback_structured()
        answer = _format_answer(structured)
        return {"answer": _safety_wrap(answer), "structured": structured, "citations": citations, "model": "fallback"}

    try:
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-2.5-flash")
        resp  = model.generate_content(final_prompt)
        text  = (resp.text or "").strip()
        if not text:
            raise RuntimeError("Empty AI response")
        parsed     = _extract_json_object(text)
        structured = _validate_structured(parsed) if parsed else _fallback_structured()
        answer     = _format_answer(structured)
        return {"answer": _safety_wrap(answer), "structured": structured, "citations": citations, "model": "gemini-2.5-flash"}
    except Exception as exc:
        logger.error(f"coach_chat generation failed: {exc}")
        structured = _fallback_structured()
        answer     = _format_answer(structured)
        return {"answer": _safety_wrap(answer), "structured": structured, "citations": citations, "model": "fallback-error"}


def _format_answer(s: dict) -> str:
    parts = [f"Assessment: {s['diagnosis']}"]
    if s["weekly_actions"]:
        parts.append("This week: " + "; ".join(s["weekly_actions"]))
    if s["nutrition_corrections"]:
        parts.append("Nutrition: " + "; ".join(s["nutrition_corrections"]))
    if s["recovery_corrections"]:
        parts.append("Recovery: " + "; ".join(s["recovery_corrections"]))
    return "\n".join(parts)
