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
import androidx.compose.ui.text.input.TextFieldValue
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
    var emailState by remember { mutableStateOf(TextFieldValue(settingsManager.email)) }
    var passwordState by remember { mutableStateOf(TextFieldValue(settingsManager.password)) }
    
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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
                    value = emailState,
                    onValueChange = { emailState = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    decorationBox = { innerTextField ->
                        if (emailState.text.isEmpty()) Text("email@example.com", color = Color.Gray, fontSize = 12.sp)
                        innerTextField()
                    }
                )
            }
        }
        
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.settings_password), style = MaterialTheme.typography.labelSmall)
                BasicTextField(
                    value = passwordState,
                    onValueChange = { passwordState = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    decorationBox = { innerTextField ->
                        if (passwordState.text.isEmpty()) Text("password", color = Color.Gray, fontSize = 12.sp)
                        innerTextField()
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Button(
                onClick = {
                    testing = true
                    focusManager.clearFocus()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val reading = LibreLinkUpClient.fetchGlucose(emailState.text.trim(), passwordState.text.trim())
                                Log.i("SettingsActivity", "Test connection success: ${reading.value} mg/dL")
                            } catch (e: Exception) {
                                Log.e("SettingsActivity", "Test connection failed: ${e.message}")
                            }
                        }
                        testing = false
                    }
                },
                enabled = !testing && emailState.text.isNotEmpty() && passwordState.text.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Text(if (testing) "..." else stringResource(R.string.settings_test_conn))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_back))
                }
                Button(onClick = {
                    val newEmail = emailState.text.trim()
                    val newPassword = passwordState.text.trim()
                    
                    Log.i("SettingsActivity", "Saving account settings: email=$newEmail, password=$newPassword")
                    settingsManager.email = newEmail
                    settingsManager.password = newPassword
                    
                    // Clear API cache to force fresh login with new credentials
                    LibreLinkUpClient.invalidateCache()
                    
                    // Trigger immediate poll
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
    var nLow by remember { mutableStateOf(TextFieldValue(settingsManager.normalLow.toString())) }
    var nHigh by remember { mutableStateOf(TextFieldValue(settingsManager.normalHigh.toString())) }
    var dLow by remember { mutableStateOf(TextFieldValue(settingsManager.dndLow.toString())) }
    var dHigh by remember { mutableStateOf(TextFieldValue(settingsManager.dndHigh.toString())) }
    
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
                    val nl = nLow.text.toIntOrNull() ?: settingsManager.normalLow
                    val nh = nHigh.text.toIntOrNull() ?: settingsManager.normalHigh
                    val dl = dLow.text.toIntOrNull() ?: settingsManager.dndLow
                    val dh = dHigh.text.toIntOrNull() ?: settingsManager.dndHigh
                    
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
fun ThresholdInput(label: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = { if (it.text.all { c -> c.isDigit() }) onValueChange(it) },
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
