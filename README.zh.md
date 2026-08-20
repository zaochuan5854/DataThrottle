# DataThrottle ⚡

<p align="center">
  <img src="artworks/screenshots/en/04_home_on.png" width="300" alt="DataThrottle - 1 Mbps Cellular Throttling" />
</p>

**一款轻量、无需 Root 的 Android 工具，用于精确限制蜂窝网络带宽并节省移动数据。**

<p align="center">
  🌐 <a href="README.md">English</a> • <a href="README.ja.md">日本語</a> • <b>简体中文</b>
</p>

> ⚠️ **注意：** DataThrottle 仅限制 **下载（下行）速度** 。上传速度不会受到任何影响或限制。

---

## 📌 为什么选择 DataThrottle？

### 移动数据的困境

现代 4G 和 5G 网络速度极快，但高网速也带来了一些代价：

- **爆发式的数据消耗** – 视频平台（YouTube、TikTok、Instagram Reels）在网络未受限时默认使用 1080p 或 4K 流媒体播放，这会在短短几个小时内耗尽数吉字节（GB）的月度套餐流量。
- **严格的阶梯套餐与漫游** – 使用限额套餐、国内达量限速手机卡，或高昂的国际漫游时，用户可能会面临突如其来的限速或高额超额费用。
- **系统缺乏原生精细化控制** – Android 系统没有向用户提供用于限制下载速度的原生滑动条。

### 为什么现有解决方案不尽如人意

| 功能 / 解决方案 | Android 内置省流量模式 | 基于 VPN 的限速工具 | Root / `iptables` / `tc` | **DataThrottle** |
| :--- | :---: | :---: | :---: | :---: |
| 限制前台视频/流媒体 | ❌ 否（仅限后台） | ⚠️ 是 | ✅ 是 | **✅ 是（精确的 Mbps 限制）** |
| 无需 Root | ✅ 是 | ✅ 是 | ❌ 否 | **✅ 是（完全免 Root）** |
| 电池与 CPU 开销 | ✅ 无 | ⚠️ 高（本地数据包回环） | ✅ 无 | **✅ 无（原生内核整形器）** |
| 允许同时使用真实的 VPN | ✅ 是 | ❌ 否（会阻断其他 VPN） | ✅ 是 | **✅ 是（兼容所有 VPN）** |
| Wi-Fi 自动放行（免限制） | ❌ 否 | ⚠️ 需手动切换 | ⚠️ 复杂脚本 | **✅ 自动（仅限蜂窝网络）** |


---

## 📸 视觉效果演示

| 一键开关 | 3D 滚轮带宽选择器 | 100 kbps 诊断测试 |
| :---: | :---: | :---: |
| ![Toggle Switch](artworks/videos/01_toggle_switch.gif) | ![Drumroll Picker](artworks/videos/02_drumroll_picker.gif) | ![100 kbps Test](artworks/videos/03_100k_test.gif) |
| *即时开启或关闭限速* | *平滑的 3D 滚轮速度选择器* | *实时扫描线图像下载验证* |

---

## 🌟 主要特性

- 🎯 **细粒度的下行（下载）速率限制** — DataThrottle 仅限制*下载*速度，上传速度永远不会受限。
  - **0.1–1.0 Mbps** ，步进为 0.1 Mbps — 适用于极度节省数据或低速网络模拟。
  - **1.5–10.0 Mbps** ，步进为 0.5 Mbps — 适用于 480p、720p 或 1080p 画质的平滑流媒体限制。
  - **11–50 Mbps** ，步进为 1.0 Mbps — 高吞吐量限制。
