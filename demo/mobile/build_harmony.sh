#!/bin/bash

# 鸿蒙应用构建脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/harmony/UpnpDemo"

echo "========================================="
echo "Building HarmonyOS UPnP Demo"
echo "========================================="

cd "${PROJECT_DIR}"

# 检查是否有 HarmonyOS 开发环境
if ! command -v hvigor &> /dev/null; then
    echo "Error: HarmonyOS SDK (hvigor) not found"
    echo "Please install HarmonyOS DevEco Studio"
    echo "Project location: ${PROJECT_DIR}"
    exit 1
fi

# 构建
echo "Building with hvigor..."
hvigorw clean
hvigorw assembleHap

echo "========================================="
echo "Build completed!"
echo "HAP location: ${PROJECT_DIR}/entry/build/default/outputs/default/entry-default-signed.hap"
echo "Open project in DevEco Studio for development:"
echo "  ${PROJECT_DIR}"
