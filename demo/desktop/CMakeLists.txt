cmake_minimum_required(VERSION 3.10)
project(upnp_cli_demo VERSION 1.0)

set(CMAKE_C_STANDARD 99)

# 设置项目根目录
set(PUPNP_ROOT ${CMAKE_SOURCE_DIR}/../..)

# 包含 libupnp
include_directories(${PUPNP_ROOT}/upnp/inc ${PUPNP_ROOT}/ixml/inc)

# 查找 libupnp 库
if (WIN32)
    find_library(UPNP_LIBRARY upnp HINTS ${PUPNP_ROOT}/build_windows/upnp/Release)
    find_library(IXML_LIBRARY ixml HINTS ${PUPNP_ROOT}/build_windows/ixml/Release)
elseif (APPLE)
    include_directories(${PUPNP_ROOT}/build_macos/upnp/inc)
    find_library(UPNP_LIBRARY upnp HINTS ${PUPNP_ROOT}/build_macos/upnp)
    find_library(IXML_LIBRARY ixml HINTS ${PUPNP_ROOT}/build_macos/ixml)
else ()
    find_library(UPNP_LIBRARY upnp HINTS ${PUPNP_ROOT}/build_linux/upnp)
    find_library(IXML_LIBRARY ixml HINTS ${PUPNP_ROOT}/build_linux/ixml)
endif ()

# 创建可执行文件
add_executable(upnp_demo src/upnp_demo.c)
target_link_libraries(upnp_demo ${UPNP_LIBRARY} ${IXML_LIBRARY})
