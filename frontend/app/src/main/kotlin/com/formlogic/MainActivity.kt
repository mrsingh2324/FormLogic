package com.formlogic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.formlogic.models.EXERCISE_LIBRARY
import com.formlogic.screens.*
import com.formlogic.services.ExerciseInput
import com.formlogic.services.SaveSessionRequest
import com.formlogic.ui.theme.FormLogicTheme
import com.formlogic.viewmodels.*

// ─── Route helpers ────────────────────────────────────────────────────────────

object R {
    const val AUTH         = "auth"
    const val ONBOARDING   = "onboarding"
    const val HOME         = "home"
    const val COACH        = "coach"
    const val WORKOUTS     = "workouts"
    const val NUTRITION    = "nutrition"
    const val PROGRESS     = "progress"
    const val PROFILE      = "profile"
    const val HISTORY      = "history"
    const val PLANS        = "plans"
    const val ACHIEVEMENTS = "achievements"
    const val PAYWALL      = "paywall"
    const val TUTORIAL     = "tutorial/{exerciseId}"
    const val WARMUP       = "warmup/{isWarmup}"
    const val CAMERA       = "camera/{exerciseId}/{exerciseName}/{targetReps}/{targetSets}"
    const val SUMMARY      = "summary/{reps}/{duration}/{score}/{calories}/{exerciseName}"
    const val VERIFY       = "verify/{token}"
    const val RESET_PW     = "resetpw/{token}"

    fun camera(id: String, name: String, reps: Int = 15, sets: Int = 3) =
        "camera/$id/${name.replace(" ", "_")}/$reps/$sets"
    fun warmupBefore(exerciseId: String, exerciseName: String, reps: Int, sets: Int) =
        "warmup_before/$exerciseId/${exerciseName.replace(" ", "_")}/$reps/$sets"
    fun summary(reps: Int, dur: Int, score: Float, cal: Int, name: String) =
        "summary/$reps/$dur/$score/$cal/${name.replace(" ", "_")}"
    fun tutorial(id: String) = "tutorial/$id"
    fun warmup(isWarmup: Boolean) = "warmup/$isWarmup"

    // Warmup-before-camera route pattern
    const val WARMUP_BEFORE =
        "warmup_before/{exerciseId}/{exerciseName}/{targetReps}/{targetSets}"
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon:  androidx.compose.ui.graphics.vector.ImageVector,
)
private val NAV_ITEMS = listOf(
    NavItem(R.HOME,      "Today",     Icons.Default.Home),
    NavItem(R.COACH,     "Coach",     Icons.Default.SmartToy),
    NavItem(R.PLANS,     "Plan",      Icons.Default.FitnessCenter),
    NavItem(R.NUTRITION, "Nutrition", Icons.Default.Restaurant),
    NavItem(R.PROGRESS,  "Progress",  Icons.Default.TrendingUp),
    NavItem(R.PROFILE,   "Profile",   Icons.Default.Person),
)
private val BOTTOM_ROUTES = NAV_ITEMS.map { it.route }.toSet()

// ─── Shared session state holder (survives recomposition between routes) ──────

/**
 * Holds per-rep score list from WorkoutCameraScreen so WorkoutSummaryScreen
 * can persist the full detail to the backend.
 */
object SessionBuffer {
    var perRepScores: List<Float> = emptyList()
    var exerciseId:   String = ""
    var exerciseName: String = ""
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FormLogicTheme { App() } }
    }
}

