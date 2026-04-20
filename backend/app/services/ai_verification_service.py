"""AI verification service — replaces services/aiVerificationService.ts"""
import os
import json
from app.utils.logger import logger


async def verify_action(prompt: str, context=None) -> str:
    try:
        import google.generativeai as genai
        genai.configure(api_key=os.getenv("GEMINI_API_KEY", ""))
        model = genai.GenerativeModel("gemini-2.5-flash")
        full_prompt = f"{prompt}\nContext: {json.dumps(context)}" if context else prompt
        response = model.generate_content(full_prompt)
        return response.text
    except Exception as e:
        logger.error(f"AI Verification Error: {e}")
        raise RuntimeError("AI verification failed")
