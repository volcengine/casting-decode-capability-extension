#!/bin/bash

# 构建所有移动端应用

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================="
echo "Building UPnP Mobile Demos"
echo "========================================="

# 构建 Android 应用
echo
echo "Building Android demo..."
cd "${SCRIPT_DIR}"
chmod +x build_android.sh
./build_android.sh

# 构建 iOS 应用（仅在 macOS 上）
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo
    echo "Building iOS demo..."
    cd "${SCRIPT_DIR}"
    chmod +x build_ios.sh
    ./build_ios.sh
else
    echo
    echo "iOS build skipped (not on macOS)"
fi

# 构建 HarmonyOS 应用
echo
echo "Building HarmonyOS demo..."
cd "${SCRIPT_DIR}"
chmod +x build_harmony.sh
./build_harmony.sh

echo
echo "========================================="
echo "All builds completed!"
echo "========================================="
