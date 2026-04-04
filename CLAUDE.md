# GlucoGuard - Wear OS Glucose Alarm App

## Project Goal

A minimal Wear OS app that polls LibreLinkUp API every minute and triggers vibration alarms when glucose is out of range. No watchface, no visualization - just background monitoring and alarms. Gluroo handles the watchface separately.

## Architecture

Three components:

1. **Foreground Service** - Runs 24/7 with a partial wake lock. Every 60 seconds: authenticates to LibreLinkUp, fetches glucose value, evaluates against thresholds, triggers alarm if needed.
2. **Alarm Engine** - Evaluates glucose against two threshold profiles: normal (70-170 mg/dL) and DND (60-250 mg/dL). The active profile is determined by the system's Do Not Disturb state, not by time of day. Uses `NotificationManager.getCurrentInterruptionFilter()` to check DND.
3. **Alarm Activity** - Full-screen activity (like a wake-up alarm) that shows the glucose value prominently and provides Dismiss and Snooze (configurable duration) buttons. Launched by MainActivity when alarm is active.

## Key Behaviors

- **Snooze is global**, not per-alarm-type. When snoozed, no alarms fire for the chosen duration regardless of high or low. The snooze variable is a simple timestamp (`snoozeUntil`).
- **Snooze does NOT auto-reset** when glucose returns to range (intentional: avoids re-alarming if glucose oscillates around the threshold). Snooze expires only when the timer elapses.
- **Snooze duration** is configurable via +/- picker in AlarmActivity (1-120 min, default 30). Last chosen value persisted in SharedPreferences.
- **Vibration** uses the `Vibrator` API directly for reliability (bypasses notification sound policies). Use a strong, repeating pattern that is hard to miss.
- **Alarm flow**: service detects out-of-range → sets `alarmActive = true` + starts vibration → user opens app → `MainActivity.onResume()` detects `alarmActive` → launches `AlarmActivity` from foreground (always allowed).
- **DND detection**: `INTERRUPTION_FILTER_NONE` or `INTERRUPTION_FILTER_PRIORITY` = DND active = use relaxed thresholds. Otherwise use normal thresholds. Requires `ACCESS_NOTIFICATION_POLICY` permission.

## Known Platform Constraints (Samsung Wear OS 6 / One UI Watch 8)

- **Background Activity Launch (BAL) is fully locked down**: Samsung blocks activity launches from background even for `AlarmManager.setAlarmClock()` and `fullScreenIntent` notifications. Only launching from a foreground Activity works reliably. This is why the alarm flow goes through `MainActivity.onResume()`.
- **Notification permission**: `POST_NOTIFICATIONS` (API 33+) must be requested at runtime. The dialog may not appear reliably on first install on Wear OS — this is an open issue. The persistent foreground service notification ("Monitoring glucose…") is exempt and always shows.
- **Battery optimization**: Samsung Wear OS aggressively kills background processes. If the service is not excluded from battery optimization, it may be suspended, causing missed polls and missed alarms. This is likely also why notifications sometimes don't appear.

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

**TrendArrow mapping**: 1=↓, 2=↘, 3=→, 4=↗, 5=↑

## Hardcoded Configuration (current)

All config values are constants in `Config.kt`. Settings UI is planned (see Next Steps).

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

    const val SNOOZE_DURATION_MS = 30 * 60 * 1000L // 30 min default (overridden by SharedPrefs)
}
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose for Wear OS
- **HTTP**: OkHttp
- **Background**: Foreground Service with partial wake lock (NOT WorkManager)
- **Vibration**: Vibrator API (android.os.Vibrator)
- **Storage**: SharedPreferences (snooze duration preference)
- **Min SDK**: API 30 (Wear OS 3+)
- **Target SDK**: API 36
- **Localization**: EN (default) + IT via `res/values-it/strings.xml`

## Project Structure

