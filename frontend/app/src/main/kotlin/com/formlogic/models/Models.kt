package com.formlogic.models

import androidx.compose.ui.graphics.Color
import kotlin.math.*

// ─── Pose / skeleton ──────────────────────────────────────────────────────────

data class Keypoint(val x: Float, val y: Float, val score: Float = 1f)

data class ExerciseAngles(
    val leftKnee:   Double? = null,
    val rightKnee:  Double? = null,
    val leftHip:    Double? = null,
    val rightHip:   Double? = null,
    val leftElbow:  Double? = null,
    val rightElbow: Double? = null,
    val trunkAngle: Double? = null,   // deviation from vertical (0 = upright)
    val leftShoulder: Double? = null,
    val rightShoulder: Double? = null,
)

data class FormFeedback(
    val score:          Float,
    val issues:         List<String>,
    val severity:       String,           // "good" | "warning" | "danger"
    val affectedJoints: List<Int> = emptyList(),
)

fun scoreColor(score: Float): Color = when {
    score >= 90f -> Color(0xFF43D9AD)
    score >= 75f -> Color(0xFF6C63FF)
    score >= 60f -> Color(0xFFFFB547)
    else         -> Color(0xFFFF4757)
}

fun scoreLabel(score: Float): String = when {
    score >= 90f -> "Excellent"
    score >= 75f -> "Good"
    score >= 60f -> "Fair"
    else         -> "Fix Form"
}

// ─── Skeleton connections (MediaPipe 33-point) ────────────────────────────────

val SKELETON_CONNECTIONS = listOf(
    7 to 2, 8 to 5,
    11 to 12, 11 to 23, 12 to 24, 23 to 24,
    11 to 13, 13 to 15,
    12 to 14, 14 to 16,
    23 to 25, 25 to 27, 27 to 29, 29 to 31,
    24 to 26, 26 to 28, 28 to 30, 30 to 32,
)
val LEFT_JOINTS  = setOf(11, 13, 15, 23, 25, 27, 29, 31)
val RIGHT_JOINTS = setOf(12, 14, 16, 24, 26, 28, 30, 32)

// ─── Rep detection ────────────────────────────────────────────────────────────

data class RepConfig(
    val startAngle:     Double,
    val bottomAngle:    Double,
    val hysteresis:     Double = 10.0,
    val minAngleChange: Double = 30.0,
    val isHold:         Boolean = false,   // true for plank / wall-sit (timed, not reps)
)

private val REP_CONFIGS = mapOf(
    // ── Lower body ────────────────────────────────────────────────────────────
    "squat"           to RepConfig(160.0, 90.0),
    "sumo_squat"      to RepConfig(160.0, 90.0),
    "jump_squat"      to RepConfig(160.0, 85.0),
    "lunge"           to RepConfig(160.0, 90.0, minAngleChange = 25.0),
    "glute_bridge"    to RepConfig(170.0, 130.0, 8.0, 25.0),
    "goblet_squat"    to RepConfig(160.0, 90.0),
    "rdl"             to RepConfig(170.0, 110.0, 10.0, 35.0),   // hip hinge
    // ── Upper body ────────────────────────────────────────────────────────────
    "pushup"          to RepConfig(165.0, 85.0, minAngleChange = 40.0),
    "diamond_pushup"  to RepConfig(165.0, 85.0, minAngleChange = 40.0),
    "wide_pushup"     to RepConfig(165.0, 85.0, minAngleChange = 40.0),
    "tricep_dip"      to RepConfig(165.0, 80.0, minAngleChange = 40.0),
    "pullup"          to RepConfig(160.0, 50.0, 12.0, 50.0),    // elbow angle
    "db_press"        to RepConfig(165.0, 80.0, minAngleChange = 45.0),
    "db_row"          to RepConfig(165.0, 70.0, minAngleChange = 50.0),
    // ── Core ──────────────────────────────────────────────────────────────────
    "crunch"          to RepConfig(150.0, 80.0),
    "mountain_climber"to RepConfig(160.0, 60.0, minAngleChange = 60.0),
    "plank"           to RepConfig(170.0, 160.0, 5.0, 5.0, isHold = true),
    // ── Cardio ────────────────────────────────────────────────────────────────
    "burpee"          to RepConfig(160.0, 80.0, minAngleChange = 50.0),
    "jumping_jack"    to RepConfig(170.0, 140.0, 8.0, 20.0),
    "high_knees"      to RepConfig(170.0, 120.0, 8.0, 30.0),
)

