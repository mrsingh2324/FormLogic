package com.formlogic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.formlogic.utils.AnalyticsManager
import com.formlogic.utils.SentryManager

class FormLogicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SentryManager.init(this)
        AnalyticsManager.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel("default",      "General",          NotificationManager.IMPORTANCE_DEFAULT).apply { description = "General FormLogic notifications" },
            NotificationChannel("reminders",    "Workout Reminders",NotificationManager.IMPORTANCE_HIGH  ).apply { description = "Daily workout reminders"; enableVibration(true); setShowBadge(true) },
            NotificationChannel("achievements", "Achievements",     NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Achievement unlocked"; setShowBadge(true) },
        ).forEach { nm.createNotificationChannel(it) }
    }
}