@Composable
fun App() {
    val nav       = rememberNavController()
    val authVm: AuthViewModel       = viewModel()
    val workoutVm: WorkoutViewModel = viewModel()
    val isLoggedIn by authVm.isLoggedIn.collectAsStateWithLifecycle(false)
    val authState  by authVm.state.collectAsStateWithLifecycle()
    val backStack  by nav.currentBackStackEntryAsState()
    val curRoute   = backStack?.destination?.route
    val showBar    = isLoggedIn && curRoute in BOTTOM_ROUTES

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedOut) nav.navigate(R.AUTH) { popUpTo(0) { inclusive = true } }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            icon     = { Icon(item.icon, item.label) },
                            label    = { Text(item.label) },
                            selected = backStack?.destination?.hierarchy?.any { it.route == item.route } == true,
                            colors   = NavigationBarItemDefaults.colors(
                                selectedIconColor   = Color.White,
                                selectedTextColor   = Color.White,
                                indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            nav,
            startDestination = if (isLoggedIn) R.HOME else R.AUTH,
            Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF090A11), Color(0xFF151529))))
                .padding(pad)
        ) {

            composable(R.AUTH) {
                AuthScreen(authVm,
                    onSuccess         = { nav.navigate(R.HOME) { popUpTo(R.AUTH) { inclusive = true } } },
                    onRegisterSuccess = { nav.navigate(R.ONBOARDING) { popUpTo(R.AUTH) { inclusive = true } } }
                )
            }

            composable(R.ONBOARDING) {
                OnboardingScreen(authVm) {
                    nav.navigate(R.HOME) { popUpTo(R.ONBOARDING) { inclusive = true } }
                }
            }

            composable(R.HOME) {
                TodayScreen(authVm, workoutVm,
                    onOpenPlans = { nav.navigate(R.PLANS) },
                    onOpenChat  = { nav.navigate(R.COACH) }
                )
            }

            composable(R.COACH)       { CoachChatScreen(authVm) }
            composable(R.WORKOUTS)    { WorkoutsScreen(workoutVm) { nav.navigate(R.HOME) } }
            composable(R.NUTRITION)   { NutritionScreen(authVm) }
            composable(R.PROGRESS)    { ProgressScreen(workoutVm, authVm) }
            composable(R.PROFILE)     { ProfileScreen(authVm) {} }
            composable(R.HISTORY)     { HistoryScreen(workoutVm) { nav.popBackStack() } }
            composable(R.PLANS)       { PlanIntelligenceScreen(authVm) }
            composable(R.ACHIEVEMENTS){ AchievementsScreen(authVm) }
            composable(R.PAYWALL)     { PaywallScreen({ nav.popBackStack() }, {}, { nav.popBackStack() }) }

            // ── Tutorial ──────────────────────────────────────────────────────
            composable(R.TUTORIAL, listOf(navArgument("exerciseId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("exerciseId") ?: "squat"
                val ex = EXERCISE_LIBRARY.find { it.id == id } ?: EXERCISE_LIBRARY.first()
                ExerciseTutorialScreen(
                    exercise       = ex,
                    onStartWorkout = {
                        // Warmup → Camera (trainer-style flow)
                        nav.navigate(R.warmupBefore(ex.id, ex.name, ex.targetReps, ex.targetSets))
                    },
                    onBack = { nav.popBackStack() }
                )
            }

            // ── Standalone warmup / cooldown ──────────────────────────────────
            composable(R.WARMUP, listOf(navArgument("isWarmup") { type = NavType.BoolType })) { entry ->
                WarmupCooldownScreen(
                    isWarmup   = entry.arguments?.getBoolean("isWarmup") ?: true,
                    onComplete = { nav.popBackStack() },
                    onBack     = { nav.popBackStack() }
                )
            }

            // ── Warmup-before-workout (routes into camera on completion) ──────
            composable(R.WARMUP_BEFORE, listOf(
                navArgument("exerciseId")   { type = NavType.StringType },
                navArgument("exerciseName") { type = NavType.StringType },
                navArgument("targetReps")   { type = NavType.IntType },
                navArgument("targetSets")   { type = NavType.IntType },
            )) { entry ->
                val eid   = entry.arguments?.getString("exerciseId")   ?: "squat"
                val ename = entry.arguments?.getString("exerciseName")?.replace("_", " ") ?: "Squat"
                val reps  = entry.arguments?.getInt("targetReps")      ?: 15
                val sets  = entry.arguments?.getInt("targetSets")      ?: 3
                WarmupCooldownScreen(
                    isWarmup   = true,
                    onComplete = { nav.navigate(R.camera(eid, ename, reps, sets)) { popUpTo(R.WARMUP_BEFORE) { inclusive = true } } },
                    onBack     = { nav.popBackStack() }
                )
            }

            // ── Camera (live workout) ─────────────────────────────────────────
            composable(R.CAMERA, listOf(
                navArgument("exerciseId")   { type = NavType.StringType },
                navArgument("exerciseName") { type = NavType.StringType },
                navArgument("targetReps")   { type = NavType.IntType },
                navArgument("targetSets")   { type = NavType.IntType },
            )) { entry ->
                val id   = entry.arguments?.getString("exerciseId")   ?: "squat"
                val name = entry.arguments?.getString("exerciseName")?.replace("_", " ") ?: "Squat"
                val reps = entry.arguments?.getInt("targetReps") ?: 15
                val sets = entry.arguments?.getInt("targetSets") ?: 3

                WorkoutCameraScreen(
                    exerciseId   = id,
                    exerciseName = name,
                    targetReps   = reps,
                    targetSets   = sets,
                    onFinish     = { totalReps, dur, score, cal, perRepScores ->
                        // Buffer per-rep data before navigating (can't pass List<Float> via nav args)
                        SessionBuffer.perRepScores = perRepScores
                        SessionBuffer.exerciseId   = id
                        SessionBuffer.exerciseName = name
                        nav.navigate(R.summary(totalReps, dur, score, cal, name)) { popUpTo(R.HOME) }
                    },
                    onBack = { nav.popBackStack() }
                )
            }

            // ── Summary + save ────────────────────────────────────────────────
            composable(R.SUMMARY, listOf(
                navArgument("reps")         { type = NavType.IntType },
                navArgument("duration")     { type = NavType.IntType },
                navArgument("score")        { type = NavType.FloatType },
                navArgument("calories")     { type = NavType.IntType },
                navArgument("exerciseName") { type = NavType.StringType },
            )) { entry ->
                val totalReps = entry.arguments?.getInt("reps")            ?: 0
                val duration  = entry.arguments?.getInt("duration")        ?: 0
                val avgScore  = entry.arguments?.getFloat("score")         ?: 0f
                val calories  = entry.arguments?.getInt("calories")        ?: 0
                val exName    = entry.arguments?.getString("exerciseName")?.replace("_", " ") ?: ""

                WorkoutSummaryScreen(
                    totalReps       = totalReps,
                    durationSeconds = duration,
                    avgFormScore    = avgScore,
                    caloriesBurned  = calories,
                    exerciseName    = exName,
                    onSave = {
                        // ── Persist to backend with full per-rep form scores ──
                        val perRepScores = SessionBuffer.perRepScores
                        val request = SaveSessionRequest(
                            duration  = duration,
                            exercises = listOf(
                                ExerciseInput(
                                    exercise_id    = SessionBuffer.exerciseId.ifBlank { "unknown" },
                                    exercise_name  = exName,
                                    reps           = totalReps,
                                    sets           = entry.arguments?.getInt("targetSets") ?: 1,
                                    form_scores    = perRepScores.map { it.toDouble() },
                                    avg_form_score = avgScore.toDouble(),
                                    completed      = true,
                                )
                            ),
                            notes = null,
                            mood  = null,
                        )
                        workoutVm.saveSession(
                            req     = request,
                            onSuccess = {
                                SessionBuffer.perRepScores = emptyList()
                                workoutVm.loadHistory()
                                nav.navigate(R.HOME) { popUpTo(R.HOME) }
                            },
                            onError = { _ ->
                                // Offline: navigate home anyway, data will be retried
                                nav.navigate(R.HOME) { popUpTo(R.HOME) }
                            }
                        )
                    },
                    onDiscard = {
                        SessionBuffer.perRepScores = emptyList()
                        nav.navigate(R.HOME) { popUpTo(R.HOME) }
                    }
                )
            }

            // ── Auth utils ────────────────────────────────────────────────────
            composable(R.VERIFY, listOf(navArgument("token") { type = NavType.StringType })) { entry ->
                VerifyEmailScreen(entry.arguments?.getString("token"), authVm) {
                    nav.navigate(R.AUTH) { popUpTo(0) }
                }
            }
            composable(R.RESET_PW, listOf(navArgument("token") { type = NavType.StringType })) { entry ->
                ResetPasswordScreen(entry.arguments?.getString("token"), authVm) {
                    nav.navigate(R.AUTH) { popUpTo(0) }
                }
            }
        }
    }
}
