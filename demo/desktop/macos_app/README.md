# UPnP macOS App

这是一个带 UI 的 macOS 应用程序，用于搜索和显示 UPnP/DLNA 设备。

## 🚀 快速开始

### 构建并运行

```bash
cd pupnp/demo/desktop/macos_app/
chmod +x build.sh
./build.sh
```

### 应用位置

构建后的应用程序位于：
`pupnp/demo/desktop/macos_app/build/UpnpMacOSApp.app`

## 📋 功能特性

- **搜索设备**：点击 "Search Devices" 按钮搜索 UPnP/DLNA 设备
- **设备列表**：显示发现的设备的名称、类型和位置
- **刷新列表**：点击 "Refresh" 按钮清空并刷新设备列表

## 📁 项目结构

```
macos_app/
├── UpnpMacOSApp/
│   └── UpnpMacOSApp/
│       ├── main.swift
│       ├── AppDelegate.swift
│       ├── ViewController.swift
│       └── Info.plist
├── build.sh
└── README.md
```

## 📝 注意事项

当前版本使用模拟设备数据。后续可以集成 libupnp 库实现真实的设备搜索功能。

## 🔧 后续开发计划

1. 集成 libupnp 库，实现真实的 UPnP 设备搜索
2. 添加设备详细信息显示
3. 实现设备控制功能
4. 添加 Deviceability 扩展支持
