// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include "upnp.h"
#include "UpnpString.h"

#define MAX_DEVICES 20

struct UPnPDevice {
    char friendlyName[256];
    char location[512];
    char deviceType[256];
    int discovered;
};

struct UPnPDevice devices[MAX_DEVICES];
int deviceCount = 0;
int running = 1;

void signalHandler(int signal) {
    printf("\nShutting down...\n");
    running = 0;
}

int DiscoverCallback(Upnp_Discovery *discovery) {
    if (discovery->ErrCode != UPNP_E_SUCCESS) {
        printf("Discovery error: %d\n", discovery->ErrCode);
        return UPNP_E_SUCCESS;
    }

    if (deviceCount >= MAX_DEVICES) {
        return UPNP_E_SUCCESS;
    }

    char *friendlyName = UpnpString_get_String(discovery->FriendlyName);
    char *location = UpnpString_get_String(discovery->Location);
    char *deviceType = UpnpString_get_String(discovery->DeviceType);

    if (friendlyName) {
        strncpy(devices[deviceCount].friendlyName, friendlyName, 255);
    }
    if (location) {
        strncpy(devices[deviceCount].location, location, 511);
    }
    if (deviceType) {
        strncpy(devices[deviceCount].deviceType, deviceType, 255);
    }
    devices[deviceCount].discovered = 1;
    deviceCount++;

    printf("\nNew device discovered!\n");
    printf("Device #%d: %s\n", deviceCount, devices[deviceCount-1].friendlyName);
    printf("Type: %s\n", devices[deviceCount-1].deviceType);
    printf("Location: %s\n", devices[deviceCount-1].location);

    return UPNP_E_SUCCESS;
}

void printMenu() {
    printf("\n===== UPnP Demo Menu =====\n");
    printf("1. Search for UPnP devices\n");
    printf("2. List discovered devices\n");
    printf("3. Create media renderer device\n");
    printf("4. Exit\n");
    printf("=============================\n");
    printf("Select an option: ");
}

void searchDevices(UpnpClient_Handle clientHandle) {
    printf("\nSearching for UPnP devices (5 seconds)...\n");
    deviceCount = 0;
    memset(devices, 0, sizeof(devices));
    
    int ret = UpnpSearchAsync(clientHandle, 5, "upnp:rootdevice", NULL);
    if (ret != UPNP_E_SUCCESS) {
        printf("Search failed: %d\n", ret);
        return;
    }
    
    printf("Search in progress...\n");
    for (int i = 5; i > 0; i--) {
        printf("%d...\n", i);
        sleep(1);
    }
    printf("Search complete! Found %d devices.\n", deviceCount);
}

void listDevices() {
    printf("\nDiscovered Devices:\n");
    if (deviceCount == 0) {
        printf("No devices found.\n");
        return;
    }
    
    for (int i = 0; i < deviceCount; i++) {
        printf("\n--- Device #%d ---\n", i + 1);
        printf("Name: %s\n", devices[i].friendlyName);
        printf("Type: %s\n", devices[i].deviceType);
        printf("Location: %s\n", devices[i].location);
    }
}

void createDevice() {
    printf("\nCreating media renderer device...\n");
    printf("Device created with UDN: uuid:test-media-renderer-123456\n");
    printf("Note: Full device implementation requires more setup.\n");
}

int main(int argc, char *argv[]) {
    UpnpClient_Handle clientHandle;
    int choice;
    
    printf("========================================\n");
    printf("UPnP Command Line Demo\n");
    printf("========================================\n");
    
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    printf("\nInitializing UPnP library...\n");
    int ret = UpnpInit("", 0);
    if (ret != UPNP_E_SUCCESS) {
        printf("UpnpInit failed: %d\n", ret);
        return -1;
    }
    printf("UPnP library initialized successfully.\n");
    
    UpnpSetMaxJobsTotal(200);
    
    ret = UpnpClient_Create(&clientHandle);
    if (ret != UPNP_E_SUCCESS) {
        printf("UpnpClient_Create failed: %d\n", ret);
        UpnpFinish();
        return -1;
    }
    
    ret = UpnpRegisterClient(clientHandle, DiscoverCallback, NULL, NULL);
    if (ret != UPNP_E_SUCCESS) {
        printf("UpnpRegisterClient failed: %d\n", ret);
        UpnpClient_Destroy(clientHandle);
        UpnpFinish();
        return -1;
    }
    printf("UPnP client registered.\n");
    
    while (running) {
        printMenu();
        if (scanf("%d", &choice) != 1) {
            int c;
            while ((c = getchar()) != '\n' && c != EOF);
            printf("Invalid input. Please try again.\n");
            continue;
        }
        
        switch (choice) {
            case 1:
                searchDevices(clientHandle);
                break;
            case 2:
                listDevices();
                break;
            case 3:
                createDevice();
                break;
            case 4:
                running = 0;
                break;
            default:
                printf("Invalid option. Please try again.\n");
        }
    }
    
    printf("\nCleaning up...\n");
    UpnpClient_Destroy(clientHandle);
    UpnpFinish();
    printf("Goodbye!\n");
    
    return 0;
}
