package com.example.glucoguard.alarm

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
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
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
import com.example.glucoguard.service.GlucoseMonitorService

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.w(TAG, "AlarmActivity.onCreate() — activity IS being created")

        // Keep screen on and show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val glucoseValue = intent.getIntExtra(EXTRA_GLUCOSE_VALUE, 0)
        val isLow = intent.getBooleanExtra(EXTRA_IS_LOW, true)

        setContent {
            GlucoGuardTheme {
                AlarmScreen(
                    glucoseValue = glucoseValue,
                    isLow = isLow,
                    onDismiss = { dismiss() },
                    onSnooze = { snooze() }
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

    private fun snooze() {
        sendBroadcast(Intent(GlucoseMonitorService.ACTION_SNOOZE).apply {
            `package` = packageName
        })
        // alarmActive is also cleared in the service's snoozeReceiver
        GlucoseMonitorService.alarmActive.set(false)
        VibrationHelper.stop(applicationContext)
        finish()
    }

    companion object {
        private const val TAG = "AlarmActivity"
        const val EXTRA_GLUCOSE_VALUE = "glucose_value"
        const val EXTRA_IS_LOW = "is_low"
    }
}

@Composable
fun AlarmScreen(
    glucoseValue: Int,
    isLow: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val valueColor = if (isLow) Color(0xFFFF3B30) else Color(0xFFFF9500) // red for low, orange for high
    val label = if (isLow) "LOW" else "HIGH"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$glucoseValue",
                fontSize = 52.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "mg/dL",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Dismiss", fontSize = 13.sp)
                }
                Button(
                    onClick = onSnooze,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C3A5E))
                ) {
                    Text("Snooze 30m", fontSize = 13.sp)
                }
            }
        }
    }
}
