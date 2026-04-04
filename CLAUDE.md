# GlucoGuard - Wear OS Glucose Alarm App

## Project Goal

A minimal Wear OS app that polls LibreLinkUp API every minute and triggers vibration alarms when glucose is out of range. No watchface, no visualization - just background monitoring and alarms. Gluroo handles the watchface separately.

## Architecture

Three components:

1. **Foreground Service** - Runs 24/7 with a partial wake lock. Every 60 seconds: authenticates to LibreLinkUp, fetches glucose value, evaluates against thresholds, triggers alarm if needed.
2. **Alarm Engine** - Evaluates glucose against two threshold profiles: normal (70-170 mg/dL) and DND (60-250 mg/dL). The active profile is determined by the system's Do Not Disturb state, not by time of day. Uses `NotificationManager.getCurrentInterruptionFilter()` to check DND.
3. **Alarm Activity** - Full-screen activity (like a wake-up alarm) that shows the glucose value prominently and provides Dismiss and Snooze (30 min) buttons. Launched by the service when glucose is out of range.

## Key Behaviors

- **Snooze is global**, not per-alarm-type. When snoozed, no alarms fire for 30 minutes regardless of high or low. The snooze variable is a simple timestamp (`snoozeUntil`).
- **Snooze auto-resets** when glucose returns to the normal range. If snoozed but glucose comes back in range, clear `snoozeUntil` so that a new excursion triggers immediately.
- **Vibration** uses the `Vibrator` API directly for reliability (bypasses notification sound policies). Use a strong, repeating pattern that is hard to miss.
- **Notification** is also posted alongside the vibration as a visible log, but the primary alert mechanism is the full-screen Activity + Vibrator.
- **DND detection**: `INTERRUPTION_FILTER_NONE` or `INTERRUPTION_FILTER_PRIORITY` = DND active = use relaxed thresholds. Otherwise use normal thresholds. Requires `ACCESS_NOTIFICATION_POLICY` permission.

## LibreLinkUp API

Reference implementation is in `glice.js` (Node.js). The Kotlin implementation must replicate the same flow:

### Step 1 - Login
```
POST https://api-eu.libreview.io/auth/login
Headers:
  User-Agent: LibreLinkUp/4.16.0 CFNetwork/1485 Darwin/23.1.0
  product: llu.ios
  version: 4.16.0
  Accept: application/json
  Content-Type: application/json
Body: { "email": "<email>", "password": "<password>" }
Response: { data: { authTicket: { token }, user: { id } } }
```

### Step 2 - Get Connection ID
```
GET https://api-eu.libreview.io/llu/connections
Headers: same User-Agent/product/version/Accept as above, plus:
  Authorization: Bearer <token>
  Account-Id: SHA256(<user.id>)
Response: { data: [{ patientId }] }
```

### Step 3 - Get Glucose
```
GET https://api-eu.libreview.io/llu/connections/<patientId>/graph
Headers: same as step 2
Response: { data: { connection: { glucoseMeasurement: { ValueInMgPerDl, TrendArrow } } } }
```

**Important**: There is no token refresh. Just re-login every time. The three calls are made sequentially on every poll cycle. This is intentional to keep the code simple.

## Hardcoded Configuration (first version)

All config values are constants in a single `Config.kt` file. No UI for settings. We will add a settings UI later.

```kotlin
object Config {
    const val EMAIL = "nuky1989@gmail.com"
    const val PASSWORD = "010101010101"
    const val BASE_URL = "https://api-eu.libreview.io"

    const val POLL_INTERVAL_MS = 60_000L // 1 minute

    // Normal thresholds
    const val NORMAL_LOW = 70
    const val NORMAL_HIGH = 170

    // DND thresholds (more relaxed)
    const val DND_LOW = 60
    const val DND_HIGH = 250

    const val SNOOZE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
}
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose for Wear OS (only for the alarm full-screen activity)
- **HTTP**: Ktor Client or OkHttp (whichever is simpler for Wear OS)
- **Background**: Foreground Service with partial wake lock (NOT WorkManager)
- **Vibration**: Vibrator API (android.os.Vibrator / VibratorManager)
- **Storage**: Not needed for v1 (all config is hardcoded, snooze state is in memory)
- **Min SDK**: API 30 (Wear OS 3+, Galaxy Watch 4/5)
- **Target SDK**: API 34

## Project Structure

```
app/src/main/java/com/glucoguard/
  Config.kt                  # All hardcoded constants
  GlucoGuardApp.kt           # Application class
  service/
    GlucoseMonitorService.kt # Foreground service, polling loop, alarm logic
  api/
    LibreLinkUpClient.kt     # HTTP calls to LibreLinkUp (login, connections, graph)
    Models.kt                # Data classes for API responses
  alarm/
    AlarmActivity.kt         # Full-screen alarm UI (value + Dismiss + Snooze)
    VibrationHelper.kt       # Vibration patterns
  util/
    DndHelper.kt             # Check DND state
```

## Implementation Order

Work in this exact order. Each step should compile and be testable before moving to the next.

### Step 1: Scaffold + API Client
Create the project scaffold with the package structure above. Implement `Config.kt` with all constants and `LibreLinkUpClient.kt` that performs the 3-step API flow (login → connections → graph) and returns the glucose value as an Int. Add `Models.kt` with data classes for the JSON responses. Add a temporary main activity that calls the API client on launch and displays the glucose value as text on screen, just to verify the API works on Wear OS.

### Step 2: Foreground Service
Implement `GlucoseMonitorService.kt` as a foreground service with a persistent notification ("GlucoGuard monitoring..."). Use a Handler + partial wake lock to poll every 60 seconds. Each poll calls `LibreLinkUpClient`, logs the value. The service starts on app launch and survives app closure. Add `FOREGROUND_SERVICE`, `WAKE_LOCK`, `INTERNET` permissions to manifest.

### Step 3: Alarm Logic + DND
Add `DndHelper.kt` to check DND state. In the service polling loop, after fetching glucose: check DND → select thresholds → compare value → if out of range AND not snoozed → trigger alarm. Add snooze state as a simple `var snoozeUntil: Long = 0` in the service. Add auto-reset logic (clear snooze when value returns in range).

### Step 4: Alarm Activity + Vibration
Implement `AlarmActivity.kt` as a full-screen Compose activity. Large glucose value centered on screen, color-coded (red for low, orange/yellow for high). Two buttons: "Dismiss" (closes activity) and "Snooze 30m" (sets snooze timestamp via a bound service or broadcast, then closes). Implement `VibrationHelper.kt` with a strong repeating vibration pattern. The service triggers both the vibration and launches the activity with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` and a full-screen intent.

### Step 5: Auto-start on Boot
Add a `BootCompletedReceiver` that starts the foreground service on device boot. Add `RECEIVE_BOOT_COMPLETED` permission.

## What NOT To Do

- Do NOT create a watchface or complications
- Do NOT implement a settings/config UI (everything is in Config.kt)
- Do NOT use WorkManager (we need precise 1-minute intervals)
- Do NOT use Room/SQLite (no persistence needed in v1)
- Do NOT handle token refresh (re-login every time)
- Do NOT store credentials securely (hardcoded in Config.kt for v1)
- Do NOT implement trend-based alerts (only absolute thresholds for v1)
- Do NOT add Hilt/Dagger (overkill for this size)
