# GlucoGuard - Wear OS Glucose Alarm App

## Project Goal

A minimal Wear OS app that polls LibreLinkUp API every minute and triggers vibration alarms when glucose is out of range. No watchface, no visualization - just background monitoring and alarms.

## Architecture

Three components:

1. **Foreground Service** - Runs 24/7 with a partial wake lock. Every 60 seconds: authenticates to LibreLinkUp, fetches glucose value, evaluates against thresholds, triggers alarm if needed.
2. **Alarm Engine** - Evaluates glucose against two threshold profiles: normal and DND. Profile is determined by system Do Not Disturb state.
3. **Alarm Activity** - Full-screen activity showing glucose value with Dismiss and Snooze buttons.

## Key Behaviors

- **Snooze is global**: Suppresses all alarms for a set duration (1-120 min).
- **Vibration**: Strong, repeating pattern via `Vibrator` API.
- **Alarm flow**: Service detected out-of-range → `alarmActive = true` → `MainActivity.onResume()` detects state → launches `AlarmActivity`.
- **DND detection**: Uses `NotificationManager.getCurrentInterruptionFilter()`.

## Known Platform Constraints (Samsung Wear OS)

- **BAL (Background Activity Launch)**: Heavily restricted. Alarms must be triggered via foreground activity (MainActivity) or notifications with high priority.
- **Battery optimization**: Samsung aggressively kills background services. Must be manually excluded by user.

## Tech Stack

- **Language**: Kotlin
- **UI**: Compose for Wear OS
- **HTTP**: OkHttp + Gson
- **Background**: Foreground Service + Partial WakeLock
- **Localization**: EN (default), IT

## Project Structure

```
app/src/main/java/com/glucoguard/app/
  Config.kt                    # Constants (DND thresholds, etc.)
  GlucoGuardApp.kt             # Application class, DI (SettingsManager)
  service/
    GlucoseMonitorService.kt   # Background polling and alarm logic
  api/
    LibreLinkUpClient.kt       # API Client (login, fetch)
    Models.kt                  # Data classes + trendToArrow()
  alarm/
    AlarmActivity.kt           # Alarm UI with Snooze/Dismiss
    VibrationHelper.kt         # Vibration patterns
  receiver/
    BootCompletedReceiver.kt   # Starts service on device boot
  util/
    SettingsManager.kt         # SharedPreferences wrapper
    DndHelper.kt               # DND state check
  presentation/
    MainActivity.kt            # Main monitor screen
    SettingsActivity.kt        # Multi-screen settings UI
```

## To-Do for Store Release

### Technical
- [ ] **Battery Optimization Prompt**: Implement `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` request.
- [ ] **Ongoing Activity**: Support `androidx.wear.ongoing` for persistent launcher icon.

### Legal/Store
- [x] **Privacy Policy**: Created `privacy.html` for GitHub Pages.
- [ ] **Store Description**: Clarify this is a third-party companion app for LibreLinkUp; NOT for medical diagnosis.

## Completed
- [x] Package name changed to `com.glucoguard.app`.
- [x] Teal/White Branding and Icons.
- [x] Credential validation in Settings (Test Connection).
- [x] Reliability fixes for Wear OS keyboard (truncation/sync issues).
- [x] Re-login synchronization (prevent multiple simultaneous logins).
- [x] Auto-start on boot (`BootCompletedReceiver`).
- [x] Immediate UI refresh when returning from settings.
- [x] Medical Disclaimer screen (EN/IT).
