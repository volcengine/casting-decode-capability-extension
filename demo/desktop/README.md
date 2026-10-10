# UPnP 命令行 Demo

> ⚠️ **尚未开发完成**：本 CLI Demo（`src/upnp_demo.c`）使用了本版本 libupnp 已废弃或不存在的旧 API（`UpnpInit`、`UpnpClient_Create`、`UpnpClient_Destroy`、`Upnp_Discovery` 结构体、4 参数的 `UpnpRegisterClient`），**当前无法编译通过**，下方构建步骤会失败。仓库主推的完整 Demo 为 Android（见 `demo/mobile/android/`）。如需修复本 CLI，应改用新 API（`UpnpInit2` + `UpnpDiscovery_get_*` 访问器 + 3 参数 `UpnpRegisterClient`），可参考 `demo/desktop/macos_app/UpnpMacOSApp/UpnpMacOSApp/UpnpManager.m` 的正确用法。

这是一个跨平台的 UPnP/DLNA 命令行 demo 程序，支持 Windows、macOS 和 Linux。

## 目录结构

```
pupnp/cli/
├── CMakeLists.txt
├── build.sh          # macOS/Linux 构建脚本
├── build.bat         # Windows 构建脚本
├── src/
│   └── upnp_demo.c   # 命令行 demo 源码
└── README.md
```

## 功能特性（规划中，尚未可用）

- 搜索 UPnP/DLNA 设备
- 列出发现的设备
- 创建设备
- 命令行界面菜单

## 构建说明

### macOS / Linux

```bash
cd pupnp/cli/
chmod +x build.sh
./build.sh
```

### Windows

使用 NMake:
```batch
cd pupnp\cli\
build.bat
```

或使用 Visual Studio:
```batch
cd pupnp\cli\
build.bat
# 然后在 Visual Studio 中打开项目并编译
```

## 运行 Demo

### macOS / Linux

```bash
cd pupnp/cli/build/
./upnp_demo
```

### Windows

```batch
cd pupnp\cli\build\
upnp_demo.exe
```

## 使用方法

运行后会出现菜单：

```
===== UPnP Demo Menu =====
1. Search for UPnP devices
2. List discovered devices
3. Create media renderer device
4. Exit
=============================
Select an option:
```

选择相应的数字来执行操作。

## 注意

当前版本需要先在各自的平台上构建 libupnp 库后才能正常链接。

## 下一步计划

- 集成 libupnp 构建系统
- 完善设备控制功能
- 添加 Deviceability 扩展支持
