# App Oops

**English** | [简体中文](README.zh-CN.md)

A **Material 3 Expressive** Android permission auditor and AppOps controller powered by **Shizuku**. Manage sensitive app permissions, block unauthorized motion/clipboard access, restrict background abuse, and sync community rules — all without Root.

---

## ✨ Features

- **Rootless Low-Level Permission Control (Shizuku Privileged Shell)**
  - Obtains privileged Shell (`UID: 2000`) and `IAppOpsService` proxy via Shizuku to safely manage AppOps modes (`ALLOW` / `IGNORE` / `DENY` / `DEFAULT`) without Root access.
  - Covers four sensitive permission categories:
    - **Sensors & Movement**: Body & motion sensors, high sampling rate sensors, background body sensors, and activity recognition (blocks intrusive shake-to-trigger ad detection).
    - **Clipboard & Privacy**: Read/write clipboard, read installed packages, and usage statistics.
    - **Background & WakeLock**: Run in background, run any in background, CPU `WakeLock`, and boot completed auto-start.
    - **System Alert & Security**: Display over other apps (overlay), modify system settings, and notification listener access.
- **Real-time Interception Guard & Flexible Alerts**
  - Foreground monitor service records intercepted accesses in Audit Logs and displays latest activity on the Dashboard.
  - Supports **Toast Notices**, **System Notifications**, and **Android 16+ Live Updates** promoted alerts in the status bar and lockscreen.
  - Supports **Temporary Permission Allow**: grant timed temporary access directly from an interception alert and automatically re-block when the timer expires.
- **Batch Presets & Community Rules**
  - Built-in batch rule presets (disable accelerometer/gyroscope, intercept clipboard read/write, block app list & usage stats, background restriction) with multi-app selection.
  - Subscribe to [GraceThings/app-oops-rules](https://github.com/GraceThings/app-oops-rules) (via GitHub, jsDelivr CDN, or Gitee mirror) or custom repositories, automatically matching rules to apps installed on your device.
  - Export modified app permissions as rule JSON and submit directly via GitHub Issue to trigger automated PR creation.
- **Material 3 Expressive UI & Localization**
  - Supports Dynamic Color (Monet), curated expressive palettes (Emerald Jade, Deep Cobalt, Radiant Purple, Sunset Amber), and Light / Dark / Follow System themes.
  - Built-in support for **Follow System**, **English**, **简体中文**, **繁體中文（香港）**, **繁體中文（台灣）**, **日本語**, and **한국어** (using Android 13+ per-app `LocaleManager` and persisted fallback on Android 11–12).

---

## 🛠️ Requirements & Build

- **Min SDK**: Android 11 (API 30)
- **Compile / Target SDK**: API 37
- **Tech Stack**:
  - Kotlin `2.1.10` • AGP `9.1.0`
  - Jetpack Compose BOM `2026.03.01` • Material 3 `1.5.0-alpha23`
  - Shizuku API / Provider `13.1.5` • HiddenApiBypass `4.3`

```bash
# Clone the repository
git clone https://github.com/GraceThings/app-oops-android.git
cd app-oops-android

# Run unit tests and build the Debug APK
./gradlew testDebugUnitTest assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

---

## 🔗 Links

- **Project Homepage**: [https://github.com/GraceThings/app-oops-android](https://github.com/GraceThings/app-oops-android)
- **Community Rules Repository**: [https://github.com/GraceThings/app-oops-rules](https://github.com/GraceThings/app-oops-rules)
- **Developer**: Grace Chan ([GraceThings](https://github.com/GraceThings))
