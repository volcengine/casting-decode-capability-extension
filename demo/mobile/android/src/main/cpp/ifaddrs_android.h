// Copyright (c) 2015 The Android Open Source Project. All rights reserved.
// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: BSD-2-Clause
//
// This file has been modified by ByteDance Ltd. and/or its affiliates. on 2026-06-18
//
// Original file was released under BSD-2-Clause, with the full license text
// available at https://android.googlesource.com/platform/bionic/+/master/libc/bionic/ifaddrs.cpp
//
// This modified file is released under the same license.

/*
 * ifaddrs_android.h - 为 Android API 21-23 提供 getifaddrs/freeifaddrs
 *
 * Android bionic 自 API 24 (Android 7.0) 才暴露 getifaddrs。
 * 本 shim 通过 NETLINK_ROUTE (RTM_GETLINK + RTM_GETADDR) 实现相同语义，
 * 使 API 21+ 的设备同样可用。
 *
 * 包含顺序：务必在任何 <ifaddrs.h> 之前包含本头；或由构建系统强制 include。
 */
#ifndef IFADDRS_ANDROID_H
#define IFADDRS_ANDROID_H

#include <sys/socket.h>
#include <net/if.h>

/*
 * bionic 自 NDK r10 起即在 <ifaddrs.h> 声明了 `struct ifaddrs`，
 * 仅 getifaddrs/freeifaddrs 这两个函数被标注为 __INTRODUCED_IN(24)。
 * 因此在 API<24 构建时直接 include <ifaddrs.h> 拿到 struct，
 * 然后补充函数声明即可；避免与 bionic 的定义冲突。
 */
#include <ifaddrs.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__ANDROID__) && (!defined(__ANDROID_API__) || __ANDROID_API__ < 24)

int  getifaddrs(struct ifaddrs **result);
void freeifaddrs(struct ifaddrs *ifa);

#endif

#ifdef __cplusplus
}
#endif

#endif /* IFADDRS_ANDROID_H */
