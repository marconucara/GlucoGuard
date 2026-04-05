# GlucoGuard for Wear OS

**GlucoGuard** is a minimal, high-reliability background monitoring and alarm system for LibreLinkUp users on Wear OS. 

Designed for Type 1 Diabetics who need dependable alerts without the complexity or battery drain of full-featured visualization suites.

---

## 🩺 The User Experience

### Why GlucoGuard?
Official Wear OS support for glucose monitoring is often fragmented or restricted by aggressive battery management on modern watches (like Samsung Galaxy Watch series). GlucoGuard is built to solve one specific problem: **ensuring you never miss a hypo or hyper alert.**

- **Reliability First**: Unlike many watchfaces that "sleep" to save battery, GlucoGuard runs as a dedicated foreground service with a persistent notification, ensuring it stays alive 24/7.
- **Smart DND (Do Not Disturb)**: The app automatically detects your watch's system DND state. You can set a tighter glucose range for the day and a wider, "quiet" range for the night to avoid alarm fatigue while staying safe.
- **Minimalist Design**: No graphs, no complex menus. Just your current glucose, the trend arrow, and a rock-solid alarm interface.
- **Perfect Companion**: Use GlucoGuard alongside your favorite visualization apps (like **Gluroo**, **Juggluco**, or **G-Watch**). While those apps provide beautiful watchfaces and history, GlucoGuard acts as your "safety net" background alarm system.

### Key Features
- **Automatic Polling**: Fetches data from LibreLinkUp every 60 seconds.
- **Custom Thresholds**: Separate High/Low limits for Normal and DND modes.
- **Snooze Function**: Easily silence alarms for 1 to 120 minutes with a single tap.
- **Auto-Start**: Automatically resumes monitoring after a watch reboot.
- **Medical Disclaimer**: Integrated safety first approach with mandatory acceptance.

---

## 🚀 Setup & LibreLinkUp Configuration

GlucoGuard works as a "caregiver" app for users with **Abbott FreeStyle Libre** sensors. Because the main LibreLink app does not allow third-party access, you must follow this specific setup flow:

1. **Invite Yourself as a Caregiver**: Open your official **FreeStyle Libre** app on your phone. Go to "Connected Apps" -> "LibreLinkUp" and invite yourself. You can use the same email address as your main account or a secondary one.
2. **Create a Caregiver Account**: You will receive an invitation email. Use the link to create a **separate** account on the **LibreLinkUp** platform (or use the LibreLinkUp mobile app).
3. **Accept the Invitation**: Log into the **LibreLinkUp app** at least once on your phone with the new caregiver credentials. You must accept the invitation and the terms of service within the official app for the connection to become active.
4. **Configure GlucoGuard**: Once the connection is confirmed in the LibreLinkUp app, you can uninstall it from your phone. Open **GlucoGuard** on your watch, enter those same caregiver credentials, and tap "Test Connection".

*Note: GlucoGuard connects to the LibreLinkUp cloud, not directly to the sensor. This means your phone must be uploading data to the cloud for the watch to receive it.*

---

## 🛠 Technical Overview

GlucoGuard is built using modern Android standards for Wear OS:
- **Language**: 100% Kotlin with Jetpack Compose for Wear OS.
- **Service**: A Foreground Service utilizing a `Partial WakeLock` to ensure network requests are completed even when the screen is off.
- **API**: Authenticates directly with the official LibreLinkUp (Abbott) cloud API.
- **Battery Optimization**: Includes a dedicated flow to guide users through excluding the app from system battery restrictions (essential for Samsung Wear OS devices).

### Device Compatibility
Currently, the app has been extensively tested and optimized specifically for the **Samsung Galaxy Watch 5 Pro**. While it should work on most Wear OS 3.0+ devices, the background activity and vibration patterns are tuned for the Galaxy Watch's specific behavior.

---

## 📜 License & Disclaimer

**Disclaimer**: This is a third-party, unofficial application. It is NOT affiliated with, authorized, or endorsed by Abbott Laboratories. This software is provided for personal informational purposes only and is **NOT** intended for medical diagnosis, treatment decisions, or to replace professional medical advice. Always verify glucose levels with a finger-stick blood glucose meter before taking any medical action.

**License**: MIT License

Copyright (c) 2024-2026

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
