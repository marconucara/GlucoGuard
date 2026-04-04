package com.example.glucoguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.glucoguard.R

class GlucoGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_monitoring_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_monitoring_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, getString(R.string.channel_alarm_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_alarm_desc)
                setSound(null, null)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "glucoguard_monitoring"
        const val ALARM_CHANNEL_ID = "glucoguard_alarm"
        const val ALARM_NOTIFICATION_ID = 2
    }
}
