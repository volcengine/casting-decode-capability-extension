#!/bin/bash

# macOS UPnP App 构建脚本

echo "========================================="
echo "Building UPnP macOS App"
echo "========================================="

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR="${SCRIPT_DIR}/build"
APP_DIR="${BUILD_DIR}/UpnpMacOSApp.app"
SOURCE_DIR="${SCRIPT_DIR}/UpnpMacOSApp/UpnpMacOSApp"
PUPNP_DIR="${SCRIPT_DIR}/../../.."
PUPNP_BUILD="${PUPNP_DIR}/build_macos"

# 清理旧构建
rm -rf "${BUILD_DIR}"

# 创建构建目录
mkdir -p "${APP_DIR}/Contents/MacOS"
mkdir -p "${APP_DIR}/Contents/Resources"

# 复制 Info.plist
cp "${SOURCE_DIR}/Info.plist" "${APP_DIR}/Contents/"

cd "${SOURCE_DIR}"

mkdir -p "${BUILD_DIR}"

# 首先用 clang 编译 UpnpManager.m 为 .o 文件
clang -c UpnpManager.m \
    -o "${BUILD_DIR}/UpnpManager.o" \
    -I "${PUPNP_DIR}/upnp/inc" \
    -I "${PUPNP_DIR}/ixml/inc" \
    -I "${PUPNP_BUILD}/upnp/inc"

if [ $? -ne 0 ]; then
    echo "编译 UpnpManager.m 失败！"
    exit 1
fi

# 现在用 swiftc 编译 Swift 文件，链接上 .o 和库
swiftc -o "${APP_DIR}/Contents/MacOS/UpnpMacOSApp" \
    main.swift \
    AppDelegate.swift \
    ViewController.swift \
    UpnpManagerSwift.swift \
    "${BUILD_DIR}/UpnpManager.o" \
    -framework Cocoa \
    -L "${PUPNP_BUILD}/upnp" \
    -L "${PUPNP_BUILD}/ixml" \
    -lupnp \
    -lixml \
    -import-objc-header "UpnpMacOSApp-Bridging-Header.h"

# 复制 dylib 到 Frameworks 目录
mkdir -p "${APP_DIR}/Contents/Frameworks"
cp "${PUPNP_BUILD}/upnp/libupnp.20.0.4.dylib" "${APP_DIR}/Contents/Frameworks/libupnp.20.dylib"
cp "${PUPNP_BUILD}/ixml/libixml.11.1.8.dylib" "${APP_DIR}/Contents/Frameworks/libixml.11.dylib"

# 设置 rpath
install_name_tool -add_rpath @executable_path/../Frameworks "${APP_DIR}/Contents/MacOS/UpnpMacOSApp"

# 检查构建是否成功
if [ $? -eq 0 ]; then
    echo "========================================="
    echo "Build successful!"
    echo "App location: ${APP_DIR}"
    echo "========================================="
    echo "Opening app..."
    open "${APP_DIR}"
else
    echo "Build failed!"
    exit 1
fi
