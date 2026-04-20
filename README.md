# FormLogic AI — Complete Tech Stack Migration

## Stack Summary

| Layer        | Original                  | Converted                              |
|--------------|---------------------------|----------------------------------------|
| Frontend     | React Native / Expo (TSX) | **Kotlin + Jetpack Compose** (Android) |
| Backend      | Node.js / Express (TS)    | **Python / FastAPI**                   |
| ORM          | Mongoose                  | Beanie (async MongoDB ODM)             |
| Auth tokens  | jsonwebtoken              | python-jose                            |
| Passwords    | bcryptjs                  | bcrypt (Python, cost=12)               |
| State mgmt   | Zustand                   | ViewModel + StateFlow                  |
| Local storage| AsyncStorage              | Jetpack DataStore                      |
| HTTP client  | Axios                     | Retrofit 2 + OkHttp                    |
| Navigation   | Expo Router               | Jetpack Navigation Compose             |
| Camera       | VisionCamera + expo-camera| CameraX                                |
| Voice        | expo-speech               | Android TextToSpeech (en-IN)           |
| Email        | Nodemailer                | aiosmtplib                             |
| GCS Storage  | @google-cloud/storage     | google-cloud-storage (Python)          |
| AI           | @google/genai (Gemini)    | google-generativeai (Python)           |
| Tests        | Jest                      | pytest                                 |
| CI/CD        | GitHub Actions            | GitHub Actions (updated)               |
| Container    | Node 18 Dockerfile        | Python 3.12 Dockerfile                 |

---

## Backend Setup

```bash
cd backend
python -m venv venv && source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env        # Fill in your secrets
mkdir -p logs
python -m app.utils.seed_data   # Optional: seed Indian food database
uvicorn app.main:app --reload --port 5000
```

Swagger UI → **http://localhost:5000/api/docs**

Run tests: `pytest tests/ -v`

Docker full stack: `docker-compose up --build`

---

## Frontend Setup

1. Open `frontend/` in Android Studio (Hedgehog+, JDK 17, SDK 35)
2. Add to `local.properties`:
   ```
   BASE_URL=http://10.0.2.2:5000
   ```
3. Sync Gradle → Run ▶

Build release APK: `./gradlew assembleRelease`

---

## File Map

```
backend/
  app/main.py                         FastAPI app entry point
  app/models/models.py                Beanie ODM (User, Workout, Plan, Nutrition…)
  app/routes/auth.py                  Auth routes (register/login/refresh/reset)
  app/routes/all_routes.py            All other routers (13 groups)
  app/middleware/                     auth_middleware, error_middleware
  app/services/                       email, AI (Gemini), GCS storage, plan generator
  app/utils/                          jwt_utils, database, logger, startup, tdee, analytics, seed_data
  tests/test_backend.py               pytest: JWT, TDEE, BMI, plan generator

frontend/app/src/main/kotlin/com/formlogic/
  MainActivity.kt                     NavHost + bottom bar (all 15 routes wired)
  FormLogicApp.kt                     Application class
  screens/Screens.kt                  Auth, Home, Workouts, Progress, History, Plans, Achievements, Profile, VerifyEmail, ResetPassword
  screens/NutritionScreen.kt          Full nutrition: food search, macro tracking, meal logging
  screens/WorkoutCameraScreen.kt      CameraX + skeleton overlay + rep detection
  screens/OnboardingScreen.kt         5-step profile setup
  screens/AdditionalScreens.kt        ExerciseTutorial, WarmupCooldown, Paywall, WorkoutSummary
  components/Components.kt            FormScoreRing, FormScoreBadge, RepCounter, FeedbackBanner, SkeletonOverlay, ExercisePicker, WorkoutControls, SocialAuthButtons, RestTimer, FirstWorkoutGuide
  viewmodels/AuthViewModel.kt         Login / register / logout / forgotPw / resetPw
  viewmodels/WorkoutViewModel.kt      History / stats / save session
  viewmodels/PoseDetectionViewModel.kt VoiceCoach (TTS) + FormFeedback + RepDetector
  store/AuthStore.kt                  DataStore token + user persistence
  store/WorkoutStore.kt               Session state (Zustand → StateFlow replacement)
  services/ApiService.kt              Full Retrofit interface (40+ endpoints)
  utils/Utils.kt                      TDEE, BMI, PoseDetection math, FeatureFlags, OfflineQueue
  ui/theme/Theme.kt                   Material3 dark theme (FormLogic purple brand)
  AndroidManifest.xml                 Camera, internet permissions + deep-link intent filters
```

---

## API (identical paths to original Node.js backend)

All 40+ endpoints preserved. Base: `/api/v1/`

Auth, Social Auth, Users, Workouts, Nutrition, Plans, Achievements, Tracking, Notifications, Privacy, Upload, AI, Webhooks, Health — all identical to original.
