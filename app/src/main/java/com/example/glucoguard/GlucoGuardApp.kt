package com.example.glucoguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GlucoGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GlucoGuard Monitoring",
            NotificationManager.IMPORTANCE_LOW // silent, no sound
        ).apply {
            description = "Persistent notification while GlucoGuard is monitoring glucose"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "glucoguard_monitoring"
    }
}
