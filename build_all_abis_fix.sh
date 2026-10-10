#!/bin/bash

# 设置 NDK 和 CMake 路径
NDK_PATH=~/Library/Android/sdk/ndk/21.1.6352462
CMAKE_PATH=~/Library/Android/sdk/cmake/3.18.1/bin/cmake

# 目标架构列表
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

# 平台版本：libupnp 依赖 getifaddrs（API 24 才引入 bionic），
# 我们通过 demo/mobile/android/src/main/cpp/ifaddrs_android.{h,c}
# 提供 NETLINK shim 实现，并在本脚本中用 -include 强制预置 getifaddrs 声明，
# 使 pupnp 可在 android-21 平台下编译通过，shim 在最终 .so 链接时解析符号。
PLATFORM="android-21"

# 源码目录
SRC_DIR=$(pwd)
OUTPUT_DIR="${SRC_DIR}/output_android"

# shim 头路径（供 pupnp 编译时 -include 使用）
SHIM_INC="${SRC_DIR}/demo/mobile/android/src/main/cpp/ifaddrs_android.h"

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
    # 关闭 UPNP_BUILD_SHARED：pupnp 的 libupnp.so 会要求立即解析 getifaddrs，
    # 而该符号由我们 demo 侧的 shim 提供，Android 上只需静态库。
    ${CMAKE_PATH} \
        -DCMAKE_TOOLCHAIN_FILE=${NDK_PATH}/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=${ABI} \
        -DANDROID_PLATFORM=${PLATFORM} \
        -DCMAKE_C_FLAGS="-include ${SHIM_INC}" \
        -DUPNP_BUILD_SHARED=OFF \
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
    
    # 拷贝并 strip 动态库（仅在构建出 .so 时执行，android-21 下 pupnp 关闭了 shared 构建）
    if [ -f upnp/libupnp.so ] && [ -f ixml/libixml.so ]; then
        cp upnp/libupnp.so ${ABI_OUT_DIR}/
        cp ixml/libixml.so ${ABI_OUT_DIR}/
        ${NDK_PATH}/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip ${ABI_OUT_DIR}/libupnp.so ${ABI_OUT_DIR}/libixml.so
    fi

    # 拷贝静态库
    cp upnp/libupnp.a ${ABI_OUT_DIR}/
    cp ixml/libixml.a ${ABI_OUT_DIR}/
    
    echo "Finished building ${ABI}"
    cd ${SRC_DIR}
done

echo "========================================="
echo "All builds completed. Outputs are in ${OUTPUT_DIR}"
ls -lhR ${OUTPUT_DIR}
