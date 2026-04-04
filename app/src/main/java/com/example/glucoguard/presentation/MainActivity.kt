package com.example.glucoguard.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.glucoguard.R
import com.example.glucoguard.alarm.AlarmActivity
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
import com.example.glucoguard.service.GlucoseMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "GlucoGuard Logo",
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = status,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}
