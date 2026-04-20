"""
notification_service.py
Full Python port of services/notificationService.ts
Uses Celery + Redis for background job queue (BullMQ equivalent).
Push notifications sent via Expo Push API (same as original).
"""
from __future__ import annotations

import json
import logging
import os
from datetime import datetime
from typing import Optional

import httpx
from celery import Celery
from celery.schedules import crontab

from app.utils.logger import logger

# ─── Celery app (BullMQ equivalent) ──────────────────────────────────────────

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")

celery_app = Celery(
    "formlogic",
    broker=REDIS_URL,
    backend=REDIS_URL,
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Kolkata",
    enable_utc=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    task_max_retries=3,
    task_default_retry_delay=5,
)

# ─── Expo Push notification helper ───────────────────────────────────────────

EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send"


def is_expo_push_token(token: str) -> bool:
    return token.startswith("ExponentPushToken[") or token.startswith("ExpoPushToken[")


async def _send_expo_push(token: str, title: str, body: str, data: Optional[dict] = None, channel_id: str = "default") -> bool:
    """Send a single Expo push notification. Returns True on success."""
    if not is_expo_push_token(token):
        logger.warning(f"Invalid Expo push token: {token}")
        return False

    message = {
        "to": token,
        "sound": "default",
        "title": title,
        "body": body,
        "data": data or {},
        "channelId": channel_id,
    }

    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.post(EXPO_PUSH_URL, json={"messages": [message]}, headers={"Accept": "application/json", "Content-Type": "application/json"})
        resp.raise_for_status()
        result = resp.json()

    ticket = result.get("data", [{}])[0]
    if ticket.get("status") == "error":
        details = ticket.get("details", {})
        logger.error(f"Expo push error for {token}: {ticket.get('message')} — {details}")
        if details.get("error") == "DeviceNotRegistered":
            return False  # caller should clear token
    return True


# ─── Celery tasks (equivalent to BullMQ workers) ─────────────────────────────

@celery_app.task(
    bind=True,
    max_retries=3,
    default_retry_delay=5,
    name="notifications.send_push",
)
def send_push_task(self, user_id: str, title: str, body: str, data: Optional[dict] = None, channel_id: str = "default"):
    """Background task: fetch token and send Expo push."""
    import asyncio
    from app.utils.database import connect_db
    from app.models.models import User

    async def _run():
        await connect_db()
        user = await User.get(user_id)
        if not user or not user.expo_push_token:
            return
        token = user.expo_push_token
        ok = await _send_expo_push(token, title, body, data, channel_id)
        if not ok:
            # DeviceNotRegistered — clear stale token
            user.expo_push_token = None
            await user.save()

    try:
        asyncio.run(_run())
    except Exception as exc:
        logger.error(f"Push task failed for {user_id}: {exc}")
        raise self.retry(exc=exc)


@celery_app.task(name="notifications.workout_reminder")
def workout_reminder_task(user_id: str, workout_name: str):
    """Relay a scheduled reminder into the send_push queue."""
    send_push_task.delay(
        user_id=user_id,
        title="Time to train! 💪",
        body=f"Your {workout_name} workout is scheduled for today.",
        data={"screen": "workouts"},
        channel_id="reminders",
    )


@celery_app.task(name="plans.weekly_regeneration")
def weekly_regeneration_task():
    """Regenerate active plans weekly for all users with an active plan."""
    import asyncio
    from app.utils.database import connect_db
    from app.models.models import User
    from app.services.plan_adaptive_service import regenerate_active_plan_for_user

    async def _run():
        await connect_db()
        users = await User.find(User.is_active == True).to_list()  # noqa: E712
        done = 0
        for u in users:
            out = await regenerate_active_plan_for_user(str(u.id), reason="weekly_auto")
            if out:
                done += 1
        logger.info(f"Weekly regeneration complete: regenerated={done}")

    asyncio.run(_run())


# ─── Periodic beat schedule (cron reminders) ─────────────────────────────────
# Users' reminders are stored in Redis as Celery beat entries.
# The schedule is rebuilt on every app start from the DB.

async def rebuild_reminder_schedule():
    """Called at startup to re-register all user reminders in Celery beat."""
    from app.utils.database import connect_db
    from app.models.models import User

    await connect_db()
    users = await User.find_all().to_list()

    schedule = {}
    for user in users:
        reminder = getattr(user, "reminder", None)
        if not reminder or not reminder.get("enabled"):
            continue
        uid = str(user.id)
        hour   = reminder.get("hour", 8)
        minute = reminder.get("minute", 0)
        name   = reminder.get("workout_name", "your workout")
        schedule[f"reminder-{uid}"] = {
            "task":     "notifications.workout_reminder",
            "schedule": crontab(hour=hour, minute=minute),
            "kwargs":   {"user_id": uid, "workout_name": name},
        }

    schedule["weekly-plan-regeneration"] = {
        "task": "plans.weekly_regeneration",
        "schedule": crontab(hour=3, minute=0, day_of_week="mon"),
        "kwargs": {},
    }
    celery_app.conf.beat_schedule = schedule
    logger.info(f"⏰ Rebuilt {len(schedule)} reminder schedules")


# ─── Public async API (called from FastAPI routes) ───────────────────────────

async def send_push_notification(user_id: str, title: str, body: str, data: Optional[dict] = None) -> None:
    """Enqueue an immediate push notification (async fire-and-forget)."""
    send_push_task.delay(user_id=user_id, title=title, body=body, data=data)


async def schedule_workout_reminder(user_id: str, workout_name: str, hour: int, minute: int) -> None:
    """Persist reminder config on user document and update Celery beat."""
    from app.models.models import User

    user = await User.get(user_id)
    if not user:
        return

    # Store on user document so it survives restarts
    user_dict = user.model_dump()
    user_dict["reminder"] = {"enabled": True, "hour": hour, "minute": minute, "workout_name": workout_name}
    # Celery beat schedule update
    celery_app.conf.beat_schedule[f"reminder-{user_id}"] = {
        "task":     "notifications.workout_reminder",
        "schedule": crontab(hour=hour, minute=minute),
        "kwargs":   {"user_id": user_id, "workout_name": workout_name},
    }
    logger.info(f"Scheduled daily reminder for {user_id} at {hour:02d}:{minute:02d}")


async def cancel_workout_reminder(user_id: str) -> None:
    """Remove a user's scheduled reminder."""
    key = f"reminder-{user_id}"
    celery_app.conf.beat_schedule.pop(key, None)
    logger.info(f"Cancelled reminder for {user_id}")


async def notify_achievement_unlocked(user_id: str, achievement_name: str, icon: str) -> None:
    await send_push_notification(
        user_id,
        f"{icon} Achievement Unlocked!",
        f'You earned "{achievement_name}". Keep it up!',
        {"screen": "achievements"},
    )


async def notify_streak_at_risk(user_id: str, streak_days: int) -> None:
    await send_push_notification(
        user_id,
        "⚠️ Streak at risk!",
        f"Don't lose your {streak_days}-day streak! Quick workout before midnight?",
        {"screen": "workouts"},
    )