class RepDetector(exerciseId: String) {
    private val cfg    = REP_CONFIGS[exerciseId] ?: REP_CONFIGS["squat"]!!
    private var phase  = "start"
    private var minA   = Double.MAX_VALUE
    private var maxA   = 0.0
    var repCount = 0; private set

    val isHold get() = cfg.isHold

    fun update(angle: Double): Boolean {
        if (angle < minA) minA = angle
        if (angle > maxA) maxA = angle
        when (phase) {
            "start"  -> if (angle >= cfg.startAngle  - cfg.hysteresis) { phase = "down"; minA = angle }
            "down"   -> if (angle <= cfg.bottomAngle + cfg.hysteresis) phase = "bottom"
                        else if (angle > cfg.startAngle + cfg.hysteresis) phase = "start"
            "bottom" -> if (angle >= cfg.startAngle  - cfg.hysteresis) {
                if (maxA - minA >= cfg.minAngleChange) {
                    phase = "start"; repCount++; minA = Double.MAX_VALUE; maxA = 0.0; return true
                }
                phase = "start"
            }
        }
        return false
    }

    fun reset() { phase = "start"; minA = Double.MAX_VALUE; maxA = 0.0; repCount = 0 }
}

// ─── Exercise library ─────────────────────────────────────────────────────────

data class ExerciseOption(
    val id:           String,
    val name:         String,
    val emoji:        String,
    val targetSets:   Int,
    val targetReps:   Int,
    val description:  String,
    val muscleGroups: List<String>,
)

val EXERCISE_LIBRARY = listOf(
    ExerciseOption("squat",           "Bodyweight Squat",  "🦵", 3, 15, "Full depth squat",              listOf("Quads","Glutes","Hamstrings")),
    ExerciseOption("sumo_squat",      "Sumo Squat",        "🏋️", 3, 15, "Wide stance squat",            listOf("Inner Thighs","Glutes")),
    ExerciseOption("jump_squat",      "Jump Squat",        "⚡", 3, 10, "Explosive squat jump",          listOf("Full Legs","Cardio")),
    ExerciseOption("pushup",          "Push-Up",           "💪", 3, 12, "Standard push-up",              listOf("Chest","Shoulders","Triceps")),
    ExerciseOption("diamond_pushup",  "Diamond Push-Up",   "💎", 3, 10, "Hands in diamond shape",        listOf("Triceps","Inner Chest")),
    ExerciseOption("wide_pushup",     "Wide Push-Up",      "🦅", 3, 12, "Wide hand placement",           listOf("Outer Chest","Shoulders")),
    ExerciseOption("lunge",           "Forward Lunge",     "🏃", 3, 12, "Alternating forward lunge",     listOf("Quads","Glutes","Balance")),
    ExerciseOption("plank",           "Plank",             "🧘", 3,  1, "Hold 30–60 seconds",            listOf("Core","Shoulders")),
    ExerciseOption("crunch",          "Crunch",            "🔥", 3, 20, "Basic abdominal crunch",        listOf("Abs")),
    ExerciseOption("mountain_climber","Mountain Climber",  "🏔️",3, 20, "Fast alternating knee drives",  listOf("Core","Cardio")),
    ExerciseOption("burpee",          "Burpee",            "🌀", 3, 10, "Full body explosive movement",  listOf("Full Body","Cardio")),
    ExerciseOption("glute_bridge",    "Glute Bridge",      "🌉", 3, 15, "Supine hip thrust",             listOf("Glutes","Hamstrings")),
    ExerciseOption("goblet_squat",    "Goblet Squat",      "🏺", 3, 12, "Squat holding weight at chest", listOf("Quads","Glutes","Core")),
    ExerciseOption("rdl",             "Romanian Deadlift", "🏋️", 3, 12, "Hip hinge with straight legs",  listOf("Hamstrings","Glutes","Lower Back")),
    ExerciseOption("tricep_dip",      "Tricep Dip",        "🪑", 3, 12, "Dips off bench or chair",       listOf("Triceps","Shoulders")),
    ExerciseOption("pullup",          "Pull-Up",           "🔝", 3,  6, "Full hang to chin over bar",    listOf("Lats","Biceps","Core")),
    ExerciseOption("db_press",        "Dumbbell Press",    "🏋️", 3, 12, "Flat or incline press",         listOf("Chest","Shoulders","Triceps")),
    ExerciseOption("db_row",          "Dumbbell Row",      "🚣", 3, 12, "Single-arm bent-over row",      listOf("Lats","Rhomboids","Biceps")),
    ExerciseOption("jumping_jack",    "Jumping Jack",      "⭐", 3, 30, "Rhythmic cardio",               listOf("Full Body","Cardio")),
    ExerciseOption("high_knees",      "High Knees",        "🦵", 3, 30, "Run in place, knees to waist", listOf("Hip Flexors","Cardio")),
)

