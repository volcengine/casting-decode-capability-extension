cmake_minimum_required(VERSION 3.10)
project(upnp_android_demo VERSION 1.0)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 11)

# ---------------------------------------------------------------------------
# pupnp 根路径（android/ 目录的上两级就是 pupnp/）
# ---------------------------------------------------------------------------
get_filename_component(PUPNP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../.. ABSOLUTE)

# 预构建产物目录：output_android/<ABI>/lib{upnp,ixml}.{a,so}
# 由 pupnp/build_all_abis_fix.sh 生成
set(PREBUILT_DIR "${PUPNP_ROOT}/output_android/${ANDROID_ABI}")

if(NOT EXISTS "${PREBUILT_DIR}/libupnp.a")
    message(FATAL_ERROR
        "prebuilt libupnp/libixml 未找到: ${PREBUILT_DIR}. "
        "请先在 pupnp/ 根目录执行: bash build_all_abis_fix.sh")
endif()

# 预构建期使用的 upnpconfig.h 路径（按 ABI 选择）
set(UPNPCONFIG_DIR "${PUPNP_ROOT}/build_android/build_${ANDROID_ABI}/upnp/inc")

# ---------------------------------------------------------------------------
# 将预构建 .a 作为 IMPORTED 目标
# ---------------------------------------------------------------------------
add_library(ixml STATIC IMPORTED)
set_target_properties(ixml PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/libixml.a"
    INTERFACE_INCLUDE_DIRECTORIES "${PUPNP_ROOT}/ixml/inc;${PUPNP_ROOT}/upnp/inc;${UPNPCONFIG_DIR}"
)

add_library(upnp STATIC IMPORTED)
set_target_properties(upnp PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/libupnp.a"
    INTERFACE_INCLUDE_DIRECTORIES "${PUPNP_ROOT}/upnp/inc;${UPNPCONFIG_DIR}"
)

# ---------------------------------------------------------------------------
# getifaddrs/freeifaddrs shim（仅 __ANDROID_API__ < 24 时生效）
# pupnp 的 libupnp.a 虽以 android-24 编译，但对 getifaddrs 仅存在未解析外部引用，
# 只要在最终 .so 链接时提供同名实现即可消除依赖，从而支持 android-21 运行。
# ---------------------------------------------------------------------------
# JNI 桥接库
# ---------------------------------------------------------------------------
add_library(upnp_android_native SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/upnp_android_jni.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/ifaddrs_android.c
)

target_include_directories(upnp_android_native PRIVATE
    ${PUPNP_ROOT}/upnp/inc
    ${PUPNP_ROOT}/ixml/inc
    ${UPNPCONFIG_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
)

target_compile_definitions(upnp_android_native PRIVATE UPNP_STATIC_LIB)

# 确保 shim 符号不被隐藏，供 libupnp.a 的未解析外部引用在链接阶段找到
set_source_files_properties(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/ifaddrs_android.c
    PROPERTIES COMPILE_FLAGS "-fvisibility=default"
)

find_library(log-lib log)
find_library(android-lib android)

# 注意链接顺序：upnp 依赖 ixml
target_link_libraries(upnp_android_native
    upnp
    ixml
    ${log-lib}
    ${android-lib}
)
