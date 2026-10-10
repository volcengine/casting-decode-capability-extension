#!/bin/bash

# pupnp 鸿蒙平台构建脚本
# 注意：需要鸿蒙 SDK 环境

set -e

# 配置
SRC_DIR=$(cd "$(dirname "$0")" && pwd)
BUILD_DIR="${SRC_DIR}/build_harmony"
OUTPUT_DIR="${SRC_DIR}/output_harmony"

# 鸿蒙 SDK 路径（需要根据实际情况修改）
HARMONY_SDK_PATH="/path/to/harmony/sdk"
HARMONY_NDK_PATH="${HARMONY_SDK_PATH}/native/llvm"

# 创建目录
mkdir -p "${BUILD_DIR}"
mkdir -p "${OUTPUT_DIR}"

cd "${BUILD_DIR}"
echo "========================================="
echo "Building for HarmonyOS"
echo "========================================="

# 清理旧的构建文件
rm -rf *

# 运行 CMake
cmake \
    -DCMAKE_TOOLCHAIN_FILE=${HARMONY_NDK_PATH}/build/cmake/harmony.toolchain.cmake \
    -DOHOS_ARCH=arm64-v8a \
    -DOHOS_PLATFORM=OHOS \
    -DOHOS_API_LEVEL=9 \
    -DUPNP_BUILD_SHARED=ON \
    -DUPNP_BUILD_STATIC=ON \
    -DUPNP_BUILD_SAMPLES=ON \
    -DBUILD_TESTING=ON \
    -DCMAKE_BUILD_TYPE=Release \
    "${SRC_DIR}"

# 编译
make -j4

# 拷贝输出文件
cp upnp/libupnp.so "${OUTPUT_DIR}/"
cp ixml/libixml.so "${OUTPUT_DIR}/"
cp upnp/libupnp.a "${OUTPUT_DIR}/"
cp ixml/libixml.a "${OUTPUT_DIR}/"

# 拷贝头文件
mkdir -p "${OUTPUT_DIR}/include/upnp"
mkdir -p "${OUTPUT_DIR}/include/ixml"
cp -r "${SRC_DIR}/upnp/inc/"* "${OUTPUT_DIR}/include/upnp/"
cp -r "${SRC_DIR}/ixml/inc/"* "${OUTPUT_DIR}/include/ixml/"
cp "${BUILD_DIR}/upnp/inc/upnpconfig.h" "${OUTPUT_DIR}/include/upnp/"

# 拷贝示例程序
mkdir -p "${OUTPUT_DIR}/samples"
cp upnp/sample/tv_ctrlpt/tv_ctrlpt "${OUTPUT_DIR}/samples/"
cp upnp/sample/tv_device/tv_device "${OUTPUT_DIR}/samples/"
cp upnp/sample/tv_combo/tv_combo "${OUTPUT_DIR}/samples/"

echo "========================================="
echo "Build completed!"
echo "Output: ${OUTPUT_DIR}"
ls -lh "${OUTPUT_DIR}"
