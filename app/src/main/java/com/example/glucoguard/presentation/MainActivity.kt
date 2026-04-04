package com.example.glucoguard.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.glucoguard.service.GlucoseMonitorService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.presentation.theme.GlucoGuardTheme
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
}

@Composable
fun GlucoseTestScreen() {
    var status by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        status = try {
            val reading = withContext(Dispatchers.IO) { LibreLinkUpClient.fetchGlucose() }
            "${reading.value} mg/dL\nTrend: ${reading.trend}"
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
