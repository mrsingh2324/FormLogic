"""
test_backend.py — Complete pytest suite for FormLogic Python/FastAPI backend
"""
import pytest
import pytest_asyncio
from datetime import datetime, timedelta


# ─── JWT ──────────────────────────────────────────────────────────────────────

class TestJWT:
    def test_access_token_roundtrip(self):
        from app.utils.jwt_utils import generate_access_token, verify_access_token
        token = generate_access_token("user123", "test@example.com")
        payload = verify_access_token(token)
        assert payload["user_id"] == "user123"
        assert payload["email"] == "test@example.com"

    def test_refresh_token_roundtrip(self):
        from app.utils.jwt_utils import generate_refresh_token, verify_refresh_token
        token = generate_refresh_token("user456", "user@example.com")
        payload = verify_refresh_token(token)
        assert payload["user_id"] == "user456"

    def test_expired_token_raises(self):
        from jose import jwt
        import os
        from app.utils.jwt_utils import verify_access_token
        from fastapi import HTTPException
        expired = jwt.encode(
            {"user_id": "x", "email": "x@x.com", "exp": datetime.utcnow() - timedelta(seconds=1)},
            os.environ["JWT_SECRET"], algorithm="HS256"
        )
        with pytest.raises(HTTPException) as exc:
            verify_access_token(expired)
        assert exc.value.status_code == 401


# ─── TDEE / BMI ───────────────────────────────────────────────────────────────

class TestTDEE:
    def test_male_muscle_gain(self):
        from app.utils.tdee import calculate_nutrition_targets, UserMetrics
        r = calculate_nutrition_targets(UserMetrics(weight_kg=80, height_cm=175, age_years=25, gender="male", fitness_level="intermediate", goals=["muscle_gain"], days_per_week=4))
        assert 1200 <= r.calories <= 4000
        assert r.protein > 100
        assert r.calories % 50 == 0

    def test_female_weight_loss(self):
        from app.utils.tdee import calculate_nutrition_targets, UserMetrics
        r = calculate_nutrition_targets(UserMetrics(weight_kg=65, height_cm=162, age_years=30, gender="female", fitness_level="beginner", goals=["weight_loss"]))
        assert r.calories >= 1200
        assert r.protein > 80

    def test_macro_calories_sum(self):
        from app.utils.tdee import calculate_nutrition_targets, UserMetrics
        r = calculate_nutrition_targets(UserMetrics(goals=["muscle_gain"]))
        macro_cals = r.protein * 4 + r.carbs * 4 + r.fats * 9
        assert abs(macro_cals - r.calories) / r.calories < 0.05

    def test_bmi_healthy(self):
        from app.utils.tdee import calculate_bmi
        r = calculate_bmi(70, 175)
        assert r.category == "Healthy"
        assert 22 <= r.bmi <= 23

    def test_bmi_obese(self):
        from app.utils.tdee import calculate_bmi
        assert calculate_bmi(130, 170).category == "Obese"

    def test_bmi_underweight(self):
        from app.utils.tdee import calculate_bmi
        assert calculate_bmi(40, 175).category == "Underweight"


# ─── Plan Generator ────────────────────────────────────────────────────────────

class TestPlanGenerator:
    def test_structure(self):
        from app.services.plan_generator import generate_workout_plan
        s = generate_workout_plan("weight_loss", "beginner", 4, ["bodyweight"], 3)
        assert len(s) == 4
        for week in s:
            assert len(week.days) == 7

    def test_advanced_has_more_exercises(self):
        from app.services.plan_generator import generate_workout_plan
        s_beg = generate_workout_plan("muscle_gain", "beginner", 1, ["bodyweight"], 5)
        s_adv = generate_workout_plan("muscle_gain", "advanced", 1, ["bodyweight"], 5)
        beg_ex = sum(len(d.exercises) for d in s_beg[0].days if not d.is_rest)
        adv_ex = sum(len(d.exercises) for d in s_adv[0].days if not d.is_rest)
        assert adv_ex >= beg_ex

    def test_reps_increase_over_weeks(self):
        from app.services.plan_generator import generate_workout_plan
        s = generate_workout_plan("toning", "intermediate", 4, ["bodyweight"], 3)
        w1 = [e.target_reps for d in s[0].days for e in d.exercises if not d.is_rest]
        w4 = [e.target_reps for d in s[3].days for e in d.exercises if not d.is_rest]
        if w1 and w4:
            assert sum(w4) >= sum(w1)

    def test_unknown_goal_fallback(self):
        from app.services.plan_generator import generate_workout_plan
        s = generate_workout_plan("xyz_unknown", "beginner", 2, [], 3)
        assert len(s) == 2

    def test_bodyweight_only_no_dumbbells(self):
        from app.services.plan_generator import generate_workout_plan
        s = generate_workout_plan("muscle_gain", "intermediate", 1, ["bodyweight"], 3)
        dumbbell_ids = {"goblet_squat", "rdl", "db_press", "db_row", "db_curl", "db_lunge"}
        for d in s[0].days:
            for e in d.exercises:
                assert e.exercise_id not in dumbbell_ids

    def test_rest_days_present(self):
        from app.services.plan_generator import generate_workout_plan
        s = generate_workout_plan("weight_loss", "beginner", 1, ["bodyweight"], 3)
        rest = sum(1 for d in s[0].days if d.is_rest)
        assert rest >= 1


