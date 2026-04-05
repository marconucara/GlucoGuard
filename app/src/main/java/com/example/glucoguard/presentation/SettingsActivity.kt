package com.example.glucoguard.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.glucoguard.GlucoGuardApp
import com.example.glucoguard.R
import com.example.glucoguard.api.LibreLinkUpClient
import com.example.glucoguard.service.GlucoseMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsManager = (application as GlucoGuardApp).settingsManager
            var currentScreen by remember { mutableStateOf("main") }

            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (currentScreen) {
                        "main" -> MainSettingsScreen(
                            onAccountClick = { currentScreen = "account" },
                            onThresholdsClick = { currentScreen = "thresholds" },
                            onBack = { finish() }
                        )
                        "account" -> AccountSettingsScreen(
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "main" }
                        )
                        "thresholds" -> ThresholdSettingsScreen(
                            settingsManager = settingsManager,
                            onBack = { currentScreen = "main" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainSettingsScreen(onAccountClick: () -> Unit, onThresholdsClick: () -> Unit, onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Button(onClick = onAccountClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_account))
            }
        }
        item {
            Button(onClick = onThresholdsClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_thresholds))
            }
        }
        item {
            FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_back))
            }
        }
    }
}

@Composable
fun AccountSettingsScreen(settingsManager: com.example.glucoguard.util.SettingsManager, onBack: () -> Unit) {
    var email by remember { mutableStateOf(settingsManager.email) }
    var password by remember { mutableStateOf(settingsManager.password) }
    
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) } // null = none, true = success, false = fail
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Reset test result if user changes credentials
    LaunchedEffect(email, password) {
        testResult = null
    }

    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Text(stringResource(R.string.settings_libre_creds), style = MaterialTheme.typography.titleSmall) }
        
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.settings_email), style = MaterialTheme.typography.labelSmall)
                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Start),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (email.isEmpty()) Text("email...", color = Color.Gray, fontSize = 12.sp)
                            innerTextField()
                        }
                    }
                )
            }
        }
        
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.settings_password), style = MaterialTheme.typography.labelSmall)
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Start),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (password.isEmpty()) Text("password", color = Color.Gray, fontSize = 12.sp)
                            innerTextField()
                        }
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Button(
                onClick = {
                    testing = true
                    testResult = null
                    focusManager.clearFocus()
                    scope.launch {
                        testResult = withContext(Dispatchers.IO) {
                            try {
                                val reading = LibreLinkUpClient.fetchGlucose(email.trim(), password.trim())
                                Log.i("SettingsActivity", "Test connection success: ${reading.value} mg/dL")
                                true
                            } catch (e: Exception) {
                                Log.e("SettingsActivity", "Test connection failed: ${e.message}")
                                false
                            }
                        }
                        testing = false
                    }
                },
                enabled = !testing && email.isNotEmpty() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            testing -> "Testing..."
                            testResult == true -> "Success ✓"
                            testResult == false -> "Failed ✗"
                            else -> stringResource(R.string.settings_test_conn)
                        },
                        color = when (testResult) {
                            true -> Color.Green
                            false -> Color.Red
                            else -> Color.Unspecified
                        }
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_back))
                }
                Button(onClick = {
                    val newEmail = email.trim()
                    val newPassword = password.trim()
                    
                    Log.i("SettingsActivity", "Saving account settings: email=$newEmail, password=$newPassword")
                    settingsManager.email = newEmail
                    settingsManager.password = newPassword
                    
                    LibreLinkUpClient.invalidateCache()
                    
                    val intent = Intent(context, GlucoseMonitorService::class.java).apply {
                        action = GlucoseMonitorService.ACTION_REFRESH_POLLING
                    }
                    context.startService(intent)
                    
                    onBack()
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
fun ThresholdSettingsScreen(settingsManager: com.example.glucoguard.util.SettingsManager, onBack: () -> Unit) {
    var nLow by remember { mutableStateOf(settingsManager.normalLow.toString()) }
    var nHigh by remember { mutableStateOf(settingsManager.normalHigh.toString()) }
    var dLow by remember { mutableStateOf(settingsManager.dndLow.toString()) }
    var dHigh by remember { mutableStateOf(settingsManager.dndHigh.toString()) }
    
    ScalingLazyColumn(
        state = rememberScalingLazyListState(),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Text(stringResource(R.string.settings_normal_thresholds), style = MaterialTheme.typography.titleSmall) }
        item { ThresholdInput(stringResource(R.string.settings_low), nLow) { nLow = it } }
        item { ThresholdInput(stringResource(R.string.settings_high), nHigh) { nHigh = it } }
        
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { Text(stringResource(R.string.settings_dnd_thresholds), style = MaterialTheme.typography.titleSmall) }
        item { ThresholdInput(stringResource(R.string.settings_low), dLow) { dLow = it } }
        item { ThresholdInput(stringResource(R.string.settings_high), dHigh) { dHigh = it } }
        
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_back))
                }
                Button(onClick = {
                    val nl = nLow.toIntOrNull() ?: settingsManager.normalLow
                    val nh = nHigh.toIntOrNull() ?: settingsManager.normalHigh
                    val dl = dLow.toIntOrNull() ?: settingsManager.dndLow
                    val dh = dHigh.toIntOrNull() ?: settingsManager.dndHigh
                    
                    Log.i("SettingsActivity", "Saving thresholds: N[$nl-$nh], DND[$dl-$dh]")
                    settingsManager.normalLow = nl
                    settingsManager.normalHigh = nh
                    settingsManager.dndLow = dl
                    settingsManager.dndHigh = dh
                    
                    onBack()
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
fun ThresholdInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() }) onValueChange(it) },
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            cursorBrush = SolidColor(Color.White),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .width(60.dp)
                .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                .padding(6.dp)
        )
    }
}
