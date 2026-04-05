package com.glucoguard.app.service

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
import com.glucoguard.app.Config
import com.glucoguard.app.GlucoGuardApp
import com.glucoguard.app.R
import com.glucoguard.app.alarm.VibrationHelper
import com.glucoguard.app.api.GlucoseReading
import com.glucoguard.app.api.LibreLinkUpClient
import com.glucoguard.app.presentation.MainActivity
import com.glucoguard.app.util.DndHelper
import java.util.concurrent.atomic.AtomicBoolean

class GlucoseMonitorService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private val handler = Handler(Looper.getMainLooper())

    private val settingsManager by lazy { (application as GlucoGuardApp).settingsManager }

    private val snoozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val durationMs = intent.getLongExtra(EXTRA_SNOOZE_MS, Config.SNOOZE_DURATION_MS)
            val isNoData = intent.getBooleanExtra(EXTRA_IS_NO_DATA_ALARM, false)
            
            if (isNoData) {
                settingsManager.noDataSnoozeUntil = System.currentTimeMillis() + durationMs
                settingsManager.noDataAlarmActive = false
                noDataAlarmActive.set(false)
            } else {
                settingsManager.glucoseSnoozeUntil = System.currentTimeMillis() + durationMs
                settingsManager.alarmActive = false
                alarmActive.set(false)
            }
            
            VibrationHelper.stop(applicationContext)
            Log.d(TAG, "Snoozed (${if(isNoData) "NoData" else "Glucose"}) for ${durationMs / 60_000}m")
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val email = settingsManager.email
            val password = settingsManager.password

            if (email.isBlank() || password.isBlank()) {
                Log.w(TAG, "Polling skipped: Credentials not set")
            } else {
                Thread {
                    try {
                        val reading = LibreLinkUpClient.fetchGlucose(email, password)
                        settingsManager.lastSuccessfulPollTimestamp = System.currentTimeMillis()
                        Log.d(TAG, "Glucose: ${reading.value} mg/dL, trend: ${reading.trendToArrow()}")
                        handleReading(reading)
                    } catch (e: Exception) {
                        Log.e(TAG, "Poll failed: ${e.javaClass.simpleName}: ${e.message}")
                        checkNoDataAlarm()
                    }
                }.start()
            }
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

        // Resume state from persistent storage
        alarmActive.set(settingsManager.alarmActive)
        noDataAlarmActive.set(settingsManager.noDataAlarmActive)
        lastAlarmValue = settingsManager.lastAlarmValue
        lastAlarmIsLow = settingsManager.lastAlarmIsLow
        
        if (alarmActive.get() || noDataAlarmActive.get()) {
            VibrationHelper.start(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_ALARM -> {
                triggerAlarm(GlucoseReading(55, 1), true) // Test alarm: Low glucose
            }
            ACTION_REFRESH_POLLING -> {
                Log.d(TAG, "Refresh polling requested")
                handler.removeCallbacks(pollRunnable)
                handler.post(pollRunnable)
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                handler.removeCallbacks(pollRunnable)
                handler.post(pollRunnable)
            }
        }
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
        val low = if (dnd) settingsManager.dndLow else settingsManager.normalLow
        val high = if (dnd) settingsManager.dndHigh else settingsManager.normalHigh
        val inRange = reading.value in low..high

        if (inRange) {
            if (alarmActive.getAndSet(false)) {
                settingsManager.alarmActive = false
                Log.d(TAG, "Glucose back in range — alarm cleared")
                VibrationHelper.stop(applicationContext)
            }
            // Clear no-data alarm if we got a successful reading
            if (noDataAlarmActive.getAndSet(false)) {
                settingsManager.noDataAlarmActive = false
                VibrationHelper.stop(applicationContext)
            }
            return
        }

        // Out of range: check snooze
        val now = System.currentTimeMillis()
        if (now < settingsManager.glucoseSnoozeUntil) {
            Log.d(TAG, "Out of range (${reading.value}) but snoozed — skipping alarm")
            return
        }

        val isLow = reading.value < low
        Log.w(TAG, "ALARM: glucose ${reading.value} mg/dL out of range [$low-$high], DND=$dnd, isLow=$isLow")
        triggerAlarm(reading, isLow)
    }

    private fun checkNoDataAlarm() {
        val lastPoll = settingsManager.lastSuccessfulPollTimestamp
        if (lastPoll == 0L) return // Haven't had a single success yet

        val minutesSinceLastPoll = (System.currentTimeMillis() - lastPoll) / 60_000
        val threshold = settingsManager.noDataThresholdMin

        if (minutesSinceLastPoll >= threshold) {
            val now = System.currentTimeMillis()
            if (now < settingsManager.noDataSnoozeUntil) {
                Log.d(TAG, "No data ($minutesSinceLastPoll min) but snoozed — skipping alarm")
                return
            }
            
            Log.w(TAG, "ALARM: No data for $minutesSinceLastPoll minutes (threshold: $threshold)")
            triggerNoDataAlarm()
        }
    }

    private fun triggerAlarm(reading: GlucoseReading, isLow: Boolean) {
        alarmActive.set(true)
        lastAlarmValue = reading.value
        lastAlarmIsLow = isLow
        
        settingsManager.alarmActive = true
        settingsManager.lastAlarmValue = reading.value
        settingsManager.lastAlarmIsLow = isLow
        
        Log.w(TAG, "triggerAlarm() — vibrating")
        VibrationHelper.start(applicationContext)
    }

    private fun triggerNoDataAlarm() {
        noDataAlarmActive.set(true)
        settingsManager.noDataAlarmActive = true
        
        Log.w(TAG, "triggerNoDataAlarm() — vibrating")
        VibrationHelper.start(applicationContext)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, GlucoGuardApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_monitoring_text))
            .setSmallIcon(R.drawable.ic_logo)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GlucoseMonitorService"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SNOOZE = "com.glucoguard.app.ACTION_SNOOZE"
        const val ACTION_TEST_ALARM = "com.glucoguard.app.ACTION_TEST_ALARM"
        const val ACTION_REFRESH_POLLING = "com.glucoguard.app.ACTION_REFRESH_POLLING"

        const val EXTRA_SNOOZE_MS = "snooze_ms"
        const val EXTRA_IS_NO_DATA_ALARM = "is_no_data"
        
        val alarmActive = AtomicBoolean(false)
        val noDataAlarmActive = AtomicBoolean(false)

        @Volatile var lastAlarmValue: Int = 0
        @Volatile var lastAlarmIsLow: Boolean = false
    }
}