# ─── User Model ───────────────────────────────────────────────────────────────

class TestUserModel:
    def test_password_hash_and_verify(self):
        from app.models.models import User
        pw = "SecurePass123!"
        hashed = User.hash_password(pw)
        user = User(name="T", email="t@t.com", password_hash=hashed)
        assert user.verify_password(pw)
        assert not user.verify_password("wrong")

    def test_email_token(self):
        import hashlib
        from app.models.models import User
        user = User(name="T", email="t@t.com", password_hash="x")
        raw = user.generate_email_verification_token()
        assert len(raw) == 64
        assert user.email_verification_token == hashlib.sha256(raw.encode()).hexdigest()

    def test_lockout(self):
        from app.models.models import User
        user = User(name="T", email="t@t.com", password_hash="x")
        assert not user.is_locked()
        user.lock_until = datetime.utcnow() + timedelta(minutes=30)
        assert user.is_locked()

    def test_safe_dict(self):
        from app.models.models import User
        user = User(name="Alice", email="a@b.com", password_hash="secret")
        d = user.safe_dict()
        assert "password_hash" not in d
        assert d["name"] == "Alice"


# ─── WorkoutSession ───────────────────────────────────────────────────────────

class TestWorkoutSession:
    def test_aggregates(self):
        from app.models.models import WorkoutSession, ExercisePerformed
        exs = [
            ExercisePerformed(exercise_id="squat", exercise_name="Squat", reps=15, sets=3, form_scores=[85,90,88], avg_form_score=87.7, completed=True),
            ExercisePerformed(exercise_id="pushup", exercise_name="Push-Up", reps=12, sets=3, form_scores=[80,78,82], avg_form_score=80.0, completed=True),
        ]
        s = WorkoutSession(user_id="u1", duration=1800, exercises=exs)
        s.compute_aggregates()
        assert s.total_reps == 27
        assert s.avg_form_score > 0
        assert s.calories_burned > 0

    def test_empty_session(self):
        from app.models.models import WorkoutSession
        s = WorkoutSession(user_id="u1", duration=600, exercises=[])
        s.compute_aggregates()
        assert s.total_reps == 0


# ─── Achievements ─────────────────────────────────────────────────────────────

class TestAchievements:
    def test_definitions(self):
        from app.models.models import ACHIEVEMENT_DEFINITIONS
        assert len(ACHIEVEMENT_DEFINITIONS) >= 8
        for a in ACHIEVEMENT_DEFINITIONS:
            assert {"id","name","description","icon","category","condition","xp_reward","rarity"}.issubset(a.keys())

    def test_legendary_exists(self):
        from app.models.models import ACHIEVEMENT_DEFINITIONS
        assert any(a["rarity"] == "legendary" for a in ACHIEVEMENT_DEFINITIONS)

    def test_first_workout(self):
        from app.models.models import ACHIEVEMENT_DEFINITIONS
        first = next(a for a in ACHIEVEMENT_DEFINITIONS if a["id"] == "first_workout")
        assert first["condition"]["value"] == 1


# ─── API Integration (AsyncClient) ───────────────────────────────────────────

class TestAPI:
    @pytest_asyncio.fixture
    async def client(self):
        from httpx import AsyncClient, ASGITransport
        from app.main import app
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
            yield ac

    @pytest.mark.asyncio
    async def test_health(self, client):
        r = await client.get("/health")
        assert r.status_code in (200, 503)
        assert "status" in r.json()

    @pytest.mark.asyncio
    async def test_docs_accessible(self, client):
        r = await client.get("/api/docs")
        assert r.status_code == 200

    @pytest.mark.asyncio
    async def test_register_missing_fields(self, client):
        r = await client.post("/api/v1/auth/register", json={"email": "x@x.com"})
        assert r.status_code == 422

    @pytest.mark.asyncio
    async def test_login_invalid_creds(self, client):
        r = await client.post("/api/v1/auth/login", json={"email": "nobody@x.com", "password": "wrong"})
        assert r.status_code in (401, 422, 500)

    @pytest.mark.asyncio
    async def test_protected_without_token(self, client):
        r = await client.get("/api/v1/users/me")
        assert r.status_code in (401, 403)

    @pytest.mark.asyncio
    async def test_privacy_info_public(self, client):
        r = await client.get("/api/v1/privacy/info")
        assert r.status_code == 200
        assert r.json()["success"] is True

    @pytest.mark.asyncio
    async def test_food_search_public(self, client):
        r = await client.get("/api/v1/nutrition/food/search", params={"q": "dal"})
        assert r.status_code == 200

    @pytest.mark.asyncio
    async def test_forgot_password_always_200(self, client):
        r = await client.post("/api/v1/auth/forgot-password", json={"email": "nonexistent@x.com"})
        assert r.status_code == 200

    @pytest.mark.asyncio
    async def test_resend_verification_always_200(self, client):
        r = await client.post("/api/v1/auth/resend-verification", json={"email": "nobody@x.com"})
        assert r.status_code == 200