// ─── TDEE ──────────────────────────────────────────────────────────────────────

data class UserMetrics(
    val weightKg: Double = 70.0, val heightCm: Double = 165.0, val ageYears: Int = 30,
    val gender: String = "other", val fitnessLevel: String = "beginner",
    val goals: List<String> = emptyList(), val daysPerWeek: Int = 3,
)

data class NutritionTargets(val calories: Int, val protein: Int, val carbs: Int, val fats: Int, val fiber: Int, val waterMl: Int)
data class BmiResult(val bmi: Double, val category: String, val colorHex: String)

private fun actMult(level: String, days: Int) =
    when { level == "advanced" || days >= 5 -> 1.725; level == "intermediate" || days >= 3 -> 1.55; else -> 1.375 }

private fun bmr(m: UserMetrics): Double {
    val male   = 10 * m.weightKg + 6.25 * m.heightCm - 5 * m.ageYears + 5
    val female = 10 * m.weightKg + 6.25 * m.heightCm - 5 * m.ageYears - 161
    return when (m.gender) { "male" -> male; "female" -> female; else -> (male + female) / 2 }
}

fun calculateNutritionTargets(m: UserMetrics): NutritionTargets {
    val tdee = bmr(m) * actMult(m.fitnessLevel, m.daysPerWeek)
    val goal = m.goals.firstOrNull()?.lowercase() ?: ""
    val raw  = when { "weight_loss" in goal -> tdee - 400; "muscle_gain" in goal -> tdee + 250; else -> tdee }
    val cal  = (raw.coerceIn(1200.0, 4000.0) / 50).toInt() * 50
    val (p, c, f) = when { "muscle_gain" in goal -> Triple(0.30, 0.45, 0.25); "weight_loss" in goal -> Triple(0.35, 0.35, 0.30); "endurance" in goal -> Triple(0.20, 0.55, 0.25); else -> Triple(0.25, 0.50, 0.25) }
    return NutritionTargets(cal, ((cal * p) / 4).toInt(), ((cal * c) / 4).toInt(), ((cal * f) / 9).toInt(), minOf(38, (cal / 1000.0 * 14).toInt()), (m.weightKg * 35).toInt())
}

fun calculateBmi(weightKg: Double, heightCm: Double): BmiResult {
    val h = heightCm / 100; val bmi = Math.round(weightKg / (h * h) * 10) / 10.0
    return when { bmi < 18.5 -> BmiResult(bmi, "Underweight", "#4FC3F7"); bmi < 25 -> BmiResult(bmi, "Healthy", "#43D9AD"); bmi < 30 -> BmiResult(bmi, "Overweight", "#FFB547"); else -> BmiResult(bmi, "Obese", "#FF4757") }
}

// ─── Pose math helpers ────────────────────────────────────────────────────────

fun isVisible(kp: Keypoint?, threshold: Float = 0.5f) = (kp?.score ?: 0f) >= threshold

fun calculateAngle(a: Keypoint, b: Keypoint, c: Keypoint): Double {
    var angle = abs((atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)) * 180.0 / PI)
    if (angle > 180) angle = 360 - angle
    return angle
}

