package com.example.glucoguard.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Text
import com.example.glucoguard.R
import com.example.glucoguard.alarm.AlarmActivity
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
import com.example.glucoguard.service.GlucoseMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, Intent(this, GlucoseMonitorService::class.java))
        setContent {
            GlucoGuardTheme {
                GlucoseTestScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (GlucoseMonitorService.alarmActive.get()) {
            startActivity(
                Intent(this, AlarmActivity::class.java).apply {
                    putExtra(AlarmActivity.EXTRA_GLUCOSE_VALUE, GlucoseMonitorService.lastAlarmValue)
                    putExtra(AlarmActivity.EXTRA_IS_LOW, GlucoseMonitorService.lastAlarmIsLow)
                }
            )
        }
    }
}

/** Maps LibreLinkUp TrendArrow (1–5) to a unicode arrow character. */
fun trendToArrow(trend: Int): String = when (trend) {
    1 -> "↓"
    2 -> "↘"
    3 -> "→"
    4 -> "↗"
    5 -> "↑"
    else -> "?"
}

@Composable
fun GlucoseTestScreen() {
    val loading = stringResource(R.string.loading)
    val trendLabel = stringResource(R.string.trend_label)
    var status by remember { mutableStateOf(loading) }

    LaunchedEffect(Unit) {
        status = try {
            val reading = withContext(Dispatchers.IO) { LibreLinkUpClient.fetchGlucose() }
            "${reading.value} mg/dL  ${trendToArrow(reading.trend)}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = status,
                fontSize = 20.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
