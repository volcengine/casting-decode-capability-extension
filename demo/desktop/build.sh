#!/bin/bash

# 跨平台命令行 demo 构建脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"

mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

echo "========================================="
echo "Building UPnP CLI Demo"
echo "========================================="

cmake ..
make

echo
echo "========================================="
echo "Build completed!"
echo "========================================="
echo
echo "Run the demo:"
echo "  ${BUILD_DIR}/upnp_demo"
echo
