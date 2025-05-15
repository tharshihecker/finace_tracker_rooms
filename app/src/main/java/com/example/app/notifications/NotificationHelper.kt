package com.example.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.app.R

object NotificationHelper {
    private const val CHANNEL_ID_BUDGET = "budget_alerts_channel"
    private const val CHANNEL_NAME_BUDGET = "Budget Alerts"
    private const val NOTIFICATION_ID_BUDGET = 1001

    fun createBudgetNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID_BUDGET)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_BUDGET,
                    CHANNEL_NAME_BUDGET,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                    description = "Notifications when you exceed or approach your budget"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun sendBudgetAlert(context: Context, message: String) {
        createBudgetNotificationChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Optional: Cancel feedback notification if it's still lingering
        manager.cancel(2001) // Assuming feedback uses ID 2001

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Budget Alert")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX) // Ensures heads-up delivery
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // Enables sound, vibration, lights
            .setAutoCancel(true)
            .setGroup("budget_group") // Optional: group if you ever use stacking
            .build()

        manager.notify(NOTIFICATION_ID_BUDGET, notification)
    }
}
