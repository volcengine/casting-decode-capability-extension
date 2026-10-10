// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import Cocoa

class AppDelegate: NSObject, NSApplicationDelegate {

    var window: NSWindow!
    
    func applicationDidFinishLaunching(_ aNotification: Notification) {
        // 创建主窗口
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 800, height: 600),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "UPnP Control Point"
        window.center()
        
        // 加载视图控制器
        let viewController = ViewController()
        window.contentViewController = viewController
        
        // 显示窗口
        window.makeKeyAndOrderFront(nil)
    }

    func applicationWillTerminate(_ aNotification: Notification) {
        // 清理工作
    }
}