- 📶 **智能蜂窝网络专属限速** — 持续监测连接状态，仅在蜂窝数据网络下应用限制，连接到 Wi-Fi 时会自动恢复到无限制状态。
- 🔋 **零 VPN 开销** — 使用 Android 原生流量整形器（`Settings.Global.ingress_rate_limit_bytes_per_second`），而不是本地 VPN 回环，因此不会产生额外的电池消耗，也无需重新路由 DNS。
- 🛡️ **免 Root 架构** — 在首次运行时，通过 **[Shizuku](https://github.com/RikkaApps/Shizuku/)** （完全在设备上运行）或通过电脑上的标准 ADB 命令，授予一次性权限即可。
- 🎛️ **快捷设置瓷贴** — 直接从通知栏快捷开关限速。
- 🧪 **内置 100 kbps 诊断测试** — 采用实时、逐行下载扫描线图像的方式来验证限速的准确性，并报告实际测量到的比特率。
- 🎨 **现代 Material 3 UI** — 采用 Jetpack Compose 构建，支持流畅的 3D 滚轮选择器、完整的深色/浅色主题，并提供英文、日文和中文的本地化支持。
- 🧹 **安全重置与卸载** — 内置恢复功能，可在卸载应用前恢复所有原始的 Android 系统网络参数。

---

## 🚀 开始使用与权限配置

由于 Android 系统保护了其系统级的限速设置，DataThrottle 在首次启动时需要被授予 **三项权限** ：

| 编号 | 用途 | 权限 |
| :---: | :--- | :--- |
| 1 | 带宽控制 | `WRITE_SECURE_SETTINGS` |
| 2 | 状态通知 | `POST_NOTIFICATIONS` (Android 13+) |
| 3 | 后台执行 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

### 步骤 1：授予带宽控制权限 (`WRITE_SECURE_SETTINGS`)

**方案 A — Shizuku（推荐，无需电脑）**
1. 安装并打开 **[Shizuku](https://github.com/RikkaApps/Shizuku/releases)** （可在 GitHub 获取）。
2. 在手机上通过“无线调试”启动 Shizuku。
3. 打开 **DataThrottle** ，在配置界面点击 **一键授权 (Grant with One Tap)** 。

**方案 B — 电脑 ADB 授权（适合开发者）**
1. 在设备上启用 **开发者选项** 和 **USB 调试** 。
2. 通过 USB 或无线 ADB 将设备连接到电脑。
3. 运行以下命令：
   ```bash
   adb shell pm grant com.datathrottle android.permission.WRITE_SECURE_SETTINGS
   ```
4. 在 DataThrottle 中点击 **检查权限 (Check Permission)** 进行确认。

### 步骤 2：启用通知与免除电池优化

- **状态通知** — 点击 **允许通知** 以显示实时状态并保持前台服务的稳定运行。
- **后台执行** — 点击 **免除电池优化** ，以防止 OEM 厂商的电池管理器在屏幕关闭时强制杀掉该服务。

---

## 📖 使用方法

1. **选择速度** — 滚动 3D 滚轮选择器来选择一个带宽上限（例如 `1.0 Mbps`）。
2. **启动服务** — 将开关切换至 **ON** 。
3. **自动工作机制**
   - 在蜂窝网络下：下载速度将被限制在您所设定的上限。
   - 在 Wi-Fi 网络下：服务将处于 **待机（不限制）** 状态。
4. **快捷设置** — 将 DataThrottle 瓷贴添加到您的快捷设置面板中，以便一键切换。

---

## 🧪 诊断速度验证

要确认限速功能是否在您的设备上正常工作：
1. 打开 **设置** （齿轮图标） → **100 kbps 测试** 。
2. 点击 **开始 100 kbps 测试** 。
3. 应用会临时应用严格的 100 kbps 限制，并逐行流式下载一张诊断图像，从而让您验证实测吞吐量是否符合配置的限制。

---

## 🧹 安全重置与干净卸载

在卸载 DataThrottle 之前，建议先将 Android 的网络限制恢复为默认值：
1. 打开 DataThrottle 中的 **设置** 。
2. 点击 **重置并准备卸载** 。
3. 确认对话框。这会将 `ingress_rate_limit_bytes_per_second` 恢复为 `-1`（无限制）并清除服务配置。

---

## 🛠️ 技术深度探究：工作原理

### 为什么选择内核级流量整形？

大多数第三方带宽管理工具是通过创建本地 `VpnService` 隧道来工作的。虽然这种方法不需要特殊权限，但它带有明显的弊端：
- 每个数据包都需要经过用户空间路由，这会带来明显的 CPU 开销和电池消耗。
- 它会干扰真正的 VPN（公司 VPN、WireGuard、Tailscale、AdGuard 等），导致无法同时使用。
- 可能会引入额外的延迟和丢包。

DataThrottle 则是直接与 Android 系统的网络限制器进行通信：

```kotlin
Settings.Global.putLong(
    contentResolver,
    "ingress_rate_limit_bytes_per_second",
    bytesPerSecond
)
```

- **内核级直接执行** — 由 Linux 内核的 `tc` (traffic control) / eBPF 的 **ingress（输入）** 子系统直接强制执行。该子系统仅管理接收（下载）流量，发送（上传）流量保持原样不受干扰。
- **零延迟与零电池消耗** — 数据包直接在内核中进行整形，没有用户空间拦截，因此对设备功耗无附加影响。
- **动态网络状态感知** — 前台服务通过 `ConnectivityManager.NetworkCallback` 监听网络状态的变化，因此只有在活动连接为蜂窝网络时才会应用限制。

---

## 💻 源码构建

### 前提条件

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 34 (Android 14)

### 克隆与构建

```bash
# 克隆仓库
git clone https://github.com/zaochuan5854/DataThrottle.git
cd DataThrottle

# 构建调试版 APK
./gradlew assembleDebug

# 运行单元测试与仪器测试
./gradlew test
```