```
app/src/main/java/com/example/glucoguard/
  Config.kt                    # All hardcoded constants
  GlucoGuardApp.kt             # Application class, notification channels
  service/
    GlucoseMonitorService.kt   # Foreground service, polling loop, alarm logic
                               # Companion object holds alarmActive, lastAlarmValue, lastAlarmIsLow
  api/
    LibreLinkUpClient.kt       # HTTP calls to LibreLinkUp (login, connections, graph)
    Models.kt                  # Data classes for API responses
  alarm/
    AlarmActivity.kt           # Full-screen alarm UI (value + snooze picker + Dismiss + Snooze)
    VibrationHelper.kt         # Vibration patterns
  receiver/
    BootCompletedReceiver.kt   # Starts service on device boot
  util/
    DndHelper.kt               # Check DND state
  presentation/
    MainActivity.kt            # Debug screen + alarm redirect logic in onResume()
    theme/Theme.kt
```

## Next Steps

Work in priority order. Each step should compile and be testable before moving to the next.

### Step A: Battery Optimization Exemption
Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and prompt the user to exclude GlucoGuard from battery optimization at first launch. This is critical for reliable 24/7 monitoring on Samsung Wear OS and likely also fixes the notification delivery issue. Requires `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in manifest and a `PowerManager.isIgnoringBatteryOptimizations()` check at startup.

### Step B: Fix Notification Permission
Investigate why `POST_NOTIFICATIONS` dialog does not appear on Wear OS despite `requestPermissions()` being called. Possible causes: dialog shown before activity is visible, Wear OS handling differences, or Samsung suppressing the dialog. Consider showing an in-app prompt with a button that explicitly calls `requestPermissions()` on user tap rather than automatically at startup.

### Step C: API Client Optimization
**Token caching**: Keep `token` and `accountIdHash` in memory (or SharedPreferences). On each poll, skip login and use cached token. Only re-login if any API call returns 4xx. This reduces each poll from 3 HTTP calls to 1, improving reliability and reducing battery usage.

**PatientId caching**: Cache `patientId` in SharedPreferences after first successful fetch (it is expected to be stable over time — verify this assumption in practice). Skip the connections call on every poll. Only re-fetch patientId if the graph call fails for any reason. This further reduces steady-state polling to a single HTTP call.

Implementation: refactor `LibreLinkUpClient` to hold state (`cachedToken`, `cachedAccountIdHash`, `cachedPatientId`) and implement retry-with-refresh logic.

### Step D: Settings UI
Add a settings screen accessible via a gear icon from MainActivity. Split into two sub-screens:

1. **Account settings**: email, password fields + "Test connection" button that runs the 3-step API flow and shows the result.
2. **Threshold settings**: sliders or +/- pickers for NORMAL_LOW, NORMAL_HIGH, DND_LOW, DND_HIGH. Show current DND state for context.

Add a **"Test alarm"** button (in settings or main screen) that immediately triggers the alarm flow (sets `alarmActive=true`, starts vibration) without waiting for a real out-of-range reading — useful to verify the full flow without manipulating Config.kt.

Persist all settings in SharedPreferences. Remove hardcoded values from `Config.kt` (keep only non-user-facing constants like POLL_INTERVAL_MS).

### Step E: Persistence & UX Polish
- **Last known value**: persist latest glucose value + timestamp + trend in SharedPreferences. Show immediately on app open while the next poll is in progress.
- **Last updated timestamp**: show "updated Xs ago" below the glucose value in the main screen.
- **Stale data warning**: if last successful poll is older than 5 minutes, show a visual warning (e.g., gray out the value or show a "⚠ no data" indicator).

## What NOT To Do

- Do NOT create a watchface or complications
- Do NOT use WorkManager (we need precise 1-minute intervals)
- Do NOT use Room/SQLite (SharedPreferences is sufficient)
- Do NOT handle token refresh via a refresh endpoint (re-login on 4xx)
- Do NOT add Hilt/Dagger (overkill for this size)
- Do NOT implement trend-based alerts (only absolute thresholds)
