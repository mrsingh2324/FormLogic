"""Analytics event tracking — replaces utils/analytics.ts"""
import os
from app.utils.logger import logger


def track_event(user_id: str, event: dict) -> None:
    """Fire-and-forget analytics event. Wire up PostHog/Mixpanel here."""
    logger.info(f"[analytics] user={user_id} event={event.get('name')} props={event.get('props', {})}")
