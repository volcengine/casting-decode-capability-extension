// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

import promptAction from '@ohos.promptAction';

@Entry
@Component
struct Index {
  @State statusText: string = 'Ready';
  @State devicesText: string = 'No devices found yet';
  @State isSearching: boolean = false;
  @State isCreating: boolean = false;

  build() {
    Column() {
      Column() {
        Text($r('app.string.text_status'))
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .width('100%')
          .margin({ bottom: 8 });

        Text(this.statusText)
          .width('100%')
          .padding(8)
          .backgroundColor($r('app.color.status_background'))
          .margin({ bottom: 16 });
      }
      .width('100%');

      Button($r('app.string.button_search_devices'))
        .width('100%')
        .height(44)
        .backgroundColor($r('app.color.button_blue'))
        .enabled(!this.isSearching && !this.isCreating)
        .margin({ bottom: 8 })
        .onClick(() => {
          this.searchForDevices();
        });

      Button($r('app.string.button_create_device'))
        .width('100%')
        .height(44)
        .backgroundColor($r('app.color.button_green'))
        .enabled(!this.isSearching && !this.isCreating)
        .margin({ bottom: 16 })
        .onClick(() => {
          this.createDevice();
        });

      Column() {
        Text($r('app.string.text_discovered_devices'))
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .width('100%')
          .margin({ bottom: 8 });

        Text(this.devicesText)
          .width('100%')
          .height(200)
          .padding(8)
          .backgroundColor($r('app.color.devices_background'))
      }
      .width('100%');
    }
    .width('100%')
    .height('100%')
    .padding(16)
  }

  searchForDevices() {
    this.isSearching = true;
    this.statusText = 'Searching for UPnP devices...';
    this.devicesText = '';

    setTimeout(() => {
      this.devicesText = `
        Found device: Test Media Renderer
          Device Type: urn:schemas-upnp-org:device:MediaRenderer:1
          Location: http://192.168.1.100:1900/desc.xml
        
        Found device: Test Media Server
          Device Type: urn:schemas-upnp-org:device:MediaServer:1
          Location: http://192.168.1.101:1901/desc.xml
      `.trim();
      this.statusText = 'Search completed';
      this.isSearching = false;
      
      promptAction.showToast({
        message: 'Search completed!',
        duration: 2000
      });
    }, 2000);
  }

  createDevice() {
    this.isCreating = true;
    this.statusText = 'Creating UPnP device...';

    setTimeout(() => {
      this.statusText = 'Device created successfully: uuid:test-media-renderer-123456';
      this.isCreating = false;
      
      promptAction.showToast({
        message: 'Device created successfully!',
        duration: 2000
      });
    }, 1500);
  }
}
