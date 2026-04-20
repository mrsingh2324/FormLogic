package com.formlogic.utils

import android.content.Context
import com.formlogic.BuildConfig

/**
 * SentryManager — port of utils/sentry.ts
 * Wraps Sentry Android SDK for crash reporting.
 *
 * To enable:
 *   1. Add to app/build.gradle.kts:
 *      implementation("io.sentry:sentry-android:7.14.0")
 *   2. Add to AndroidManifest.xml <application>:
 *      <meta-data android:name="io.sentry.dsn" android:value="https://xxx@sentry.io/yyy" />
 *   3. Uncomment Sentry calls below.
 */
object SentryManager {

    fun init(context: Context) {
        if (BuildConfig.DEBUG) return
        // TODO:
        // SentryAndroid.init(context) { options ->
        //     options.dsn = BuildConfig.SENTRY_DSN
        //     options.environment = if (BuildConfig.DEBUG) "development" else "production"
        //     options.tracesSampleRate = 0.2
        //     options.isEnableUserInteractionTracing = true
        // }
    }

    fun captureException(throwable: Throwable, extra: Map<String, Any> = emptyMap()) {
        android.util.Log.e("Sentry", "Exception: ${throwable.message}", throwable)
        // TODO: Sentry.captureException(throwable) { scope ->
        //     extra.forEach { (k, v) -> scope.setExtra(k, v.toString()) }
        // }
    }

    fun captureMessage(message: String, level: String = "info") {
        android.util.Log.w("Sentry", "[$level] $message")
        // TODO: Sentry.captureMessage(message)
    }

    fun setUser(userId: String, email: String) {
        // TODO: Sentry.setUser(User().apply { this.id = userId; this.email = email })
    }

    fun clearUser() {
        // TODO: Sentry.setUser(null)
    }

    fun addBreadcrumb(message: String, category: String = "app") {
        // TODO: Sentry.addBreadcrumb(Breadcrumb(message).apply { this.category = category })
    }
}
