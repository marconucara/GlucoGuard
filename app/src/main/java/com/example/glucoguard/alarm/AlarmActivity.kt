package com.example.glucoguard.alarm

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.example.glucoguard.Config
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
import com.example.glucoguard.service.GlucoseMonitorService

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.w(TAG, "AlarmActivity.onCreate() — activity IS being created")

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val glucoseValue = intent.getIntExtra(EXTRA_GLUCOSE_VALUE, 0)
        val isLow = intent.getBooleanExtra(EXTRA_IS_LOW, true)
        val initialSnoozeMinutes = loadSnoozeMinutes()

        setContent {
            GlucoGuardTheme {
                AlarmScreen(
                    glucoseValue = glucoseValue,
                    isLow = isLow,
                    initialSnoozeMinutes = initialSnoozeMinutes,
                    onDismiss = { dismiss() },
                    onSnooze = { minutes -> snooze(minutes) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VibrationHelper.stop(applicationContext)
    }

    private fun dismiss() {
        GlucoseMonitorService.alarmActive.set(false)
        VibrationHelper.stop(applicationContext)
        finish()
    }

    private fun snooze(minutes: Int) {
        saveSnoozeMinutes(minutes)
        val durationMs = minutes * 60_000L
        sendBroadcast(Intent(GlucoseMonitorService.ACTION_SNOOZE).apply {
            `package` = packageName
            putExtra(GlucoseMonitorService.EXTRA_SNOOZE_MS, durationMs)
        })
        GlucoseMonitorService.alarmActive.set(false)
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
        const val EXTRA_GLUCOSE_VALUE = "glucose_value"
        const val EXTRA_IS_LOW = "is_low"
    }
}

@Composable
fun AlarmScreen(
    glucoseValue: Int,
    isLow: Boolean,
    initialSnoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val valueColor = if (isLow) Color(0xFFFF3B30) else Color(0xFFFF9500)
    val label = if (isLow) "LOW" else "HIGH"
    var snoozeMinutes by remember { mutableIntStateOf(initialSnoozeMinutes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$glucoseValue",
                fontSize = 44.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "mg/dL",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            // Snooze duration picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { if (snoozeMinutes > 1) snoozeMinutes-- },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${snoozeMinutes}m",
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { if (snoozeMinutes < 120) snoozeMinutes++ },
                    modifier = Modifier.size(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Dismiss", fontSize = 13.sp)
                }
                Button(
                    onClick = { onSnooze(snoozeMinutes) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A5E))
                ) {
                    Text("Snooze", fontSize = 13.sp)
                }
            }
        }
    }
}
