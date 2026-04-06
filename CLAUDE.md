# GlucoGuard - Wear OS Glucose Alarm App

## Project Goal
A minimal Wear OS app for 24/7 glucose monitoring via LibreLinkUp API. Focuses on safety (vibration alarms) and reliability on Samsung Wear OS 4+, without requiring manual battery optimization bypasses.

## Architecture & Stability Choice

### 1. The "Indestructible" Service
- **`GlucoseMonitorService`**: A Foreground Service (`dataSync` type) using a Partial WakeLock.
- **`AlarmManager` Strategy**: Instead of a software loop, we use `setExactAndAllowWhileIdle`. This forces the system to wake the app even in Doze mode, making it resilient to Samsung's aggressive background killing without needing the "Ignore Battery Optimizations" system dialog.
- **Self-Healing**: Every poll schedules the next one. If the process is killed, the next Alarm triggers a service restart.

### 2. Battery Optimization (Adaptive Polling)
The app dynamically adjusts polling frequency to save battery while maintaining safety:
- **1 min**: Default, or when near thresholds, or with steep trends (↑/↓).
- **2, 5, or 10 min**: When glucose is stable (→) and far from thresholds.
- **Slight Trend Multiplier**: Threshold distances are doubled (x2) when the trend is moving away or slightly towards a limit (↘/↗) to balance safety and power.

### 3. UX & Integration
- **Ongoing Activity**: Displays live glucose (e.g., "140↗") in the watch launcher/recents.
- **Alarm Flow**: Out-of-range detected → `VibrationHelper` starts → `MainActivity` (on resume) or `AlarmActivity` (via FullScreenIntent) handles the UI.

## Key Constraints (Samsung Wear OS 4+)
- **Battery Dialogs**: Standard `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is often blocked/flickers. We bypass this by using `AlarmManager` + `SCHEDULE_EXACT_ALARM`.
- **DND**: App respects system Do Not Disturb by switching to a "DND Profile" with stricter thresholds (Config.kt).

## To-Do for Store Release
- [ ] **Assets**: Capture screenshots (Main, Settings, Alarm) using Device Mirroring.
- [ ] **Store Listing**: Draft description with medical disclaimers (Unofficial app, not for medical decisions).

## Implementation Status (Quick Ref)
- [x] **Security**: Credentials in `EncryptedSharedPreferences`.
- [x] **Reliability**: Persistence across reboots (`BootCompletedReceiver`).
- [x] **Safety**: 10-minute WakeLock safety timeout.
- [x] **Compliance**: Privacy policy hosted on GitHub Pages.
- [x] **Localization**: English and Italian support.
