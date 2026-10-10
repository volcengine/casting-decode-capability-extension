// swift-tools-version:5.5
// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import PackageDescription

let package = Package(
    name: "UpnpMacOSApp",
    platforms: [
        .macOS(.v10_15)
    ],
    products: [
        .executable(
            name: "UpnpMacOSApp",
            targets: ["UpnpMacOSApp"]
        )
    ],
    targets: [
        .executableTarget(
            name: "UpnpMacOSApp",
            dependencies: [],
            path: "UpnpMacOSApp"
        )
    ]
)
