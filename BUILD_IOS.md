# pupnp iOS 构建说明

## 概述

本项目提供了为 pupnp 库进行 iOS 交叉编译的支持。虽然 pupnp 最初主要为桌面和 Android 平台设计，但其代码库已经包含了一些 Apple 平台特定的适配，因此可以通过 CMake 工具链为 iOS 构建。

## 前置条件

- macOS 10.15 或更高版本
- Xcode 12 或更高版本
- Command Line Tools for Xcode
- CMake 3.11 或更高版本

安装 Command Line Tools：
```bash
xcode-select --install
```

## 构建方法

### 方法一：使用构建脚本（推荐）

项目提供了一键构建脚本 `build_ios.sh`：

```bash
cd /path/to/pupnp
chmod +x build_ios.sh
./build_ios.sh
```

这将会：
1. 为 arm64（真机）和 x86_64（模拟器）分别构建
2. 创建统一的静态库
3. 输出到 `build_ios/` 目录

### 方法二：手动 CMake 构建

#### 1. 为真机构建（arm64）

```bash
mkdir -p build_ios/arm64
cd build_ios/arm64

cmake \
    -DCMAKE_TOOLCHAIN_FILE=../../cmake/ios.toolchain.cmake \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_BUILD_TYPE=Release \
    -DUPNP_BUILD_SHARED=OFF \
    -DUPNP_BUILD_STATIC=ON \
    -DUPNP_BUILD_SAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    ../..

make -j$(sysctl -n hw.ncpu)
```

#### 2. 为模拟器构建（x86_64）

```bash
mkdir -p build_ios/x86_64
cd build_ios/x86_64

cmake \
    -DCMAKE_TOOLCHAIN_FILE=../../cmake/ios.toolchain.cmake \
    -DCMAKE_OSX_ARCHITECTURES=x86_64 \
    -DCMAKE_BUILD_TYPE=Release \
    -DUPNP_BUILD_SHARED=OFF \
    -DUPNP_BUILD_STATIC=ON \
    -DUPNP_BUILD_SAMPLES=OFF \
    -DBUILD_TESTING=OFF \
    ../..

make -j$(sysctl -n hw.ncpu)
```

#### 3. 创建统一静态库

```bash
lipo -create \
    build_ios/arm64/upnp/libupnp.a \
    build_ios/x86_64/upnp/libupnp.a \
    -output build_ios/libupnp.a

lipo -create \
    build_ios/arm64/ixml/libixml.a \
    build_ios/x86_64/ixml/libixml.a \
    -output build_ios/libixml.a
```

## 集成到 iOS 项目

### 1. 导入静态库

将以下文件添加到你的 Xcode 项目：
- `build_ios/universal/libupnp.a`
- `build_ios/universal/libixml.a`

### 2. 导入头文件

将以下头文件目录添加到项目的 Header Search Paths：
- `pupnp/upnp/inc`
- `pupnp/ixml/inc`

### 3. 链接必要的系统框架

在 Xcode 项目的 "Build Phases" -> "Link Binary With Libraries" 中添加：
- `libresolv.tbd` (用于 DNS 解析)
- `libSystem.tbd` (系统库)
- `Foundation.framework` (可选，用于某些适配)

## 注意事项

### iOS 网络权限

在 iOS 上使用 pupnp 需要配置网络权限：

1. 在 `Info.plist` 中添加：
```xml
<key>NSLocalNetworkUsageDescription</key>
<string>需要访问本地网络以发现和控制 UPnP 设备</string>
```

2. 对于 iOS 14+，还需要配置多播权限（如果需要）

### 线程和网络适配

iOS 对网络和线程有一些特定限制：
- pupnp 使用的 POSIX 线程在 iOS 上支持良好
- 需要注意后台运行限制
- 网络操作应在主线程之外进行

### 已有平台适配

pupnp 代码库中已包含以下 Apple 平台适配：
- `ssdplib.h` - 排除了 Apple 平台不需要的特定头文件
- 其他文件中的 `__APPLE__` 条件编译

## 与 Platinum 的对比

如果你的项目同时考虑 Platinum：

- **Platinum**: 原生支持 iOS，有 Carthage 集成，有 Objective-C 绑定
- **pupnp**: 需要交叉编译，更轻量，API 更底层

## 故障排除

### 编译错误：找不到头文件

确保 CMake 配置中的头文件路径正确，并检查 CMake 生成的 `upnpconfig.h` 是否在正确位置。

### 链接错误：未定义的符号

确保链接了所有必要的静态库（libupnp.a 和 libixml.a），以及系统库 libresolv。

### 运行时错误：网络操作失败

检查 iOS 的网络权限配置，确保 Info.plist 中有正确的权限描述。

## 参考资源

- [pupnp GitHub](https://github.com/pupnp/pupnp)
- [CMake iOS Toolchain](https://cmake.org/cmake/help/latest/manual/cmake-toolchains.7.html#cross-compiling-for-ios-tvos-or-watchos)
- [Platinum UPnP](https://github.com/plutinosoft/Platinum) - 另一个支持 iOS 的 UPnP 库
