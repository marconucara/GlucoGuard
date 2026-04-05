package com.glucoguard.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.glucoguard.app.Config
import com.glucoguard.app.R
import com.glucoguard.app.presentation.theme.GlucoGuardTheme
import com.glucoguard.app.service.GlucoseMonitorService
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.w(TAG, "AlarmActivity.onCreate() — activity IS being created")

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmType = intent.getIntExtra(EXTRA_ALARM_TYPE, TYPE_GLUCOSE)
        val glucoseValue = intent.getIntExtra(EXTRA_GLUCOSE_VALUE, 0)
        val isLow = intent.getBooleanExtra(EXTRA_IS_LOW, true)
        val minutesSinceLastPoll = intent.getIntExtra(EXTRA_MINUTES_SINCE_POLL, 0)
        
        val initialSnoozeMinutes = loadSnoozeMinutes()

        setContent {
            GlucoGuardTheme {
                AlarmScreen(
                    alarmType = alarmType,
                    glucoseValue = glucoseValue,
                    isLow = isLow,
                    minutesSinceLastPoll = minutesSinceLastPoll,
                    initialSnoozeMinutes = initialSnoozeMinutes,
                    onDismiss = { dismiss(alarmType == TYPE_NO_DATA) },
                    onSnooze = { minutes -> snooze(minutes, alarmType == TYPE_NO_DATA) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VibrationHelper.stop(applicationContext)
    }

    private fun dismiss(isNoData: Boolean) {
        val settingsManager = (application as com.glucoguard.app.GlucoGuardApp).settingsManager
        if (isNoData) {
            settingsManager.noDataAlarmActive = false
            GlucoseMonitorService.noDataAlarmActive.set(false)
        } else {
            settingsManager.alarmActive = false
            GlucoseMonitorService.alarmActive.set(false)
        }
        VibrationHelper.stop(applicationContext)
        finish()
    }

    private fun snooze(minutes: Int, isNoData: Boolean) {
        saveSnoozeMinutes(minutes)
        val settingsManager = (application as com.glucoguard.app.GlucoGuardApp).settingsManager
        val durationMs = minutes * 60_000L
        sendBroadcast(Intent(GlucoseMonitorService.ACTION_SNOOZE).apply {
            `package` = packageName
            putExtra(GlucoseMonitorService.EXTRA_SNOOZE_MS, durationMs)
            putExtra(GlucoseMonitorService.EXTRA_IS_NO_DATA_ALARM, isNoData)
        })
        if (isNoData) {
            settingsManager.noDataAlarmActive = false
            GlucoseMonitorService.noDataAlarmActive.set(false)
        } else {
            settingsManager.alarmActive = false
            GlucoseMonitorService.alarmActive.set(false)
        }
        VibrationHelper.stop(applicationContext)
        finish()
    }

    private fun loadSnoozeMinutes(): Int {
        val default = (Config.SNOOZE_DURATION_MS / 60_000L).toInt()
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SNOOZE_MINUTES, default)
    }

    private fun saveSnoozeMinutes(minutes: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply()
    }

    companion object {
        private const val TAG = "AlarmActivity"
        private const val PREFS_NAME = "glucoguard"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        
        const val TYPE_GLUCOSE = 0
        const val TYPE_NO_DATA = 1
        
        const val EXTRA_ALARM_TYPE = "alarm_type"
        const val EXTRA_GLUCOSE_VALUE = "glucose_value"
        const val EXTRA_IS_LOW = "is_low"
        const val EXTRA_MINUTES_SINCE_POLL = "minutes_since_poll"
    }
}

// Colors
private val ColorBackground = Color(0xFF111318)
private val ColorLow        = Color(0xFFFF453A) // vivid red
private val ColorHigh       = Color(0xFFFFCC00) // amber yellow
private val ColorUnit       = Color(0xFF8E8E93) // muted gray
private val ColorBtnDismiss  = Color(0xFF3A3A3C)
private val ColorBtnSnooze   = Color(0xFF0A84FF) // iOS blue
private val ColorBtnPicker   = Color(0xFF2C2C2E)
private val ColorBtnText     = Color(0xFFEBEBF5) // off-white for contrast on dark buttons

@Composable
fun AlarmScreen(
    alarmType: Int,
    glucoseValue: Int,
    isLow: Boolean,
    minutesSinceLastPoll: Int,
    initialSnoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    var snoozeMinutes by remember { mutableIntStateOf(initialSnoozeMinutes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (alarmType == AlarmActivity.TYPE_GLUCOSE) {
                val valueColor = if (isLow) ColorLow else ColorHigh
                val label = if (isLow) stringResource(R.string.label_low) else stringResource(R.string.label_high)
                
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "$glucoseValue",
                    fontSize = 48.sp,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.unit_mgdl),
                    fontSize = 12.sp,
                    color = ColorUnit
                )
            } else {
                Text(
                    text = stringResource(R.string.alarm_no_data_title),
                    fontSize = 14.sp,
                    color = ColorHigh,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.alarm_no_data_text, minutesSinceLastPoll),
                    fontSize = 11.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBtnDismiss, contentColor = ColorBtnText)
                ) {
                    Text(stringResource(R.string.btn_dismiss), fontSize = 13.sp)
                }

                // Snooze Section (Vertical: +/- controls above Snooze button)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Controls (Row)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { if (snoozeMinutes > 1) snoozeMinutes-- },
                            modifier = Modifier.size(26.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorBtnPicker, contentColor = ColorBtnText)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        
                        Text(
                            text = "${snoozeMinutes}m",
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { snoozeMinutes++ },
                            modifier = Modifier.size(26.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorBtnPicker, contentColor = ColorBtnText)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = { onSnooze(snoozeMinutes) },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorBtnSnooze)
                    ) {
                        Text(stringResource(R.string.btn_snooze), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
