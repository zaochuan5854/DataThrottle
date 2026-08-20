# DataThrottle ⚡

<p align="center">
  <img src="artworks/screenshots/en/04_home_on.png" width="300" alt="DataThrottle - 1 Mbps Cellular Throttling" />
</p>

**A lightweight, rootless Android utility for precise cellular bandwidth throttling and mobile data conservation.**

<p align="center">
  🌐 <b>English</b> • <a href="README.ja.md">日本語</a> • <a href="README.zh.md">简体中文</a>
</p>

> ⚠️ **Note:** DataThrottle limits **download (ingress) speed only**. Upload speed is not affected or restricted in any way.

---

## 📌 Why DataThrottle?

### The Mobile Data Dilemma

Modern 4G and 5G networks are exceptionally fast, but that speed comes at a cost:

- **Aggressive data consumption** – Video platforms (YouTube, TikTok, Instagram Reels) default to 1080p or 4K streaming whenever bandwidth is unrestricted, burning through multi-gigabyte monthly allowances in a matter of hours.
- **Strict tiered plans and roaming** – Users on limited plans, MVNOs, or expensive international roaming can face sudden throttling or costly overage charges.
- **No native granular control** – Android doesn't offer a user-facing slider for capping download speed.

### Why Existing Solutions Fall Short

| Feature / Solution | Android's Built-in Data Saver | VPN-based Throttlers | Root / `iptables` / `tc` | **DataThrottle** |
| :--- | :---: | :---: | :---: | :---: |
| Limits foreground video / streaming | ❌ No (background only) | ⚠️ Yes | ✅ Yes | **✅ Yes — exact Mbps cap** |
| Rootless (no root required) | ✅ Yes | ✅ Yes | ❌ No | **✅ Yes — fully rootless** |
| Battery & CPU overhead | ✅ None | ⚠️ High (local packet loopback) | ✅ None | **✅ None — native kernel shaper** |
| Allows simultaneous real VPNs | ✅ Yes | ❌ No (blocks other VPNs) | ✅ Yes | **✅ Yes — compatible with all VPNs** |
| Automatic Wi-Fi passthrough | ❌ No | ⚠️ Manual toggling | ⚠️ Complex scripting | **✅ Automatic — cellular only** |


---

## 📸 Visual Demos

| One-Tap Toggle | 3D Drumroll Bandwidth Picker | 100 kbps Diagnostic Test |
| :---: | :---: | :---: |
| ![Toggle Switch](artworks/videos/01_toggle_switch.gif) | ![Drumroll Picker](artworks/videos/02_drumroll_picker.gif) | ![100 kbps Test](artworks/videos/03_100k_test.gif) |
| *Instantly enable or disable throttling* | *Smooth 3D drumroll speed selector* | *Live scanline stream verification* |

---

## 🌟 Key Features

- 🎯 **Fine-grained ingress (download) rate limiting** — DataThrottle caps *download* speed only; upload speed is never throttled.
  - **0.1–1.0 Mbps** in 0.1 Mbps steps — aggressive data saving and low-speed network simulation.
  - **1.5–10.0 Mbps** in 0.5 Mbps steps — smooth streaming caps for 480p, 720p, or 1080p playback.
  - **11–50 Mbps** in 1.0 Mbps steps — high-throughput caps.
