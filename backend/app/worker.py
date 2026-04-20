"""
FormLogic Celery worker + beat scheduler.

Start the worker:
    celery -A app.worker worker --loglevel=info

Start the beat scheduler (runs alongside worker in prod):
    celery -A app.worker beat --loglevel=info

Or combined (dev only):
    celery -A app.worker worker --beat --loglevel=info
"""
from __future__ import annotations

import os
from celery import Celery
from celery.schedules import crontab

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")

celery_app = Celery(
    "formlogic",
    broker=REDIS_URL,
    backend=REDIS_URL,
    include=["app.tasks"],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    # Retry failed tasks once before marking as failure
    task_acks_late=True,
    task_reject_on_worker_lost=True,
    # Beat schedule ─────────────────────────────────────────────────────────
    beat_schedule={
        # Every Monday at 02:00 UTC — regenerate adaptive plans for all users
        "weekly-plan-regen": {
            "task": "app.tasks.regen_all_active_plans",
            "schedule": crontab(hour=2, minute=0, day_of_week=1),
        },
        # Every day at 06:00 UTC — send workout reminder push notifications
        "daily-workout-reminders": {
            "task": "app.tasks.send_daily_reminders",
            "schedule": crontab(hour=6, minute=0),
        },
    },
)
