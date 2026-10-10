# Demo 构建与使用指南

本目录包含投屏设备解码能力扩展的跨平台示例 Demo。项目背景、目的、依赖与整体平台状态请见[根目录 README](../README.md)；本文只介绍各平台的目录结构与构建/运行方式。

> 主推 **Android**（已完整集成 libupnp，可真机运行）；桌面端、iOS、HarmonyOS 仅供参考，尚未开发完成。

## 目录结构

```
demo/
├── desktop/          # 桌面端命令行 demo
│   ├── CMakeLists.txt
│   ├── build.sh
│   ├── build.bat
│   ├── src/upnp_demo.c
│   └── README.md
└── mobile/           # 移动端应用 demo
    ├── android/      # Android 应用
    ├── ios/          # iOS 应用
    ├── harmony/      # HarmonyOS 应用
    ├── build_android.sh
    ├── build_ios.sh
    ├── build_harmony.sh
    ├── build_all.sh
    └── README.md
```

## 移动端 Demo（mobile/）

### Android（推荐）

```bash
cd demo/mobile/
chmod +x build_android.sh
./build_android.sh
```

或用 Android Studio 打开 `demo/mobile/android/` 直接运行。各平台细节见 [`mobile/README.md`](mobile/README.md)。

## 桌面端 Demo（desktop/）

> ⚠️ 桌面 CLI 当前使用了本版本 libupnp 已废弃的 API，无法编译通过；macOS GUI app 依赖预先构建的 `build_macos/`，尚未验证可运行。仅供参考，详情见 [`desktop/README.md`](desktop/README.md)。

```bash
cd demo/desktop/
chmod +x build.sh
./build.sh
./build/upnp_demo
```

Windows：

```batch
cd demo\desktop\
build.bat
build\upnp_demo.exe
```

## 开发环境

| 平台 | IDE | 路径 |
|------|-----|------|
| 桌面端 | 任意编辑器 + CMake | `demo/desktop/` |
| Android | Android Studio | `demo/mobile/android/` |
| iOS | Xcode | `demo/mobile/ios/UpnpDemo/` |
| HarmonyOS | DevEco Studio | `demo/mobile/harmony/UpnpDemo/` |

## 安全提示

Android Demo 内置的本地 HTTP 文件服务为使局域网内的 DMR（电视等）拉流投屏，会监听全部网卡而非 `127.0.0.1`。**该服务仅作为演示 demo、接口未鉴权，请勿用于生产环境，也不要在此基础上扩大暴露面。**
