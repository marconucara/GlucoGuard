package com.example.glucoguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.glucoguard.Config
import com.example.glucoguard.GlucoGuardApp
import com.example.glucoguard.alarm.VibrationHelper
import com.example.glucoguard.api.GlucoseReading
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.MainActivity
import com.example.glucoguard.util.DndHelper
import java.util.concurrent.atomic.AtomicBoolean

class GlucoseMonitorService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private val handler = Handler(Looper.getMainLooper())

    // Snooze state: timestamp until which alarms are suppressed (0 = not snoozed)
    var snoozeUntil: Long = 0

    private val snoozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val durationMs = intent.getLongExtra(EXTRA_SNOOZE_MS, Config.SNOOZE_DURATION_MS)
            snoozeUntil = System.currentTimeMillis() + durationMs
            alarmActive.set(false)
            Log.d(TAG, "Snoozed for ${durationMs / 60_000}m until $snoozeUntil")
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val reading = LibreLinkUpClient.fetchGlucose()
                    Log.d(TAG, "Glucose: ${reading.value} mg/dL, trend: ${reading.trend}")
                    handleReading(reading)
                } catch (e: Exception) {
                    Log.e(TAG, "Poll failed: ${e.javaClass.simpleName}: ${e.message}")
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
        ContextCompat.registerReceiver(
            this, snoozeReceiver,
            IntentFilter(ACTION_SNOOZE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        unregisterReceiver(snoozeReceiver)
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleReading(reading: GlucoseReading) {
        val dnd = DndHelper.isDndActive(this)
        val low = if (dnd) Config.DND_LOW else Config.NORMAL_LOW
        val high = if (dnd) Config.DND_HIGH else Config.NORMAL_HIGH
        val inRange = reading.value in low..high

        if (inRange) {
            if (alarmActive.getAndSet(false)) {
                Log.d(TAG, "Glucose back in range — alarm cleared")
                VibrationHelper.stop(applicationContext)
            }
            return
        }

        // Out of range: check snooze
        val now = System.currentTimeMillis()
        if (now < snoozeUntil) {
            Log.d(TAG, "Out of range (${reading.value}) but snoozed — skipping alarm")
            return
        }

        val isLow = reading.value < low
        Log.w(TAG, "ALARM: glucose ${reading.value} mg/dL out of range [$low-$high], DND=$dnd, isLow=$isLow")
        triggerAlarm(reading, isLow)
    }

    private fun triggerAlarm(reading: GlucoseReading, isLow: Boolean) {
        alarmActive.set(true)
        lastAlarmValue = reading.value
        lastAlarmIsLow = isLow
        Log.w(TAG, "triggerAlarm() — vibrating, waiting for user to open app")
        VibrationHelper.start(applicationContext)
    }

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
        const val ACTION_SNOOZE = "com.example.glucoguard.ACTION_SNOOZE"

        // Shared alarm state — read by MainActivity.onResume() to redirect to AlarmActivity.
        const val EXTRA_SNOOZE_MS = "snooze_ms"
        val alarmActive = AtomicBoolean(false)
        @Volatile var lastAlarmValue: Int = 0
        @Volatile var lastAlarmIsLow: Boolean = false
    }
}
