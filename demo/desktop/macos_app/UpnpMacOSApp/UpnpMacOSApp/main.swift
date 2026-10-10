// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import Cocoa

// 设置 AppDelegate
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate

// 启动应用
_ = NSApplicationMain(CommandLine.argc, CommandLine.unsafeArgv)