/** Trunk lean: angle between vertical and the line from mid-hip to mid-shoulder. 0 = upright. */
fun calculateTrunkAngle(kps: List<Keypoint>): Double? {
    val lShoulder = kps.getOrNull(11); val rShoulder = kps.getOrNull(12)
    val lHip      = kps.getOrNull(23); val rHip      = kps.getOrNull(24)
    if (!isVisible(lShoulder) || !isVisible(rShoulder) || !isVisible(lHip) || !isVisible(rHip)) return null
    val midShoulderY = ((lShoulder!!.y + rShoulder!!.y) / 2)
    val midHipY      = ((lHip!!.y + rHip!!.y) / 2)
    val midShoulderX = ((lShoulder.x + rShoulder.x) / 2)
    val midHipX      = ((lHip.x + rHip.x) / 2)
    val dx = midShoulderX - midHipX
    val dy = midHipY - midShoulderY   // positive = shoulder above hip (normal)
    return abs(atan2(dx.toDouble(), dy.toDouble()) * 180.0 / PI)
}

fun calculateExerciseAngles(kps: List<Keypoint>): ExerciseAngles {
    if (kps.size < 33) return ExerciseAngles()
    return ExerciseAngles(
        leftKnee    = if (isVisible(kps[23]) && isVisible(kps[25]) && isVisible(kps[27])) calculateAngle(kps[23], kps[25], kps[27]) else null,
        rightKnee   = if (isVisible(kps[24]) && isVisible(kps[26]) && isVisible(kps[28])) calculateAngle(kps[24], kps[26], kps[28]) else null,
        leftHip     = if (isVisible(kps[11]) && isVisible(kps[23]) && isVisible(kps[25])) calculateAngle(kps[11], kps[23], kps[25]) else null,
        rightHip    = if (isVisible(kps[12]) && isVisible(kps[24]) && isVisible(kps[26])) calculateAngle(kps[12], kps[24], kps[26]) else null,
        leftElbow   = if (isVisible(kps[11]) && isVisible(kps[13]) && isVisible(kps[15])) calculateAngle(kps[11], kps[13], kps[15]) else null,
        rightElbow  = if (isVisible(kps[12]) && isVisible(kps[14]) && isVisible(kps[16])) calculateAngle(kps[12], kps[14], kps[16]) else null,
        leftShoulder = if (isVisible(kps[13]) && isVisible(kps[11]) && isVisible(kps[23])) calculateAngle(kps[13], kps[11], kps[23]) else null,
        rightShoulder = if (isVisible(kps[14]) && isVisible(kps[12]) && isVisible(kps[24])) calculateAngle(kps[14], kps[12], kps[24]) else null,
        trunkAngle  = calculateTrunkAngle(kps),
    )
}

fun getPrimaryAngle(exerciseId: String, angles: ExerciseAngles): Double = when (exerciseId) {
    "pushup", "diamond_pushup", "wide_pushup",
    "tricep_dip", "db_press"
        -> ((angles.leftElbow ?: 180.0) + (angles.rightElbow ?: 180.0)) / 2
    "pullup", "db_row"
        -> ((angles.leftElbow ?: 160.0) + (angles.rightElbow ?: 160.0)) / 2
    "rdl"
        -> angles.trunkAngle?.let { 180.0 - it } ?: 170.0   // trunk deviation
    "mountain_climber", "high_knees"
        -> ((angles.leftHip ?: 170.0) + (angles.rightHip ?: 170.0)) / 2
    "jumping_jack"
        -> ((angles.leftShoulder ?: 170.0) + (angles.rightShoulder ?: 170.0)) / 2
    else -> ((angles.leftKnee ?: 180.0) + (angles.rightKnee ?: 180.0)) / 2
}

// ─── Form feedback — full coverage for all exercises ─────────────────────────

