package com.callreport.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * CallReport Application Class.
 * Initializes the foreground notification channel and sets up crash-safe defaults.
 * Strict privacy: zero persistent storage, no network calls, no analytics.
 */
class CallReportApp : Application() {

    companion object {
        const val CHANNEL_ID = "call_report_foreground_channel"
        const val CHANNEL_NAME = "Call Monitor Service"
        
        // In-memory holder for the most recently active phone number.
        // Never written to SharedPreferences, Room, or any file.
        // Cleared immediately after popup presentation.
        @Volatile
        var currentActiveNumber: String? = null
            private set

        fun updateActiveNumber(number: String?) {
            currentActiveNumber = number?.trim()
        }

        fun clearActiveNumber() {
            currentActiveNumber = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_MIN // Low priority silent notification
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Monitors phone calls to display talk-time summary after call completion."
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
