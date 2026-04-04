package com.example.glucoguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GlucoGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GlucoGuard Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while GlucoGuard is monitoring glucose"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, "GlucoGuard Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen alarm when glucose is out of range"
                setSound(null, null) // vibration handled manually via VibrationHelper
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "glucoguard_monitoring"
        const val ALARM_CHANNEL_ID = "glucoguard_alarm"
        const val ALARM_NOTIFICATION_ID = 2
    }
}
