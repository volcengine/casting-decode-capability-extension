#!/bin/bash

# pupnp iOS 简化构建脚本
# 参考 macOS 成功构建的方式

set -e

# 配置
SRC_DIR=$(cd "$(dirname "$0")" && pwd)
OUTPUT_DIR="${SRC_DIR}/build_ios"
MIN_IOS_VERSION="12.0"
PUPNP_VERSION="1.18.4"

# 目标架构
ARCHS=("arm64" "x86_64")

# 创建输出目录
mkdir -p "${OUTPUT_DIR}"

for ARCH in "${ARCHS[@]}"; do
    echo "========================================="
    echo "Building for ${ARCH}"
    echo "========================================="

    BUILD_DIR="${OUTPUT_DIR}/build_${ARCH}"
    mkdir -p "${BUILD_DIR}"
    cd "${BUILD_DIR}"

    # 确定平台和 SDK
    if [ "${ARCH}" = "arm64" ]; then
        PLATFORM="OS"
        SDK="iphoneos"
    else
        PLATFORM="SIMULATOR"
        SDK="iphonesimulator"
    fi

    SDK_PATH=$(xcrun --sdk ${SDK} --show-sdk-path)
    echo "Using SDK: ${SDK_PATH}"

    # 清理并运行 CMake
    rm -rf *

    cmake \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_OSX_ARCHITECTURES="${ARCH}" \
        -DCMAKE_OSX_SYSROOT="${SDK_PATH}" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="${MIN_IOS_VERSION}" \
        -DSTATIC_POSTFIX="" \
        -DUPNP_BUILD_SHARED=OFF \
        -DUPNP_BUILD_STATIC=ON \
        -DUPNP_BUILD_SAMPLES=OFF \
        -DBUILD_TESTING=OFF \
        -DCMAKE_BUILD_TYPE=Release \
        "${SRC_DIR}"

    # 编译
    make -j$(sysctl -n hw.ncpu) VERBOSE=1

    echo "Finished ${ARCH}"
    cd "${SRC_DIR}"
done

# 创建统一库
echo "========================================="
echo "Creating universal library"
echo "========================================="

UNIVERSAL_DIR="${OUTPUT_DIR}/universal"
mkdir -p "${UNIVERSAL_DIR}"

# 合并库
lipo -create \
    "${OUTPUT_DIR}/build_arm64/upnp/libupnp.a" \
    "${OUTPUT_DIR}/build_x86_64/upnp/libupnp.a" \
    -output "${UNIVERSAL_DIR}/libupnp.a"

lipo -create \
    "${OUTPUT_DIR}/build_arm64/ixml/libixml.a" \
    "${OUTPUT_DIR}/build_x86_64/ixml/libixml.a" \
    -output "${UNIVERSAL_DIR}/libixml.a"

# 复制头文件
mkdir -p "${UNIVERSAL_DIR}/include/upnp"
mkdir -p "${UNIVERSAL_DIR}/include/ixml"

cp -r "${SRC_DIR}/upnp/inc/"* "${UNIVERSAL_DIR}/include/upnp/"
cp -r "${SRC_DIR}/ixml/inc/"* "${UNIVERSAL_DIR}/include/ixml/"

# 复制生成的 upnpconfig.h
if [ -f "${OUTPUT_DIR}/build_arm64/upnp/inc/upnpconfig.h" ]; then
    cp "${OUTPUT_DIR}/build_arm64/upnp/inc/upnpconfig.h" "${UNIVERSAL_DIR}/include/upnp/"
fi

echo "========================================="
echo "Build completed!"
echo "Output: ${UNIVERSAL_DIR}"
ls -lh "${UNIVERSAL_DIR}"
