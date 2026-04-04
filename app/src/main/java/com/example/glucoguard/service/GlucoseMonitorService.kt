package com.example.glucoguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.glucoguard.Config
import com.example.glucoguard.GlucoGuardApp
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.MainActivity

class GlucoseMonitorService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private val handler = Handler(Looper.getMainLooper())

    private val pollRunnable = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val reading = LibreLinkUpClient.fetchGlucose()
                    Log.d(TAG, "Glucose: ${reading.value} mg/dL, trend: ${reading.trend}")
                } catch (e: Exception) {
                    Log.e(TAG, "Poll failed: ${e.message}")
                }
            }.start()
            handler.postDelayed(this, Config.POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoGuard:PollWakeLock")
        wakeLock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        handler.post(pollRunnable) // first poll immediately, then every POLL_INTERVAL_MS
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, GlucoGuardApp.CHANNEL_ID)
            .setContentTitle("GlucoGuard")
            .setContentText("Monitoring glucose...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GlucoseMonitorService"
        private const val NOTIFICATION_ID = 1
    }
}
