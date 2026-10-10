// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

//
//  UpnpManager.h
//  UpnpMacOSApp
//

#ifndef UpnpManager_h
#define UpnpManager_h

#ifdef __cplusplus
extern "C" {
#endif

typedef void (*DeviceDiscoveredCallback)(
    const char *friendlyName,
    const char *deviceType,
    const char *location,
    const char *udn
);

// 初始化 libupnp
int UpnpManager_Initialize(void);

// 清理
void UpnpManager_Cleanup(void);

// 设置设备发现回调
void UpnpManager_SetDeviceDiscoveredCallback(DeviceDiscoveredCallback callback);

// 开始搜索设备
int UpnpManager_StartSearch(void);

// 停止搜索
int UpnpManager_StopSearch(void);

// 刷新搜索
int UpnpManager_RefreshSearch(void);

#ifdef __cplusplus
}
#endif

#endif /* UpnpManager_h */
