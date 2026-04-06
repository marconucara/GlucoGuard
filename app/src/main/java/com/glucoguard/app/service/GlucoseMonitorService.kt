package com.glucoguard.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
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
    private lateinit var alarmManager: AlarmManager

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
            Log.d(TAG, "Snoozed (${if(isNoData) "NoData" else "Glucose"}) for ${durationMs / 60_000}m. Optimization: pausing polling until snooze ends.")
            
            // Optimization: stop current polling and schedule next poll right after snooze expires
            handler.removeCallbacks(pollRunnable)
            scheduleNextPoll(durationMs + 10_000L) // Add 10s buffer to ensure snooze is definitely over
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            scheduleNextPoll() // Schedule next before starting current
            
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
                        updateOngoingActivity(reading)
                        
                        // Adaptive polling: calculate next interval based on reading
                        val nextIntervalMs = calculateAdaptiveInterval(reading)
                        scheduleNextPoll(nextIntervalMs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Poll failed: ${e.javaClass.simpleName}: ${e.message}")
                        checkNoDataAlarm()
                        scheduleNextPoll(Config.POLL_INTERVAL_MS) // Default 1 min on error
                    }
                }.start()
            }
        }
    }

    private fun calculateAdaptiveInterval(reading: GlucoseReading): Long {
        val dnd = DndHelper.isDndActive(this)
        val low = if (dnd) settingsManager.dndLow else settingsManager.normalLow
        val high = if (dnd) settingsManager.dndHigh else settingsManager.normalHigh
        
        val value = reading.value
        val trend = reading.trend
        
        // If out of range, always 1 minute
        if (value < low || value > high) return Config.POLL_INTERVAL_MS
        
        val distLow = value - low
        val distHigh = high - value
        
        return when (trend) {
            3 -> when { // Constant
                distLow >= 30 && distHigh >= 30 -> 10 * 60_000L
                distLow >= 20 && distHigh >= 20 -> 5 * 60_000L
                distLow >= 10 && distHigh >= 10 -> 2 * 60_000L
                else -> Config.POLL_INTERVAL_MS
            }
            2 -> when { // Slight decrease
                distLow >= 60 && distHigh >= 30 -> 10 * 60_000L
                distLow >= 40 && distHigh >= 20 -> 5 * 60_000L
                distLow >= 20 && distHigh >= 10 -> 2 * 60_000L
                else -> Config.POLL_INTERVAL_MS
            }
            4 -> when { // Slight increase
                distLow >= 30 && distHigh >= 60 -> 10 * 60_000L
                distLow >= 20 && distHigh >= 40 -> 5 * 60_000L
                distLow >= 10 && distHigh >= 20 -> 2 * 60_000L
                else -> Config.POLL_INTERVAL_MS
            }
            else -> Config.POLL_INTERVAL_MS // Rapid change (1 or 5)
        }
    }

    private fun scheduleNextPoll(intervalMs: Long = Config.POLL_INTERVAL_MS) {
        val intent = Intent(this, GlucoseMonitorService::class.java).apply {
            action = ACTION_REFRESH_POLLING
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + intervalMs
        Log.d(TAG, "Scheduling next poll in ${intervalMs / 60_000}m ${ (intervalMs % 60_000) / 1000}s")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm", e)
            handler.postDelayed(pollRunnable, Config.POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlucoGuard:PollWakeLock")
        try {
            wakeLock.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wakelock in onCreate: ${e.message}")
        }
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
                Log.d(TAG, "Refresh polling requested via AlarmManager")
                handler.removeCallbacks(pollRunnable)
                handler.post(pollRunnable)
            }
            else -> {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to startForeground: ${e.message}")
                }
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

    private fun updateOngoingActivity(reading: GlucoseReading) {
        val status = Status.Builder()
            .addTemplate("${reading.value}${reading.trendToArrow()}")
            .build()

        val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, buildNotificationBuilder())
            .setStaticIcon(R.drawable.ic_logo)
            .setTouchIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setStatus(status)
            .build()

        ongoingActivity.apply(applicationContext)
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
        return buildNotificationBuilder().build()
    }

    private fun buildNotificationBuilder(): NotificationCompat.Builder {
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
