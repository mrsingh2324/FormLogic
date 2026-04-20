"""
FormLogic Celery tasks.

All async Beanie/MongoDB calls are run via asyncio.run() since Celery
workers are synchronous by default. Each task spins up its own event loop,
connects to the DB, does its work, and disconnects cleanly.
"""
from __future__ import annotations

import asyncio
import os
from datetime import datetime
from typing import Optional

from app.worker import celery_app
from app.utils.logger import logger


def _run(coro):
    """Execute an async coroutine from a sync Celery task."""
    return asyncio.run(coro)


# ─── Weekly adaptive plan regeneration ───────────────────────────────────────

@celery_app.task(
    name="app.tasks.regen_all_active_plans",
    bind=True,
    max_retries=2,
    default_retry_delay=300,
)
def regen_all_active_plans(self):
    """
    Fired every Monday 02:00 UTC by Celery beat.
    Finds every user with an active plan and regenerates it based on
    the past week's form scores and completion rate.
    """
    try:
        _run(_async_regen_all_plans())
    except Exception as exc:
        logger.error(f"regen_all_active_plans failed: {exc}")
        raise self.retry(exc=exc)


async def _async_regen_all_plans():
    from app.utils.database import connect_db, disconnect_db
    from app.models.models import ExercisePlan
    from app.services.plan_adaptive_service import regenerate_adaptive_plan

    await connect_db()
    try:
        active_plans = await ExercisePlan.find(
            ExercisePlan.is_active == True  # noqa: E712
        ).to_list()

        success = 0
        failed  = 0
        for plan in active_plans:
            try:
                result = await regenerate_adaptive_plan(
                    user_id=plan.user_id,
                    plan_id=str(plan.id),
                    reason="weekly_auto",
                )
                logger.info(
                    f"[beat] Plan regenerated for user={plan.user_id} "
                    f"old={result['old_plan_id']} new={result['new_plan_id']} "
                    f"days_pw={result['signals']['days_per_week']}"
                )
                success += 1
            except Exception as exc:
                logger.warning(f"[beat] Failed to regen plan {plan.id}: {exc}")
                failed += 1

        logger.info(f"[beat] weekly regen complete — success={success}, failed={failed}")
    finally:
        await disconnect_db()


# ─── Daily reminder notifications ────────────────────────────────────────────

@celery_app.task(
    name="app.tasks.send_daily_reminders",
    bind=True,
    max_retries=1,
    default_retry_delay=120,
)
def send_daily_reminders(self):
    """
    Fired every day at 06:00 UTC.
    Sends push notifications to users who have reminders enabled.
    """
    try:
        _run(_async_send_reminders())
    except Exception as exc:
        logger.error(f"send_daily_reminders failed: {exc}")
        raise self.retry(exc=exc)


async def _async_send_reminders():
    from app.utils.database import connect_db, disconnect_db
    from app.models.models import User
    from app.services.notification_service import send_push_notification

    await connect_db()
    try:
        users = await User.find(
            User.is_active == True,  # noqa: E712
            User.expo_push_token != None,  # noqa: E711
        ).to_list()

        now_hour = datetime.utcnow().hour
        sent = 0
        for user in users:
            if not user.reminder.enabled:
                continue
            if user.reminder.hour != now_hour:
                continue
            try:
                await send_push_notification(
                    token=user.expo_push_token,
                    title="FormLogic — Time to train 💪",
                    body=f"Your {user.reminder.workout_name} session is scheduled. Let's go!",
                    data={"type": "workout_reminder"},
                )
                sent += 1
            except Exception as exc:
                logger.warning(f"[beat] Push failed for user={user.id}: {exc}")

        logger.info(f"[beat] daily reminders sent={sent}")
    finally:
        await disconnect_db()


# ─── On-demand single-user plan regen (called from API route) ─────────────────

@celery_app.task(
    name="app.tasks.regen_plan_for_user",
    bind=True,
    max_retries=2,
    default_retry_delay=60,
)
def regen_plan_for_user(self, user_id: str, reason: str = "manual"):
    """
    Enqueue via:  regen_plan_for_user.delay(user_id="...", reason="api_trigger")
    Used by the /api/v1/plans/regenerate endpoint so the HTTP response
    returns immediately while the heavy work happens in the background.
    """
    try:
        result = _run(_async_regen_one(user_id, reason))
        return result
    except Exception as exc:
        logger.error(f"regen_plan_for_user failed user={user_id}: {exc}")
        raise self.retry(exc=exc)


async def _async_regen_one(user_id: str, reason: str) -> Optional[dict]:
    from app.utils.database import connect_db, disconnect_db
    from app.services.plan_adaptive_service import regenerate_active_plan_for_user

    await connect_db()
    try:
        return await regenerate_active_plan_for_user(user_id=user_id, reason=reason)
    finally:
        await disconnect_db()