fun getFormFeedback(exerciseId: String, angles: ExerciseAngles): FormFeedback {
    var ded = 0f
    val issues = mutableListOf<String>()
    val joints = mutableListOf<Int>()

    when (exerciseId) {
        // ── Squat family ─────────────────────────────────────────────────────
        "squat", "sumo_squat", "jump_squat", "goblet_squat" -> {
            val avgKnee = avg(angles.leftKnee, angles.rightKnee, 120.0)
            val avgHip  = avg(angles.leftHip,  angles.rightHip,  90.0)
            val trunk   = angles.trunkAngle ?: 0.0

            if (avgKnee > 105) { issues += "Go lower — aim for thighs parallel to floor"; ded += 20f; joints += listOf(25, 26) }
            val lk = angles.leftKnee ?: 120.0; val rk = angles.rightKnee ?: 120.0
            if (abs(lk - rk) > 20) { issues += "Uneven squat depth — balance left and right sides"; ded += 10f; joints += listOf(25, 26) }
            if (trunk > 45) { issues += "Chest falling forward — keep torso more upright"; ded += 15f; joints += listOf(11, 12, 23, 24) }
        }

        "lunge" -> {
            val frontKnee = angles.leftKnee ?: angles.rightKnee ?: 120.0
            val trunk     = angles.trunkAngle ?: 0.0
            if (frontKnee > 110) { issues += "Bend front knee to ~90° — step further forward"; ded += 20f; joints += listOf(25, 26) }
            if (trunk > 15) { issues += "Torso leaning — keep chest tall and upright"; ded += 10f; joints += listOf(11, 12) }
        }

        "glute_bridge" -> {
            val avgHip = avg(angles.leftHip, angles.rightHip, 160.0)
            if (avgHip < 150) { issues += "Push hips higher — squeeze glutes at the top"; ded += 20f; joints += listOf(23, 24) }
            val lh = angles.leftHip ?: 160.0; val rh = angles.rightHip ?: 160.0
            if (abs(lh - rh) > 15) { issues += "Hips uneven — keep pelvis level"; ded += 10f; joints += listOf(23, 24) }
        }

        "rdl" -> {
            val trunk = angles.trunkAngle ?: 0.0
            val avgKnee = avg(angles.leftKnee, angles.rightKnee, 170.0)
            if (trunk > 60) { issues += "Good hinge — but keep spine neutral, don't round back"; ded += 25f; joints += listOf(23, 24, 11, 12) }
            if (avgKnee < 150) { issues += "Keep legs nearly straight — slight bend only"; ded += 15f; joints += listOf(25, 26) }
        }

        // ── Push family ───────────────────────────────────────────────────────
        "pushup", "wide_pushup" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            val trunk    = angles.trunkAngle ?: 0.0
            if (avgElbow > 100) { issues += "Lower your chest more — elbows should reach ~90°"; ded += 25f; joints += listOf(13, 14) }
            if (trunk > 15) { issues += "Hips sagging — brace core and keep body in a straight line"; ded += 20f; joints += listOf(23, 24, 11, 12) }
        }

        "diamond_pushup" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            if (avgElbow > 110) { issues += "Lower chest fully — this is a tricep movement, go deep"; ded += 25f; joints += listOf(13, 14) }
        }

        "tricep_dip" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            val avgShoulder = avg(angles.leftShoulder, angles.rightShoulder, 45.0)
            if (avgElbow > 110) { issues += "Dip lower — elbows to ~90° for full tricep activation"; ded += 25f; joints += listOf(13, 14) }
            if (avgShoulder != null && avgShoulder < 30) { issues += "Shoulders shrugging — depress and retract shoulder blades"; ded += 15f; joints += listOf(11, 12) }
        }

        "db_press" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            if (avgElbow > 110) { issues += "Lower the weights more — elbows should reach ~90°"; ded += 20f; joints += listOf(13, 14) }
            val le = angles.leftElbow ?: 90.0; val re = angles.rightElbow ?: 90.0
            if (abs(le - re) > 20) { issues += "Uneven press — both arms should move in sync"; ded += 10f; joints += listOf(13, 14) }
        }

        // ── Pull family ───────────────────────────────────────────────────────
        "pullup" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            val trunk    = angles.trunkAngle ?: 0.0
            if (avgElbow > 130) { issues += "Pull higher — chin should clear the bar"; ded += 30f; joints += listOf(13, 14) }
            if (trunk > 20) { issues += "Reduce swing — keep body controlled, no kipping"; ded += 15f; joints += listOf(23, 24) }
        }

        "db_row" -> {
            val avgElbow = avg(angles.leftElbow, angles.rightElbow, 90.0)
            val trunk    = angles.trunkAngle ?: 0.0
            if (avgElbow > 120) { issues += "Row higher — pull elbow past your torso"; ded += 20f; joints += listOf(13, 14) }
            if (trunk > 50) { issues += "Keep back flat — don't rotate excessively"; ded += 15f; joints += listOf(11, 12, 23, 24) }
        }

        // ── Core family ───────────────────────────────────────────────────────
        "crunch" -> {
            val avgHip = avg(angles.leftHip, angles.rightHip, 90.0)
            if (avgHip != null && avgHip > 120) { issues += "Curl up more — lift shoulder blades off floor"; ded += 20f; joints += listOf(11, 12) }
        }

        "plank" -> {
            val trunk = angles.trunkAngle ?: 0.0
            val avgHip = avg(angles.leftHip, angles.rightHip, 180.0)
            if (trunk > 10) { issues += "Hips too high or too low — body should be a straight line"; ded += 25f; joints += listOf(23, 24) }
            if (avgHip != null && avgHip < 160) { issues += "Raise your hips — don't let them sag toward the floor"; ded += 20f; joints += listOf(23, 24) }
        }

        "mountain_climber" -> {
            val trunk = angles.trunkAngle ?: 0.0
            if (trunk > 20) { issues += "Keep hips level — don't let them bounce up and down"; ded += 20f; joints += listOf(23, 24) }
        }

        // ── Cardio family ─────────────────────────────────────────────────────
        "burpee" -> {
            val avgKnee = avg(angles.leftKnee, angles.rightKnee, 160.0)
            val trunk   = angles.trunkAngle ?: 0.0
            if (avgKnee < 140) { issues += "Fully extend legs at the top of the jump"; ded += 15f; joints += listOf(25, 26) }
            if (trunk > 30) { issues += "Keep back straight in the plank phase"; ded += 15f; joints += listOf(11, 12, 23, 24) }
        }

        "jumping_jack" -> {
            val avgShoulder = avg(angles.leftShoulder, angles.rightShoulder, 90.0)
            if (avgShoulder != null && avgShoulder < 60) { issues += "Raise arms higher — reach fully overhead"; ded += 15f; joints += listOf(11, 12) }
        }

        "high_knees" -> {
            val avgHip = avg(angles.leftHip, angles.rightHip, 90.0)
            if (avgHip != null && avgHip > 130) { issues += "Drive knees higher — aim for waist height"; ded += 20f; joints += listOf(23, 24, 25, 26) }
        }
    }

    val score = maxOf(0f, 100f - ded)
    val severity = when {
        score >= 80 -> "good"
        score >= 60 -> "warning"
        else        -> "danger"
    }
    return FormFeedback(score, issues, severity, joints)
}

