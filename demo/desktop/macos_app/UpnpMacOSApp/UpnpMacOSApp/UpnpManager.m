// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

//
//  UpnpManager.m
//  UpnpMacOSApp
//

#include "UpnpManager.h"
#include <stdio.h>
#include <stdlib.h>
#include "upnp.h"
#include "UpnpDiscovery.h"
#include "ixml.h"

static UpnpClient_Handle g_ctrlpt_handle = -1;
static DeviceDiscoveredCallback g_deviceCallback = NULL;

static int UpnpManager_CallbackEventHandler(
    Upnp_EventType EventType, void *Event, void *Cookie) {
    (void)Cookie;
    
    switch (EventType) {
        case UPNP_DISCOVERY_ADVERTISEMENT_ALIVE:
        case UPNP_DISCOVERY_SEARCH_RESULT: {
            const UpnpDiscovery *d_event = (UpnpDiscovery *)Event;
            int errCode = UpnpDiscovery_get_ErrCode(d_event);

            if (errCode != UPNP_E_SUCCESS) {
                break;
            }

            const char *friendlyName = NULL;
            const char *location = UpnpString_get_String(UpnpDiscovery_get_Location(d_event));
            const char *deviceType = UpnpString_get_String(UpnpDiscovery_get_DeviceType(d_event));
            const char *udn = UpnpString_get_String(UpnpDiscovery_get_DeviceID(d_event));

            if (g_deviceCallback != NULL) {
                g_deviceCallback(friendlyName, deviceType, location, udn);
            }
            break;
        }
        default:
            break;
    }
    return 0;
}

int UpnpManager_Initialize(void) {
    int rc;
    unsigned short port = 0;

    // 暂时禁用这个，因为编译有问题
    // UpnpSetMaxJobsTotal(200);

    rc = UpnpInit2(NULL, port);
    if (rc != UPNP_E_SUCCESS) {
        return -1;
    }

    rc = UpnpRegisterClient(UpnpManager_CallbackEventHandler, NULL, &g_ctrlpt_handle);
    if (rc != UPNP_E_SUCCESS) {
        UpnpFinish();
        return -1;
    }

    return 0;
}

void UpnpManager_Cleanup(void) {
    if (g_ctrlpt_handle != -1) {
        UpnpUnRegisterClient(g_ctrlpt_handle);
        UpnpFinish();
        g_ctrlpt_handle = -1;
    }
}

void UpnpManager_SetDeviceDiscoveredCallback(DeviceDiscoveredCallback callback) {
    g_deviceCallback = callback;
}

int UpnpManager_StartSearch(void) {
    if (g_ctrlpt_handle == -1) {
        return -1;
    }
    return UpnpSearchAsync(g_ctrlpt_handle, 5, "ssdp:all", NULL);
}

int UpnpManager_StopSearch(void) {
    // libupnp 没有单独 stop search 的 UpnpUnSubscribeAsync？不，没有，直接返回 0
    return 0;
}

int UpnpManager_RefreshSearch(void) {
    if (g_ctrlpt_handle == -1) {
        return -1;
    }
    return UpnpSearchAsync(g_ctrlpt_handle, 5, "ssdp:all", NULL);
}
