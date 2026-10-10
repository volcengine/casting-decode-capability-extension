// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

//
//  UpnpManagerSwift.swift
//  UpnpMacOSApp
//

import Foundation

// 设备信息模型
struct DeviceInfo {
    let friendlyName: String
    let deviceType: String
    let location: String
    let udn: String
    
    // 兼容旧构造（带默认 udn）
    init(friendlyName: String, deviceType: String, location: String, udn: String = "") {
        self.friendlyName = friendlyName
        self.deviceType = deviceType
        self.location = location
        self.udn = udn
    }
}

class UpnpManagerSwift: ObservableObject {
    
    static let shared = UpnpManagerSwift()
    
    @Published var discoveredDevices: [DeviceInfo] = []
    
    private var isInitialized = false
    
    private init() {}
    
    // 静态全局回调处理，使用 Swift 本地类型，不是 C 类型！
    private typealias SwiftDeviceCallback = (String?, String?, String?, String?) -> Void
    private static var swiftCallback: SwiftDeviceCallback?
    
    func initialize() -> Bool {
        guard !isInitialized else { return true }
        
        // 设置全局 Swift 回调
        UpnpManagerSwift.swiftCallback = { [weak self] fname, dtype, dloc, dudn in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                let name = fname ?? "Unknown Device"
                let type = dtype ?? ""
                let loc = dloc ?? ""
                let uniqueId = dudn ?? ""
                
                // 检查是否已经存在相同 UDN 的设备
                if !self.discoveredDevices.contains(where: { $0.udn == uniqueId }) {
                    let device = DeviceInfo(
                        friendlyName: name.isEmpty ? "Unknown Device" : name,
                        deviceType: type,
                        location: loc,
                        udn: uniqueId
                    )
                    self.discoveredDevices.append(device)
                }
            }
        }
        
        // 设置 C 层回调（使用不捕获的闭包）
        UpnpManager_SetDeviceDiscoveredCallback { (fname: UnsafePointer<CChar>?, dtype: UnsafePointer<CChar>?, dloc: UnsafePointer<CChar>?, dudn: UnsafePointer<CChar>?) in
            let swiftName = fname != nil ? String(cString: fname!) : nil
            let swiftType = dtype != nil ? String(cString: dtype!) : nil
            let swiftLoc = dloc != nil ? String(cString: dloc!) : nil
            let swiftUdn = dudn != nil ? String(cString: dudn!) : nil
            UpnpManagerSwift.swiftCallback?(swiftName, swiftType, swiftLoc, swiftUdn)
        }
        
        let result = UpnpManager_Initialize()
        isInitialized = (result == 0)
        return isInitialized
    }
    
    func cleanup() {
        UpnpManager_Cleanup()
        isInitialized = false
    }
    
    func startSearch() {
        _ = UpnpManager_StartSearch()
    }
    
    func stopSearch() {
        _ = UpnpManager_StopSearch()
    }
    
    func refresh() {
        discoveredDevices.removeAll()
        _ = UpnpManager_RefreshSearch()
    }
}
