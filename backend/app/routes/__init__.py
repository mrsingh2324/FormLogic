from app.routes.auth import router as auth_router
from app.routes.all_routes import (
    workout_router,
    user_router,
    nutrition_router,
    plan_router,
    achievement_router,
    tracking_router,
    ai_router,
    report_router,
    privacy_router,
    social_auth_router,
    notification_router,
    upload_router,
    webhook_router,
)

__all__ = [
    "auth_router",
    "workout_router",
    "user_router",
    "nutrition_router",
    "plan_router",
    "achievement_router",
    "tracking_router",
    "ai_router",
    "report_router",
    "privacy_router",
    "social_auth_router",
    "notification_router",
    "upload_router",
    "webhook_router",
]
