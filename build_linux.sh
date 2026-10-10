#!/bin/bash

# pupnp Linux 构建脚本

set -e

# 配置
SRC_DIR=$(cd "$(dirname "$0")" && pwd)
BUILD_DIR="${SRC_DIR}/build_linux"
OUTPUT_DIR="${SRC_DIR}/output_linux"

# 创建目录
mkdir -p "${BUILD_DIR}"
mkdir -p "${OUTPUT_DIR}"

cd "${BUILD_DIR}"
echo "========================================="
echo "Building for Linux"
echo "========================================="

# 清理旧的构建文件
rm -rf *

# 运行 CMake
cmake \
    -DUPNP_BUILD_SHARED=ON \
    -DUPNP_BUILD_STATIC=ON \
    -DUPNP_BUILD_SAMPLES=ON \
    -DBUILD_TESTING=ON \
    -DCMAKE_BUILD_TYPE=Release \
    "${SRC_DIR}"

# 编译
make -j$(nproc)

# 安装（可选）
# sudo make install

# 拷贝输出文件
cp upnp/libupnp.so* "${OUTPUT_DIR}/"
cp ixml/libixml.so* "${OUTPUT_DIR}/"
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
