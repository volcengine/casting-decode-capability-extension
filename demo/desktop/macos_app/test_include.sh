#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PUPNP_DIR="${SCRIPT_DIR}/../../../.."

echo "Testing includes..."
ls -la "${PUPNP_DIR}/upnp/inc"
echo ""
ls -la "${PUPNP_DIR}/ixml/inc"
echo ""
ls -la "${PUPNP_DIR}/build_macos/upnp/inc"
echo ""
echo "Testing clang with those includes..."

cd "${SCRIPT_DIR}/UpnpMacOSApp/UpnpMacOSApp"
cat > test_upnp.c << 'EOF'
#include <stdio.h>
#include "upnp.h"
int main() { printf("Hello\n"); return 0; }
EOF

clang -v test_upnp.c \
    -I "${PUPNP_DIR}/upnp/inc" \
    -I "${PUPNP_DIR}/ixml/inc" \
    -I "${PUPNP_DIR}/build_macos/upnp/inc"
