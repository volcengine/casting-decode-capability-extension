#!/bin/bash

# iOS 应用构建脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/ios/UpnpDemo"

echo "========================================="
echo "Building iOS UPnP Demo"
echo "========================================="

cd "${PROJECT_DIR}"

# 检查是否安装了 Xcode command line tools
if ! xcode-select -p > /dev/null 2>&1; then
    echo "Error: Xcode command line tools not found"
    exit 1
fi

# 构建
xcodebuild -scheme UpnpDemo -sdk iphonesimulator -configuration Debug clean build

echo "========================================="
echo "Build completed!"
echo "Open the project in Xcode to install on device or simulator:"
echo "  open ${PROJECT_DIR}/UpnpDemo.xcodeproj"
