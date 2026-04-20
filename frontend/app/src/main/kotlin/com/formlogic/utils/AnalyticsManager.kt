package com.formlogic.utils

import android.content.Context
import android.os.Bundle
import com.formlogic.BuildConfig

/**
 * AnalyticsManager — port of hooks/useAnalytics.ts
 * Wraps Firebase Analytics (or any provider) behind a clean interface.
 * In production, add the Firebase Android SDK and uncomment the real calls.
 */
object AnalyticsManager {

    /**
     * Initialise analytics at app startup.
     * Wire Firebase: FirebaseAnalytics.getInstance(context)
     */
    fun init(context: Context) {
        if (BuildConfig.DEBUG) return // skip analytics in debug
        // TODO: FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
    }

    /**
     * Track a screen view.
     * Original: analytics.track('screen_view', { screen_name: name })
     */
    fun trackScreen(screenName: String) {
        if (BuildConfig.DEBUG) { android.util.Log.d("Analytics", "Screen: $screenName"); return }
        // TODO: firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
        //     putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        // })
    }

    /**
     * Track a custom event.
     * Original: analytics.track(eventName, props)
     */
    fun trackEvent(eventName: String, props: Map<String, Any> = emptyMap()) {
        if (BuildConfig.DEBUG) { android.util.Log.d("Analytics", "Event: $eventName $props"); return }
        // TODO: val bundle = Bundle(); props.forEach { (k, v) -> bundle.putString(k, v.toString()) }
        //       firebaseAnalytics.logEvent(eventName, bundle)
    }

    /** Identify user after login. */
    fun identifyUser(userId: String) {
        if (BuildConfig.DEBUG) return
        // TODO: firebaseAnalytics.setUserId(userId)
    }

    /** Clear user identity on logout. */
    fun clearUser() {
        if (BuildConfig.DEBUG) return
        // TODO: firebaseAnalytics.setUserId(null)
    }

    // ─── Pre-defined events (mirrors original hook) ───────────────────────────

    fun trackLogin(method: String = "email")                     = trackEvent("login", mapOf("method" to method))
    fun trackRegister(method: String = "email")                  = trackEvent("sign_up", mapOf("method" to method))
    fun trackWorkoutStart(exerciseId: String)                    = trackEvent("workout_start", mapOf("exercise_id" to exerciseId))
    fun trackWorkoutComplete(reps: Int, score: Float, secs: Int) = trackEvent("workout_complete", mapOf("total_reps" to reps, "avg_form_score" to score, "duration_seconds" to secs))
    fun trackRepCompleted(formScore: Float)                      = trackEvent("rep_completed", mapOf("form_score" to formScore))
    fun trackAchievementUnlocked(achievementId: String)          = trackEvent("achievement_unlocked", mapOf("achievement_id" to achievementId))
    fun trackMealLogged(mealType: String, calories: Int)         = trackEvent("meal_logged", mapOf("meal_type" to mealType, "calories" to calories))
    fun trackPlanGenerated(goal: String, weeks: Int)             = trackEvent("plan_generated", mapOf("goal" to goal, "duration_weeks" to weeks))
    fun trackPaywallShown(trigger: String)                       = trackEvent("paywall_shown", mapOf("trigger" to trigger))
    fun trackSubscriptionStarted(productId: String)              = trackEvent("purchase", mapOf("product_id" to productId))
}
