#!/bin/bash

# Android 应用构建脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/android"

echo "========================================="
echo "Building Android UPnP Demo"
echo "========================================="

cd "${PROJECT_DIR}"

# 检查是否安装了 Android SDK
if [ -z "${ANDROID_HOME}" ] && [ -z "${ANDROID_SDK_ROOT}" ]; then
    echo "Error: ANDROID_HOME or ANDROID_SDK_ROOT is not set"
    exit 1
fi

# 构建
if [ -f "gradlew" ]; then
    chmod +x gradlew
    ./gradlew assembleDebug
else
    echo "gradlew not found. Building with Android Studio is recommended."
    echo "Open ${PROJECT_DIR} in Android Studio"
fi

echo "========================================="
echo "Build completed!"
echo "APK location: ${PROJECT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
