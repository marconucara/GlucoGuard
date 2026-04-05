package com.example.glucoguard.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.wear.compose.material3.*
import com.example.glucoguard.GlucoGuardApp
import com.example.glucoguard.R
import com.example.glucoguard.alarm.AlarmActivity
import com.example.glucoguard.api.GlucoseReading
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
import com.example.glucoguard.service.GlucoseMonitorService
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

        ContextCompat.startForegroundService(this, Intent(this, GlucoseMonitorService::class.java))
        setContent {
            GlucoGuardTheme {
                MainGlucoseScreen(
                    onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (GlucoseMonitorService.alarmActive.get()) {
            val intent = Intent(this, AlarmActivity::class.java).apply {
                putExtra(AlarmActivity.EXTRA_GLUCOSE_VALUE, GlucoseMonitorService.lastAlarmValue)
                putExtra(AlarmActivity.EXTRA_IS_LOW, GlucoseMonitorService.lastAlarmIsLow)
            }
            startActivity(intent)
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
    LaunchedEffect(refreshTrigger) {
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
                    errorMsg = e.message
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
                Text(
                    text = glucoseValue?.toString() ?: "--",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (glucoseValue != null) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
        }
    }
}
