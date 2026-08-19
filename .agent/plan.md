# Project Plan

モバイルデータ通信時 自動帯域制御アプリ (Bandwidth Automator / DataThrottle)

目的:
モバイルデータ通信接続時のみダウンロード帯域を制限し、Wi-Fi接続時や未接続時は自動で制限を完全解除する。

主な機能:
1. ネットワーク監視: ConnectivityManager.registerDefaultNetworkCallback を使用し、アクティブな回線（Cellular/Wi-Fi）を検知。
2. 帯域制御: Settings.Global.putInt(contentResolver, "ingress_rate_limit_bytes_per_second", value) を利用。
3. 常駐サービス: Android 14/15 対応の Foreground Service (specialUse) として実装。
4. フェイルセーフ: タスクキル時(onTaskRemoved)やサービス停止時に帯域制限を解除(-1)。
5. UI: 帯域制限値の設定(Mbps)、ADB権限(WRITE_SECURE_SETTINGS)の確認、適合性診断(実測テスト)、バッテリー最適化解除要求。

技術スタック:
- Language: Kotlin
- UI Framework: Jetpack Compose
- Target SDK: 33+ (Android 13+)
- Permissions: WRITE_SECURE_SETTINGS (via ADB), FOREGROUND_SERVICE_SPECIAL_USE, etc.

## Project Brief

# Project Brief: DataThrottle (Bandwidth Automator)

## Features
1. **Network-Adaptive Throttling**: Automatically enforces download bandwidth limits when switching to cellular data and removes all restrictions upon Wi-Fi connection.
2. **Resilient Foreground Service**: Implements a `specialUse` foreground service optimized for Android 14/15, ensuring consistent network monitoring and enforcement in the background.
3. **Customizable Bandwidth Control**: A simple UI to configure specific Mbps limits, check `WRITE_SECURE_SETTINGS` permission status, and verify performance via an integrated diagnostic test.
4. **Fail-Safe Restoration**: Automatically resets system bandwidth settings to default (-1) if the app is removed from tasks or the service is stopped, preventing accidental permanent speed caps.

## High-Level Technical Stack
- **Kotlin**: Primary language for robust and maintainable code.
- **Jetpack Compose**: Modern toolkit for building a reactive and high-performance UI.
- **Jetpack Navigation 3**: State-driven navigation for a scalable and testable app architecture.
- **Compose Material Adaptive**: Responsive layout system to ensure compatibility with various form factors (phones, foldables, tablets).
- **Kotlin Coroutines**: For non-blocking network callbacks and asynchronous system setting modifications.
- **Foreground Service (specialUse)**: Essential for maintaining bandwidth control persistence across system lifecycle events on Android 14+.
- **ConnectivityManager API**: Used for real-time cellular and Wi-Fi state detection.

## Implementation Steps
**Total Duration:** 45m 12s

### Task_1_Initial_Setup_and_Core_Logic: Initialize the Android project with Jetpack Navigation 3, Compose Material Adaptive, and Coroutine dependencies. Implement the core BandwidthController logic using WRITE_SECURE_SETTINGS to set system bandwidth limits and a fail-safe reset function.
- **Status:** COMPLETED
- **Updates:** Initialized project with com.datathrottle namespace.
- **Acceptance Criteria:**
  - Project builds successfully
  - AndroidManifest.xml includes WRITE_SECURE_SETTINGS and FOREGROUND_SERVICE_SPECIAL_USE permissions
  - BandwidthController can apply limits and reset settings to -1
  - API_KEY integration is confirmed if applicable
- **Duration:** 31m 25s

### Task_2_Foreground_Service_and_Connectivity: Implement the ConnectivityManager listener to detect network transitions (Wi-Fi vs. Cellular). Create the 'specialUse' Foreground Service to manage background monitoring and bandwidth enforcement across the app lifecycle.
- **Status:** COMPLETED
- **Updates:** Implemented NetworkMonitor using registerDefaultNetworkCallback with validation checks.
- **Acceptance Criteria:**
  - Foreground Service starts with proper notification for specialUse type
  - Connectivity changes are accurately detected and logged
  - Bandwidth limits are automatically applied on cellular and removed on Wi-Fi
  - Service successfully resets settings on termination (Fail-Safe)
- **Duration:** 1m 23s

### Task_3_UI_and_Navigation: Develop the user interface using Jetpack Compose, Navigation 3, and Material Adaptive layouts. Create screens for bandwidth configuration, permission status monitoring, and an manual override toggle.
- **Status:** COMPLETED
- **Updates:** Implemented Jetpack Compose UI with Material 3 and Adaptive components.
- **Acceptance Criteria:**
  - UI displays real-time network status and permission state
  - Mbps limits are configurable via UI
  - App supports adaptive layouts for different screen sizes
  - Navigation 3 handles screen transitions correctly
  - App icon and basic styling are implemented
- **Duration:** 12m 24s

### Task_4_Run_and_Verify: Perform a final build and comprehensive verification of the application. Instruct critic_agent to verify application stability, requirement alignment, and UI fidelity.
- **Status:** IN_PROGRESS
- **Updates:** Performed a clean build as requested.
Implemented Shizuku support:
- Added Shizuku SDK dependencies.
- Created ShizukuManager for service check and permission granting.
- Updated UI to include a 'Grant via Shizuku' button and Shizuku status indicator.
- Permission granting via Shizuku's shell is now functional.
Ready for final verification on the physical device.
- **Acceptance Criteria:**
  - App does not crash during network switching or service restarts
  - Bandwidth throttling is verified on cellular data
  - System settings are correctly restored to default on app removal
  - Build pass
  - All existing tests pass
  - The implemented UI matches the design requirements
- **StartTime:** 2026-08-20 00:21:04 JST

