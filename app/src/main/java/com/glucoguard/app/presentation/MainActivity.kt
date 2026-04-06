package com.glucoguard.app.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.glucoguard.app.GlucoGuardApp
import com.glucoguard.app.R
import com.glucoguard.app.alarm.AlarmActivity
import com.glucoguard.app.api.GlucoseReading
import com.glucoguard.app.api.LibreLinkUpClient
import com.glucoguard.app.presentation.theme.GlucoGuardTheme
import com.glucoguard.app.service.GlucoseMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

        val settingsManager = (applicationContext as GlucoGuardApp).settingsManager

        setContent {
            var isDisclaimerAccepted by remember { mutableStateOf(settingsManager.disclaimerAccepted) }
            
            GlucoGuardTheme {
                when {
                    !isDisclaimerAccepted -> {
                        DisclaimerScreen(onAccept = {
                            settingsManager.disclaimerAccepted = true
                            isDisclaimerAccepted = true
                        })
                    }
                    else -> {
                        // Start service only after all permissions/checks are done
                        LaunchedEffect(Unit) {
                            try {
                                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, GlucoseMonitorService::class.java))
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to start service: ${e.message}")
                            }
                        }

                        MainGlucoseScreen(
                            onSettingsClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (GlucoseMonitorService.alarmActive.get() || GlucoseMonitorService.noDataAlarmActive.get()) {
            val isNoData = GlucoseMonitorService.noDataAlarmActive.get()
            val intent = Intent(this, AlarmActivity::class.java).apply {
                if (isNoData) {
                    putExtra(AlarmActivity.EXTRA_ALARM_TYPE, AlarmActivity.TYPE_NO_DATA)
                    val lastPoll = (applicationContext as GlucoGuardApp).settingsManager.lastSuccessfulPollTimestamp
                    val mins = if (lastPoll == 0L) 0 else ((System.currentTimeMillis() - lastPoll) / 60_000).toInt()
                    putExtra(AlarmActivity.EXTRA_MINUTES_SINCE_POLL, mins)
                } else {
                    putExtra(AlarmActivity.EXTRA_ALARM_TYPE, AlarmActivity.TYPE_GLUCOSE)
                    putExtra(AlarmActivity.EXTRA_GLUCOSE_VALUE, GlucoseMonitorService.lastAlarmValue)
                    putExtra(AlarmActivity.EXTRA_IS_LOW, GlucoseMonitorService.lastAlarmIsLow)
                }
            }
            startActivity(intent)
        }
    }
}

@Composable
fun DisclaimerScreen(onAccept: () -> Unit) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.disclaimer_text),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.disclaimer_accept))
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationScreen(onConfigure: () -> Unit, onSkip: () -> Unit, onRefresh: () -> Unit) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.battery_opt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.battery_opt_text),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Button(
                    onClick = onConfigure,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_opt_button))
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                        Text("Check", fontSize = 11.sp)
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text("Skip", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MainGlucoseScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = (context.applicationContext as GlucoGuardApp).settingsManager
    
    var glucoseValue by remember { mutableStateOf<Int?>(null) }
    var reading by remember { mutableStateOf<GlucoseReading?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Trigger refresh when activity resumes
    DisposableEffect(context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        (context as ComponentActivity).lifecycle.addObserver(observer)
        onDispose {
            (context as ComponentActivity).lifecycle.removeObserver(observer)
        }
    }

    // Initial fetch and auto-refresh
    LaunchedEffect(refreshTrigger, settingsManager.email, settingsManager.password) {
        while(true) {
            val email = settingsManager.email
            val pass = settingsManager.password
            
            if (email.isNotBlank() && pass.isNotBlank()) {
                isRefreshing = true
                try {
                    val currentReading = withContext(Dispatchers.IO) { 
                        LibreLinkUpClient.fetchGlucose(email, pass) 
                    }
                    reading = currentReading
                    glucoseValue = currentReading.value
                    errorMsg = null
                } catch (e: Exception) {
                    Log.e("MainActivity", "Fetch error: ${e.message}")
                    // Se avevamo già un valore, non mostrare l'errore distruttivo ma solo i dati "stale"
                    if (glucoseValue == null) {
                        errorMsg = e.message
                    }
                } finally {
                    isRefreshing = false
                }
            } else {
                glucoseValue = null
                errorMsg = "Account not configured"
            }
            delay(60000) // Auto-refresh every minute
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Top Toolbar
        Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            FilledIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF2C2C2C),
                    contentColor = Color.White
                )
            ) {
                Text("⚙", fontSize = 18.sp)
            }
        }

        // Bottom Loader (semi-transparent, fixed position)
        if (isRefreshing) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).alpha(0.6f),
                    strokeWidth = 2.dp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            if (errorMsg != null && glucoseValue == null) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).alpha(0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMsg!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(onClick = onSettingsClick, modifier = Modifier.height(32.dp)) {
                    Text("Setup Account", fontSize = 10.sp)
                }
            } else {
                val lastPoll = settingsManager.lastSuccessfulPollTimestamp
                val thresholdMs = settingsManager.noDataThresholdMin * 60_000L
                val isStale = lastPoll != 0L && (System.currentTimeMillis() - lastPoll) > thresholdMs
                val contentAlpha = if (isStale) 0.4f else 1.0f

                Text(
                    text = glucoseValue?.toString() ?: "--",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (glucoseValue != null) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.alpha(contentAlpha)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(contentAlpha)
                ) {
                    Text(
                        text = "mg/dL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = reading?.trendToArrow() ?: "→",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (isStale) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val mins = (System.currentTimeMillis() - lastPoll) / 60_000
                    Text(
                        text = "Stale ($mins min ago)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Red.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