/** Null-safe average of two nullable doubles, with a default if both null. */
private fun avg(a: Double?, b: Double?, default: Double): Double =
    when {
        a != null && b != null -> (a + b) / 2
        a != null -> a
        b != null -> b
        else      -> default
    }

// ─── Feature flags ────────────────────────────────────────────────────────────

data class FeatureFlags(
    val onboardingVariant:   String  = "A",
    val showWarmupPrompt:    Boolean = true,
    val paywallTrigger:      String  = "after_session",
    val freeWorkoutLimit:    Int     = 10,
    val showCaloriesBurned:  Boolean = true,
    val voiceCoachStyle:     String  = "coach",
    val showMealSuggestions: Boolean = false,
)

object FeatureFlagManager { val flags = FeatureFlags() }

// ─── Workout session state ────────────────────────────────────────────────────

data class RepData(val repNumber: Int, val formScore: Float, val timestamp: Long = System.currentTimeMillis())

data class ActiveExercise(
    val exerciseId: String, val exerciseName: String,
    val targetReps: Int,    val targetSets: Int,
    val currentSet: Int = 1, val completedReps: Int = 0,
    val repHistory: List<RepData> = emptyList(),
    val formScores: List<Float> = emptyList(),
    val avgFormScore: Float = 0f,
    val isCompleted: Boolean = false,
    val startTime: Long = System.currentTimeMillis(),
)

enum class WorkoutPhase { IDLE, WARMUP, ACTIVE, REST, COOLDOWN, COMPLETE }

data class WorkoutState(
    val phase: WorkoutPhase = WorkoutPhase.IDLE,
    val sessionStartTime: Long? = null,
    val currentExerciseIndex: Int = 0,
    val exercises: List<ActiveExercise> = emptyList(),
    val totalDuration: Int = 0,
    val isRecording: Boolean = false,
    val voiceEnabled: Boolean = true,
    val lastVoiceMessage: String = "",
    val lastVoiceTime: Long = 0L,
)
