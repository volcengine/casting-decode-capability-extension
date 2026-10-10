#!/bin/bash

# 设置 NDK 和 CMake 路径
NDK_PATH=~/Library/Android/sdk/ndk/21.1.6352462
CMAKE_PATH=~/Library/Android/sdk/cmake/3.18.1/bin/cmake

# 目标架构列表
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

# 平台版本
PLATFORM="android-21"

# 源码目录 (假设脚本在 pupnp 目录下运行)
SRC_DIR=$(pwd)
OUTPUT_DIR="${SRC_DIR}/output_android"

mkdir -p ${OUTPUT_DIR}

for ABI in "${ABIS[@]}"; do
    echo "========================================="
    echo "Building for architecture: ${ABI}"
    echo "========================================="
    
    BUILD_DIR="${SRC_DIR}/build_android/build_${ABI}"
    mkdir -p ${BUILD_DIR}
    cd ${BUILD_DIR}
    
    # 清理旧的构建文件
    rm -rf *
    
    # 运行 CMake
    ${CMAKE_PATH} \
        -DCMAKE_TOOLCHAIN_FILE=${NDK_PATH}/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=${ABI} \
        -DANDROID_PLATFORM=${PLATFORM} \
        -DUPNP_ENABLE_DEVICE_API=OFF \
        -DUPNP_ENABLE_WEBSERVER=OFF \
        -DUPNP_BUILD_SAMPLES=OFF \
        -DBUILD_TESTING=OFF \
        -DCMAKE_BUILD_TYPE=Release \
        ${SRC_DIR}
        
    # 编译
    make -j4
    
    # 创建输出目录
    ABI_OUT_DIR="${OUTPUT_DIR}/${ABI}"
    mkdir -p ${ABI_OUT_DIR}
    
    # 拷贝并 strip 动态库
    cp upnp/libupnp.so ${ABI_OUT_DIR}/
    cp ixml/libixml.so ${ABI_OUT_DIR}/
    
    ${NDK_PATH}/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip ${ABI_OUT_DIR}/libupnp.so ${ABI_OUT_DIR}/libixml.so
    
    # 拷贝静态库
    cp upnp/libupnp.a ${ABI_OUT_DIR}/
    cp ixml/libixml.a ${ABI_OUT_DIR}/
    
    echo "Finished building ${ABI}"
    cd ${SRC_DIR}
done

echo "========================================="
echo "All builds completed. Outputs are in ${OUTPUT_DIR}"
tree ${OUTPUT_DIR} -s -h || ls -lhR ${OUTPUT_DIR}
