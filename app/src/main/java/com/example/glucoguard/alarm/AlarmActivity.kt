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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.example.glucoguard.Config
import com.example.glucoguard.R
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
    glucoseValue: Int,
    isLow: Boolean,
    initialSnoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val valueColor = if (isLow) ColorLow else ColorHigh
    val label = if (isLow) stringResource(R.string.label_low) else stringResource(R.string.label_high)
    var snoozeMinutes by remember { mutableIntStateOf(initialSnoozeMinutes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Snooze duration picker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { if (snoozeMinutes > 1) snoozeMinutes-- },
                    modifier = Modifier.size(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBtnPicker, contentColor = ColorBtnText)
                ) {
                    Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${snoozeMinutes}m",
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.widthIn(min = 38.dp),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { if (snoozeMinutes < 120) snoozeMinutes++ },
                    modifier = Modifier.size(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBtnPicker, contentColor = ColorBtnText)
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBtnDismiss, contentColor = ColorBtnText)
                ) {
                    Text(stringResource(R.string.btn_dismiss), fontSize = 13.sp)
                }
                Button(
                    onClick = { onSnooze(snoozeMinutes) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBtnSnooze)
                ) {
                    Text(stringResource(R.string.btn_snooze), fontSize = 13.sp)
                }
            }
        }
    }
}