- 📶 **Intelligent cellular-only throttling** — continuously monitors connectivity and applies the limit only on cellular data, automatically standing down to unlimited speed on Wi-Fi.
- 🔋 **Zero VPN overhead** — uses Android's native traffic shaper (`Settings.Global.ingress_rate_limit_bytes_per_second`) instead of a local VPN loop, so there's no extra battery drain and no DNS rerouting.
- 🛡️ **Rootless architecture** — a one-time permission grant via **[Shizuku](https://github.com/RikkaApps/Shizuku/)**, entirely on-device, or via standard ADB from a computer.
- 🎛️ **Quick Settings tile** — toggle throttling directly from the notification shade.
- 🧪 **Built-in 100 kbps diagnostic test** — a live, progressive scanline image download that verifies throttling accuracy and reports the real measured bitrate.
- 🎨 **Modern Material 3 UI** — built with Jetpack Compose, with a smooth 3D scroll picker, full dark/light theme support, and localization for English, Japanese, and Chinese.
- 🧹 **Safe reset & uninstall** — a built-in action restores all original Android system network values before you remove the app.

---

## 🚀 Getting Started & Permissions Setup

Because Android protects its system-level rate-limiting settings, DataThrottle needs **three permissions** on first launch:

| # | Purpose | Permission |
| :---: | :--- | :--- |
| 1 | Bandwidth control | `WRITE_SECURE_SETTINGS` |
| 2 | Status notification | `POST_NOTIFICATIONS` (Android 13+) |
| 3 | Background execution | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

### Step 1: Grant Bandwidth Control (`WRITE_SECURE_SETTINGS`)

**Option A — Shizuku (recommended, no PC needed)**
1. Install and open **[Shizuku](https://github.com/RikkaApps/Shizuku/releases)** (available on GitHub).
2. Start Shizuku using Wireless Debugging on your phone.
3. Open **DataThrottle** and tap **Grant with One Tap** on the setup screen.

**Option B — ADB via computer (for developers)**
1. Enable **Developer Options** and **USB Debugging** on your device.
2. Connect the device to your computer via USB or wireless ADB.
3. Run:
   ```bash
   adb shell pm grant com.datathrottle android.permission.WRITE_SECURE_SETTINGS
   ```
4. Tap **Check Permission** in DataThrottle to confirm.

### Step 2: Enable Notifications & Battery Exemption

- **Status notification** — tap **Allow Notifications** to show real-time status and keep the foreground service stable.
- **Background execution** — tap **Exclude from Optimization** so OEM battery managers don't kill the service when the screen is off.

---

## 📖 How to Use

1. **Choose your speed** — scroll the 3D drumroll picker to select a bandwidth limit (e.g., `1.0 Mbps`).
2. **Turn on the service** — flip the toggle switch to **ON**.
3. **Automatic behavior**
   - On cellular: bandwidth is capped at your chosen limit.
   - On Wi-Fi: the service stays in **standby (unlimited)** mode.
4. **Quick Settings** — add the DataThrottle tile to your quick settings panel for one-tap toggling.

---

## 🧪 Diagnostic Speed Verification

To confirm that rate limiting is working correctly on your device:
1. Open **Settings** (gear icon) → **100 kbps Test**.
2. Tap **Start 100 kbps Test**.
3. The app temporarily applies a strict 100 kbps limit and streams a diagnostic image line by line, letting you verify that measured throughput matches the configured limit.

---

## 🧹 Safe Reset & Clean Uninstall

Before uninstalling DataThrottle, it's a good idea to restore Android's network limits to their defaults:
1. Open **Settings** within DataThrottle.
2. Tap **Reset & Prepare for Uninstall**.
3. Confirm the dialog. This resets `ingress_rate_limit_bytes_per_second` back to `-1` (unlimited) and clears the service configuration.

---

## 🛠️ Technical Deep-Dive: How It Works

### Why Kernel-Level Traffic Shaping?

Most third-party bandwidth managers work by creating a local `VpnService` tunnel. While that approach doesn't require special permissions, it comes with real drawbacks:
- Every packet is routed through user space, adding noticeable CPU overhead and battery drain.
- It interferes with real VPNs (corporate VPNs, WireGuard, Tailscale, AdGuard, etc.).
- It can introduce extra latency and packet loss.

DataThrottle instead talks directly to Android's system network limiter:

```kotlin
Settings.Global.putLong(
    contentResolver,
    "ingress_rate_limit_bytes_per_second",
    bytesPerSecond
)
```

- **Direct kernel enforcement** — enforced by the Linux kernel's `tc` (traffic control) / eBPF **ingress** subsystem, which governs incoming (download) traffic only. Outgoing (upload) traffic is left untouched.
- **Zero latency and battery impact** — packets are shaped in-kernel, with no user-space interception.
- **Dynamic connectivity awareness** — a foreground service listens for network state changes via `ConnectivityManager.NetworkCallback`, so limits are applied only while actively on cellular.

---

## 💻 Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 34 (Android 14)

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/zaochuan5854/DataThrottle.git
cd DataThrottle

# Build a debug APK
./gradlew assembleDebug

# Run unit & instrumentation tests
./gradlew test
```
