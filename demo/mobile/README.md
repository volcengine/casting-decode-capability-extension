# UPnP 移动端 Demo

这是 UPnP/DLNA 协议的移动端应用，包含 Android、iOS 和 HarmonyOS 的原生应用程序。

> **状态说明**：本仓库主推 **Android** Demo（已完整集成 libupnp，可真机运行）。**iOS、HarmonyOS 尚未开发完成**——目前仅有 UI 界面和硬编码的模拟数据，未接入 libupnp。

> **安全提示**：Android Demo 内置的本地 HTTP 文件服务（`LocalHttpServer`）为使局域网内的 DMR（电视等）拉流投屏，会监听全部网卡而非 `127.0.0.1`。**该服务仅作为演示 demo、接口未鉴权，请勿用于生产环境，也不要在此基础上扩大暴露面。**

## 目录结构

```
pupnp/mobile/
├── android/          # Android 应用
├── ios/            # iOS 应用
├── harmony/        # HarmonyOS 应用
├── build_android.sh
├── build_ios.sh
├── build_harmony.sh
├── build_all.sh
└── README.md
```

## 构建说明

### Android 应用

#### 方法一：使用命令行构建

```bash
cd pupnp/mobile/
chmod +x build_android.sh
./build_android.sh
```

#### 方法二：使用 Android Studio

1. 打开 Android Studio
2. 打开项目：`pupnp/mobile/android/`
3. 等待 Gradle 同步完成
4. 点击 Run 按钮

### iOS 应用

> ⚠️ **尚未开发完成**：当前仅为 Mock UI（`MainViewController.swift` 中点击"搜索"后返回硬编码的假设备），**未接入 libupnp**。以下构建步骤仅能跑起空壳 UI。

#### 方法一：使用命令行构建

```bash
cd pupnp/mobile/
chmod +x build_ios.sh
./build_ios.sh
```

#### 方法二：使用 Xcode

1. 打开 Xcode
2. 打开项目：`pupnp/mobile/ios/UpnpDemo/`
3. 选择运行目标设备或模拟器
4. 点击 Run 按钮

### HarmonyOS 应用

> ⚠️ **尚未开发完成**：当前仅为 Mock UI（`Index.ets` 中用 `setTimeout` 返回硬编码的假设备），**未接入 libupnp**。以下构建步骤仅能跑起空壳 UI。

#### 方法一：使用命令行构建

```bash
cd pupnp/mobile/
chmod +x build_harmony.sh
./build_harmony.sh
```

#### 方法二：使用 DevEco Studio

1. 打开 DevEco Studio
2. 打开项目：`pupnp/mobile/harmony/UpnpDemo/`
3. 等待依赖同步完成
4. 选择运行目标设备或模拟器
5. 点击 Run 按钮

### 统一构建所有平台

> 注意：`build_all.sh` 会尝试构建全部平台，但仅 Android 为可用产物；iOS、HarmonyOS 尚未开发完成，构建结果为不含 libupnp 的空壳 UI。

```bash
cd pupnp/mobile/
chmod +x build_all.sh
./build_all.sh
```

## 功能特性

### Android 应用（已实现）

- 搜索 UPnP 设备
- 创建媒体渲染器设备
- 显示设备信息
- 支持 Deviceability 扩展

### iOS 应用（未完成，以下为规划功能）

- 搜索 UPnP 设备
- 创建媒体渲染器设备
- 显示设备信息
- 支持 Deviceability 扩展

### HarmonyOS 应用（未完成，以下为规划功能）

- 搜索 UPnP 设备
- 创建媒体渲染器设备
- 显示设备信息
- 支持 Deviceability 扩展

## 集成 libupnp

### Android 集成

✅ **已完成**：通过 JNI（`src/main/cpp/upnp_android_jni.cpp`）集成 libupnp，Kotlin 侧经 `UpnpNative.kt` 调用。

### iOS 集成

❌ **待实现**：尚未将 libupnp 集成到 iOS 应用中，当前为 Mock UI。

### HarmonyOS 集成

❌ **待实现**：尚未将 libupnp 集成到 HarmonyOS 应用中，当前为 Mock UI。
