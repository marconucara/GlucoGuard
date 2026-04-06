# 🛡️ GlucoGuard for Wear OS

**GlucoGuard** is a high-reliability, background monitoring and alarm system for LibreLinkUp users on Wear OS. It is designed to be your "safety net": a minimalist app that ensures you never miss a glucose alert, even when your watch is in deep sleep.

---

## 🩺 User Guide

### Why GlucoGuard?
Most Wear OS apps struggle with aggressive battery management (especially on Samsung Galaxy Watches), often "sleeping" when you need them most. GlucoGuard is built differently:
- **Always-On Protection**: Uses advanced Android Alarms to wake up and check your data every minute (or as needed), ensuring 24/7 reliability.
- **Smart Battery (Adaptive Polling)**: The app is "smart". It checks every minute when you are near your limits or your glucose is moving fast, but "relaxes" up to 10 minutes when you are stable and safe to save battery.
- **DND Aware**: Automatically respects your watch's "Do Not Disturb" mode, switching to a secondary profile with wider thresholds for a peaceful night.
- **Ongoing Activity**: See your live glucose (e.g., `140↗`) directly in your watch's app launcher or recent apps list.

### Quick Setup
1. **Invite Yourself as a Caregiver**: In your official **FreeStyle Libre** phone app, go to "Connected Apps" -> "LibreLinkUp" and invite your own email (or a secondary one).
2. **Create a Caregiver Account**: Use the link in the invitation email to create a **separate** account on the **LibreLinkUp** platform.
3. **Accept the Invitation**: Log into the official **LibreLinkUp mobile app** at least once to accept the invitation and terms.
4. **Configure GlucoGuard**: Enter these caregiver credentials in the watch app's Settings. 
   *Note: GlucoGuard connects to the cloud, so your phone must be uploading data for the watch to receive it.*

---

## 🛠 Developer & Architecture Notes

### Stability & Resilience
- **`AlarmManager` Strategy**: To bypass Samsung Wear OS 4+ background restrictions, we use `setExactAndAllowWhileIdle`. This forces the system to grant a CPU window for the polling service even in Doze mode.
- **Foreground Service**: Runs as a `dataSync` type with a `Partial WakeLock` to ensure network completion.
- **Self-Healing**: Every poll schedules the next one. If the process is killed by the OS, the next hardware alarm will trigger a service restart.

### Adaptive Polling Algorithm
The polling frequency is dynamic based on:
- **Glucose Distance**: Distance from Low/High thresholds (30, 20, 10 mg/dL steps).
- **Trend Velocity**: Constants (→) vs Slight (↗/↘) vs Rapid (↑/↓).
- **Directional Multipliers**: Threshold distances are doubled (x2) when the trend is moving away from a limit to maximize battery life without compromising safety.

### Technical Stack
- **UI**: Jetpack Compose for Wear OS (Material 3).
- **Storage**: `EncryptedSharedPreferences` for secure credential storage.
- **API**: Direct integration with LibreLinkUp (Abbott) cloud.

---

## 📜 Disclaimer & License

**NOT FOR MEDICAL USE**: This is an unofficial, third-party application. It is NOT affiliated with Abbott Laboratories. Use it for informational purposes only. Never make medical decisions based on this app. Always verify with a blood glucose meter.

**License**: MIT License. Copyright (c) 2024-2026.
