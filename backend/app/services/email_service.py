"""Email service — replaces services/emailService.ts"""
import os
import aiosmtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from app.utils.logger import logger

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASS = os.getenv("SMTP_PASS", "")
APP_URL = os.getenv("APP_URL", "https://formlogic.ai")
FROM_ADDR = f"FormLogic AI <{SMTP_USER}>"


async def _send(to: str, subject: str, html: str):
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = FROM_ADDR
    msg["To"] = to
    msg.attach(MIMEText(html, "html"))
    await aiosmtplib.send(msg, hostname=SMTP_HOST, port=SMTP_PORT, username=SMTP_USER, password=SMTP_PASS, start_tls=True)


async def send_verification_email(email: str, name: str, token: str):
    link = f"{APP_URL}/verify-email?token={token}"
    html = f"""<div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
        <h2 style="color:#6C63FF">Welcome to FormLogic AI, {name}! 💪</h2>
        <p>Please verify your email address to activate your account.</p>
        <a href="{link}" style="display:inline-block;background:#6C63FF;color:#fff;padding:14px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">Verify Email</a>
        <p style="color:#666;font-size:13px">This link expires in 24 hours.<br>If you didn't create a FormLogic account, ignore this email.</p>
    </div>"""
    await _send(email, "Verify your FormLogic AI account", html)
    logger.info(f"Verification email sent to {email}")


async def send_password_reset_email(email: str, name: str, token: str):
    link = f"{APP_URL}/reset-password?token={token}"
    html = f"""<div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
        <h2 style="color:#6C63FF">Password Reset Request</h2>
        <p>Hi {name}, we received a request to reset your password.</p>
        <a href="{link}" style="display:inline-block;background:#6C63FF;color:#fff;padding:14px 28px;border-radius:8px;text-decoration:none;font-weight:600;margin:16px 0">Reset Password</a>
        <p style="color:#666;font-size:13px">This link expires in 1 hour.<br>If you did not request a reset, your account is safe — ignore this email.</p>
    </div>"""
    await _send(email, "Reset your FormLogic AI password", html)


async def send_welcome_email(email: str, name: str):
    html = f"""<div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
        <h2 style="color:#6C63FF">Account verified! 🎉</h2>
        <p>Hi {name}, your FormLogic AI account is now active.</p>
        <p>Open the app and complete your first workout to start earning badges.</p>
    </div>"""
    await _send(email, "You're all set — let's train! 🏋️", html)
