package com.formlogic

import com.formlogic.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * FormLogicUnitTests.kt
 * Kotlin port of:
 *   - frontend/src/tests/poseDetection.test.ts
 *   - frontend/src/tests/tdee.test.ts
 */
class FormLogicUnitTests {

    // ── TDEE Tests (port of tdee.test.ts) ─────────────────────────────────────

    @Test
    fun tdee_maleMusclGain_caloriesAboveMaintenance() {
        val metrics = UserMetrics(
            weightKg = 80.0, heightCm = 175.0, ageYears = 25,
            gender = "male", fitnessLevel = "intermediate",
            goals = listOf("muscle_gain"), daysPerWeek = 4,
        )
        val targets = calculateNutritionTargets(metrics)
        assertTrue("Calories should be positive", targets.calories > 0)
        assertTrue("Protein should be >100g for muscle gain", targets.protein > 100)
        assertTrue("Calories should be in safe range", targets.calories in 1200..4000)
    }

    @Test
    fun tdee_femaleWeightLoss_caloriesInDeficit() {
        val metrics = UserMetrics(
            weightKg = 65.0, heightCm = 162.0, ageYears = 30,
            gender = "female", fitnessLevel = "beginner",
            goals = listOf("weight_loss"), daysPerWeek = 2,
        )
        val targets = calculateNutritionTargets(metrics)
        assertTrue("Weight loss calories should be positive", targets.calories > 0)
        assertTrue("Protein should be >80g", targets.protein > 80)
        assertTrue("Water target should be positive", targets.waterMl > 0)
    }

    @Test
    fun tdee_defaults_safeRange() {
        val targets = calculateNutritionTargets(UserMetrics())
        assertTrue(targets.calories in 1200..4000)
        assertTrue(targets.protein > 0)
        assertTrue(targets.carbs > 0)
        assertTrue(targets.fats > 0)
    }

    @Test
    fun bmi_normalWeight_healthyCategory() {
        val result = calculateBmi(70.0, 175.0)
        assertEquals("Healthy", result.category)
        assertTrue(result.bmi in 22.0..23.0)
    }

    @Test
    fun bmi_underweight() {
        val result = calculateBmi(45.0, 175.0)
        assertEquals("Underweight", result.category)
    }

    @Test
    fun bmi_obese() {
        val result = calculateBmi(110.0, 170.0)
        assertEquals("Obese", result.category)
    }

    // ── Pose Detection Tests (port of poseDetection.test.ts) ──────────────────

    @Test
    fun calculateAngle_straight_line_180degrees() {
        // Three collinear points should give ~180°
        val a = Keypoint(0f, 0f)
        val b = Keypoint(1f, 0f)
        val c = Keypoint(2f, 0f)
        val angle = calculateAngle(a, b, c)
        assertTrue("Straight line should be ~180°", angle > 170.0)
    }

    @Test
    fun calculateAngle_rightAngle_90degrees() {
        val a = Keypoint(0f, 1f)
        val b = Keypoint(0f, 0f)
        val c = Keypoint(1f, 0f)
        val angle = calculateAngle(a, b, c)
        assertTrue("Right angle should be ~90°, was $angle", angle in 85.0..95.0)
    }

    @Test
    fun repDetector_squat_countsRep() {
        val detector = RepDetector("squat")
        val startAngle  = 165.0  // standing
        val bottomAngle = 85.0   // at depth
        var repCounted = false

        // Simulate descent to depth
        for (angle in 165 downTo 85 step 5) {
            if (detector.update(angle.toDouble())) repCounted = true
        }
        // Simulate ascent back to standing
        for (angle in 85..165 step 5) {
            if (detector.update(angle.toDouble())) repCounted = true
        }

        assertTrue("Should have counted one rep", repCounted)
        assertEquals(1, detector.repCount)
    }

    @Test
    fun repDetector_partialRep_doesNotCount() {
        val detector = RepDetector("squat")
        // Go down only halfway (not past bottom threshold)
        for (angle in 165 downTo 120 step 5) {
            detector.update(angle.toDouble())
        }
        // Come back up
        for (angle in 120..165 step 5) {
            detector.update(angle.toDouble())
        }
        assertEquals("Partial rep should not be counted", 0, detector.repCount)
    }

    @Test
    fun repDetector_reset_clearsCount() {
        val detector = RepDetector("squat")
        // Count a rep
        for (angle in 165 downTo 85 step 5) detector.update(angle.toDouble())
        for (angle in 85..165 step 5) detector.update(angle.toDouble())

        detector.reset()
        assertEquals("After reset, count should be 0", 0, detector.repCount)
    }

    @Test
    fun getFormFeedback_goodSquat_highScore() {
        // Perfect squat: knee at 85° (below parallel)
        val angles = ExerciseAngles(leftKnee = 85.0, rightKnee = 85.0)
        val feedback = getFormFeedback("squat", angles)
        assertTrue("Perfect squat should score >= 80", feedback.score >= 80f)
        assertEquals("No issues for perfect form", 0, feedback.issues.size)
    }

    @Test
    fun getFormFeedback_shallowSquat_hasIssue() {
        // Shallow squat: knee at 130° (not at parallel)
        val angles = ExerciseAngles(leftKnee = 130.0, rightKnee = 130.0)
        val feedback = getFormFeedback("squat", angles)
        assertTrue("Shallow squat should have issues", feedback.issues.isNotEmpty())
        assertTrue("Shallow squat should have lower score", feedback.score < 90f)
    }

    @Test
    fun scoreColor_excellent_isTeal() {
        val color = scoreColor(95f)
        assertEquals("Excellent color should be teal 0xFF43D9AD", -12199507, color.hashCode().let { color.value.toInt() })
        // Check it's the correct color
        assertTrue(color.red < 0.5f)  // teal has low red
        assertTrue(color.green > 0.5f) // teal has high green
    }

    @Test
    fun scoreLabel_allRanges() {
        assertEquals("Excellent", scoreLabel(95f))
        assertEquals("Good",      scoreLabel(80f))
        assertEquals("Fair",      scoreLabel(65f))
        assertEquals("Fix Form",  scoreLabel(40f))
    }

    @Test
    fun getPrimaryAngle_squatUsesKnee() {
        val angles = ExerciseAngles(leftKnee = 90.0, rightKnee = 90.0, leftElbow = 160.0)
        val angle = getPrimaryAngle("squat", angles)
        assertEquals(90.0, angle, 0.1)
    }

    @Test
    fun getPrimaryAngle_pushupUsesElbow() {
        val angles = ExerciseAngles(leftElbow = 90.0, rightElbow = 90.0, leftKnee = 160.0)
        val angle = getPrimaryAngle("pushup", angles)
        assertEquals(90.0, angle, 0.1)
    }
}
