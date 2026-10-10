// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

package com.example.upnpdemo

/**
 * JNI 桥接类 —— 调用 libupnp 的 C/C++ 实现
 *
 * 工作线程：所有 native 方法非阻塞（async 搜索 / Action）；
 * 回调由 libupnp 回调线程触发，UI 层需注意切换线程。
 */
class UpnpNative(private val listener: Listener) {

    interface Listener {
        /**
         * 设备发现/刷新回调（回调线程）
         */
        fun onDeviceFound(
            udn: String,
            friendlyName: String,
            location: String,
            avTransportControlURL: String,
            renderingControlControlURL: String,
            deviceAbilityRaw: String,
            expiresSeconds: Int
        )

        /**
         * 设备下线回调（回调线程）
         */
        fun onDeviceLost(udn: String)

        /**
         * 动作结果回调（回调线程）
         * @param action 动作名（Play/Pause/Stop/SetVolume/SetAVTransportURI...）
         * @param errCode 0 = 成功，其他表示 libupnp 错误码
         */
        fun onActionResult(action: String, errCode: Int, message: String)
    }

    // --- JNI entry points ---
    external fun nativeInit(callback: Any): Int
    external fun nativeFinish()
    external fun nativeSearch(timeoutSec: Int): Int
    external fun nativeSetAVTransportURI(controlURL: String, uri: String): Int
    external fun nativePlay(controlURL: String): Int
    external fun nativePause(controlURL: String): Int
    external fun nativeStop(controlURL: String): Int
    external fun nativeSeek(controlURL: String, targetSec: Int): Int
    external fun nativeGetPositionInfo(controlURL: String): IntArray
    external fun nativeSetVolume(controlURL: String, volume: Int): Int
    external fun nativeGetServerIp(): String

    // --- Called back by JNI ---
    @Suppress("unused")
    fun onDeviceFound(
        udn: String,
        friendlyName: String,
        location: String,
        avTransportControlURL: String,
        renderingControlControlURL: String,
        deviceAbilityRaw: String,
        expiresSeconds: Int
    ) {
        listener.onDeviceFound(
            udn, friendlyName, location,
            avTransportControlURL, renderingControlControlURL, deviceAbilityRaw, expiresSeconds
        )
    }

    @Suppress("unused")
    fun onDeviceLost(udn: String) {
        listener.onDeviceLost(udn)
    }

    @Suppress("unused")
    fun onActionResult(action: String, errCode: Int, message: String) {
        listener.onActionResult(action, errCode, message)
    }

    companion object {
        init {
            System.loadLibrary("upnp_android_native")
        }
    }
}
