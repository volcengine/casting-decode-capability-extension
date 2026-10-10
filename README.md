# 投屏设备解码能力扩展 Demo

本仓库提供一套**投屏设备解码能力扩展规范**的开源示例实现（Demo），用于演示如何在设备发现阶段声明并协商大屏端（Sink 端）的解码能力，从而在投屏场景中精确匹配设备的编解码规格、动态范围与最大分辨率。

## 项目目的

在投屏场景中，大屏端（电视、盒子等）的解码能力差异很大。本项目通过在设备发现描述（DDD）的 `<device>` 节点下新增自定义扩展节点 `<DevCapExt>`，以紧凑的位掩码方式声明设备支持的编码标准、Profile、Level、Tier、色深、容器格式及最大分辨率，帮助投屏源端（Source）在推流前精确匹配设备能力，避免因超出硬件解码器极限而导致的绿屏或解码失败（尤其是竖屏短视频场景）。

本仓库包含：

- **协议规范文档**：`投屏设备解码能力扩展规范 V1.0`（见 `投屏设备解码能力扩展规范 V1.0.pdf`）。
- **跨平台示例 Demo**：位于 [`demo/`](demo/) 目录，演示能力声明与投屏拉流的完整链路。

## 依赖的开源项目

本项目基于 [pupnp（Portable SDK for UPnP Devices，libupnp）](https://github.com/pupnp/pupnp) 开发，`<device>` 节点为 UPnP 标准节点。原始 pupnp 的说明文档保留为 [`README.md.pupnp`](README.md.pupnp)，其许可证与版权信息见根目录 [`COPYING`](COPYING) 与 [`third_party_notice`](third_party_notice)。

Demo 中 Android 端的 `ifaddrs` 兼容实现改编自 AOSP bionic，许可证见 [`LICENSES/BSD-2-Clause.txt`](LICENSES/BSD-2-Clause.txt)。

## Demo 概览

本仓库主要提供 **Android** 开源 Demo，已完整集成 libupnp（Kotlin + JNI），可真机运行。桌面端（CLI / macOS）、iOS、HarmonyOS 目录仅作参考，**尚未开发完成**，请勿直接用于生产。

| 平台 | 类型 | 状态 | 说明 |
|------|------|------|------|
| Android | 移动端 | ✅ 已完成 | Kotlin + JNI，真实集成 libupnp，可真机运行 |
| macOS | 桌面端 | 🚧 未验证 | Swift/ObjC，使用正确 API，但依赖预构建库，未验证可运行 |
| Windows / Linux | 桌面端 | ❌ 未完成 | CLI 使用已废弃 API，无法编译 |
| iOS | 移动端 | ❌ 未完成 | 仅 UI 界面，未接入 libupnp |
| HarmonyOS | 移动端 | ❌ 未完成 | 仅 UI 界面，未接入 libupnp |

各平台构建与使用详情见 [`demo/README.md`](demo/README.md)。

### Android 已实现能力

- 搜索投屏设备
- 创建媒体渲染器设备
- 显示设备信息
- 支持设备解码能力扩展（DevCapExt）声明与解析

## 安全提示

Android Demo 内置了一个本地 HTTP 文件服务，用于把媒体推送给局域网内的 DMR（电视等）投屏。由于投屏要求电视能从手机的局域网 IP 拉流，该服务会监听全部网卡（而非 `127.0.0.1`）。

- **仅作为演示 Demo，该 HTTP 服务接口未鉴权。**
- **请勿用于生产环境，也不要在此基础上扩大暴露面。**
- 生产场景请自行增加鉴权、限制监听范围等安全措施。

## License

The specification is released under the Creative Commons Attribution 4.0 International License ([CC BY 4.0](LICENSES/CC-BY-4.0.txt)).

Sample code, demos in this repository are licensed under the [BSD 3-Clause License](LICENSES/BSD-3-Clause.txt).
