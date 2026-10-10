// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

package com.example.upnpdemo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.util.Log
import android.widget.ScrollView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 基于 pupnp 的 Android DLNA 控制点 Demo
 * - 启动时拉起本地 HTTP Server，退出时停止
 * - 发现 MediaRenderer，维护上下线状态
 * - 选中设备后，从 local_media 中选择 mp4 资源投送
 * - 支持暂停、停止、音量加减
 */
class MainActivity : AppCompatActivity(), UpnpNative.Listener {

    companion object {
        private const val TAG = "DlnaExpandTest"
        private const val PREFERRED_HTTP_PORT = 8080
        private const val SEARCH_TIMEOUT_SEC = 5
        private const val DEFAULT_EXPIRES_SEC = 120
        private const val DEVICE_CLEAN_INTERVAL_MS = 2_000L
        private const val PROGRESS_POLL_INTERVAL_MS = 1_000L
        private const val VOLUME_STEP = 5

        private val AUDIO_BIT_NAMES = linkedMapOf(
            0x0001 to "AAC-LC",
            0x0002 to "AC3",
            0x0004 to "EAC3",
            0x0008 to "DTS",
            0x0010 to "AAC-HE",
            0x0020 to "AAC-HE-V2"
        )

        private val CONTAINER_BIT_NAMES = linkedMapOf(
            0x0001 to "MP4",
            0x0002 to "MPEG-TS",
            0x0004 to "HLS",
            0x0008 to "FLV",
            0x0010 to "Matroska",
            0x0020 to "MPEG-DASH",
            0x0040 to "WebM",
            0x0080 to "AVI",
            0x0100 to "RMVB",
            0x0200 to "WMV"
        )

        private val LEVEL_NAMES = mapOf(
            0 to "L2",
            1 to "L21",
            2 to "L22",
            3 to "L23",
            4 to "L3",
            5 to "L31",
            6 to "L32",
            7 to "L33",
            8 to "L4",
            9 to "L41",
            10 to "L42",
            11 to "L43",
            12 to "L5",
            13 to "L51",
            14 to "L52",
            15 to "L53",
            16 to "L6",
            17 to "L61",
            18 to "L62",
            19 to "L63"
        )

        private val MEDIA_NAME_REGEX = Regex(
            "^([a-z0-9]+)_([a-z0-9]+)_([0-9]+)p_([0-9]+(?:\\.[0-9]+)?)M_([a-z0-9]+)_([a-z0-9]+)_([a-z0-9+]+)$",
            RegexOption.IGNORE_CASE
        )

        private val AVC_LIMITS = mapOf(
            0 to levelLimit(352, 288, 30.0, 2.0),
            1 to levelLimit(352, 576, 30.0, 4.0),
            2 to levelLimit(720, 480, 30.0, 4.0),
            3 to levelLimit(720, 576, 30.0, 10.0),
            4 to levelLimit(720, 480, 30.0, 10.0),
            5 to levelLimit(1280, 720, 30.0, 14.0),
            6 to levelLimit(1280, 720, 60.0, 20.0),
            7 to levelLimit(1920, 1080, 30.0, 20.0),
            8 to levelLimit(1920, 1080, 30.0, 20.0),
            9 to levelLimit(1920, 1080, 60.0, 50.0),
            10 to levelLimit(2048, 1080, 60.0, 50.0),
            11 to levelLimit(2560, 1440, 60.0, 80.0),
            12 to levelLimit(3840, 2160, 30.0, 135.0),
            13 to levelLimit(4096, 2304, 30.0, 240.0),
            14 to levelLimit(4096, 2304, 60.0, 240.0),
            15 to levelLimit(7680, 4320, 30.0, 240.0),
            16 to levelLimit(7680, 4320, 60.0, 240.0),
            17 to levelLimit(7680, 4320, 120.0, 240.0),
            18 to levelLimit(7680, 4320, 120.0, 240.0),
            19 to levelLimit(7680, 4320, 120.0, 240.0)
        )

        private val HEVC_LIMITS = mapOf(
            0 to levelLimit(176, 144, 15.0, 0.128, 0.128),
            1 to levelLimit(352, 288, 30.0, 1.5, 3.0),
            2 to levelLimit(640, 360, 30.0, 3.0, 6.0),
            3 to levelLimit(720, 480, 30.0, 6.0, 10.0),
            4 to levelLimit(1280, 720, 30.0, 10.0, 20.0),
            5 to levelLimit(1280, 720, 60.0, 10.0, 20.0),
            6 to levelLimit(1920, 1080, 30.0, 12.0, 30.0),
            7 to levelLimit(1920, 1080, 60.0, 20.0, 50.0),
            8 to levelLimit(2560, 1440, 30.0, 25.0, 50.0),
            9 to levelLimit(3840, 2160, 30.0, 25.0, 100.0),
            10 to levelLimit(3840, 2160, 60.0, 40.0, 160.0),
            11 to levelLimit(4096, 2160, 120.0, 60.0, 240.0),
            12 to levelLimit(7680, 4320, 30.0, 60.0, 240.0),
            13 to levelLimit(7680, 4320, 60.0, 120.0, 480.0),
            14 to levelLimit(8192, 4320, 120.0, 240.0, 800.0),
            15 to levelLimit(8192, 4320, 120.0, 240.0, 800.0),
            16 to levelLimit(8192, 4320, 120.0, 240.0, 800.0),
            17 to levelLimit(8192, 4320, 120.0, 240.0, 800.0),
            18 to levelLimit(8192, 4320, 120.0, 240.0, 800.0),
            19 to levelLimit(8192, 4320, 120.0, 240.0, 800.0)
        )

        private val VVC_LIMITS = mapOf(
            0 to levelLimit(176, 144, 15.0, 0.128, 0.128),
            1 to levelLimit(352, 288, 30.0, 1.5, 3.0),
            2 to levelLimit(640, 360, 30.0, 3.0, 6.0),
            3 to levelLimit(720, 480, 30.0, 6.0, 12.0),
            4 to levelLimit(1280, 720, 30.0, 12.0, 24.0),
            5 to levelLimit(1280, 720, 60.0, 15.0, 30.0),
            6 to levelLimit(1920, 1080, 30.0, 20.0, 40.0),
            7 to levelLimit(1920, 1080, 60.0, 35.0, 70.0),
            8 to levelLimit(2560, 1440, 30.0, 40.0, 80.0),
            9 to levelLimit(3840, 2160, 30.0, 60.0, 120.0),
            10 to levelLimit(3840, 2160, 60.0, 100.0, 200.0),
            11 to levelLimit(4096, 2160, 120.0, 160.0, 320.0),
            12 to levelLimit(7680, 4320, 30.0, 200.0, 400.0),
            13 to levelLimit(7680, 4320, 60.0, 300.0, 600.0),
            14 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0),
            15 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0),
            16 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0),
            17 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0),
            18 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0),
            19 to levelLimit(8192, 4320, 120.0, 500.0, 1000.0)
        )

        private val AV1_LIMITS = mapOf(
            0 to levelLimit(176, 144, 15.0, 0.5),
            1 to levelLimit(352, 288, 30.0, 1.5),
            2 to levelLimit(640, 360, 30.0, 3.0),
            3 to levelLimit(720, 480, 30.0, 6.0),
            4 to levelLimit(1280, 720, 30.0, 10.0),
            5 to levelLimit(1280, 720, 60.0, 20.0),
            6 to levelLimit(1920, 1080, 30.0, 20.0),
            7 to levelLimit(1920, 1080, 60.0, 30.0),
            8 to levelLimit(2560, 1440, 30.0, 40.0),
            9 to levelLimit(3840, 2160, 30.0, 60.0),
            10 to levelLimit(3840, 2160, 60.0, 100.0),
            11 to levelLimit(4096, 2160, 120.0, 160.0),
            12 to levelLimit(7680, 4320, 30.0, 200.0),
            13 to levelLimit(7680, 4320, 60.0, 300.0),
            14 to levelLimit(8192, 4320, 120.0, 400.0),
            15 to levelLimit(8192, 4320, 120.0, 400.0),
            16 to levelLimit(8192, 4320, 120.0, 400.0),
            17 to levelLimit(8192, 4320, 120.0, 400.0),
            18 to levelLimit(8192, 4320, 120.0, 400.0),
            19 to levelLimit(8192, 4320, 120.0, 400.0)
        )

        private fun levelLimit(
            width: Int,
            height: Int,
            fps: Double,
            mainTierBitrateMbps: Double,
            highTierBitrateMbps: Double? = null
        ): LevelTierLimit {
            return LevelTierLimit(width, height, fps, mainTierBitrateMbps, highTierBitrateMbps)
        }
    }

    private enum class VideoCodec(val displayName: String, val sortPriority: Int) {
        AVC("AVC", 1),
        HEVC("HEVC", 2),
        VVC("VVC", 3),
        AV1("AV1", 3),
        UNKNOWN("UNKNOWN", -1)
    }

    private enum class AudioCodec {
        AAC,
        AC3,
        EAC3,
        DTS,
        AAC_HE,
        AAC_HE_V2,
        UNKNOWN
    }

    private enum class DynamicRange {
        SDR,
        HLG,
        HDR10,
        HDR10PLUS,
        HDR_VIVID,
        DOLBY_VISION,
        UNKNOWN
    }

    private enum class MediaOrientation(val assetDirName: String, val displayName: String) {
        LANDSCAPE("landscape", "横屏"),
        PORTRAIT("portrait", "竖屏")
    }

    private enum class QualityBucket(
        val displayName: String,
        val majorSize: Int,
        val minorSize: Int,
        val sortPriority: Int
    ) {
        K4("4K", 3840, 2160, 6),
        K2("2K", 2560, 1440, 5),
        P1080("1080P", 1920, 1080, 4),
        P720("720P", 1280, 720, 3),
        P540("540P", 960, 540, 2),
        P480("480P", 854, 480, 1)
    }

    private data class LevelTierLimit(
        val maxWidth: Int,
        val maxHeight: Int,
        val maxFps: Double,
        val mainTierBitrateMbps: Double,
        val highTierBitrateMbps: Double? = null
    ) {
        val maxArea: Long
            get() = maxWidth.toLong() * maxHeight.toLong()

        val maxPixelsPerSecond: Double
            get() = maxArea.toDouble() * maxFps

        fun maxBitrateMbps(tierValue: Int): Double {
            return if (tierValue == 1 && highTierBitrateMbps != null) {
                highTierBitrateMbps
            } else {
                mainTierBitrateMbps
            }
        }
    }

    private data class DeviceEntry(
        val udn: String,
        var name: String,
        var location: String,
        var avTransportURL: String,
        var renderingControlURL: String,
        var deviceAbilityRaw: String,
        var expiresAtMs: Long
    )

    private data class LocalMediaSpec(
        val file: File,
        val relativePath: String,
        val container: String,
        val videoCodec: VideoCodec,
        val audioCodec: AudioCodec,
        val dynamicRange: DynamicRange,
        val profile: String,
        val width: Int,
        val height: Int,
        val fps: Double,
        val bitrateMbps: Double
    ) {
        val area: Long
            get() = width.toLong() * height.toLong()

        val pixelsPerSecond: Double
            get() = area.toDouble() * fps
    }

    private data class ParsedDeviceAbility(
        val containers: Set<String>,
        val audioCodecs: Set<AudioCodec>,
        val videoCapabilities: List<DeviceVideoCapability>
    )

    private data class DeviceVideoCapability(
        val raw: String,
        val codec: VideoCodec,
        val supportedRanges: Set<DynamicRange>,
        val profile: String,
        val profileValue: Int,
        val levelValue: Int,
        val tierValue: Int,
        val extensionValue: Int,
        val decoderMaxWidth: Int,
        val decoderMaxHeight: Int,
        val levelMaxFrameArea: Long?,
        val levelMaxPixelsPerSecond: Double?,
        val levelReferenceFps: Double?,
        val maxBitrateMbps: Double?
    )

    private data class MediaSelectionReport(
        val playable: List<LocalMediaSpec>,
        val selected: LocalMediaSpec?,
        val reason: String
    )

    private data class ResolutionOption(
        val bucketName: String,
        val media: LocalMediaSpec,
        val label: String
    )

    private lateinit var upnpNative: UpnpNative
    private lateinit var httpServer: LocalHttpServer
    private var multicastLock: WifiManager.MulticastLock? = null

    private lateinit var statusText: TextView
    private lateinit var currentMediaText: TextView
    private lateinit var deviceAbilityText: TextView
    private lateinit var playModeText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var deviceListView: ListView
    private lateinit var searchBtn: Button
    private lateinit var playBtn: Button
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var qualityBtn: Button
    private lateinit var volumeDownBtn: Button
    private lateinit var volumeUpBtn: Button
    private lateinit var experimentalSelectionCheckBox: CheckBox
    private lateinit var portraitMediaCheckBox: CheckBox
    private lateinit var repeatPlaybackCheckBox: CheckBox

    private val devices = mutableListOf<DeviceEntry>()
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private var selectedDeviceUdn: String? = null
    private val localMediaSpecs = mutableListOf<LocalMediaSpec>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val positionExecutor = Executors.newSingleThreadExecutor()
    private var currentVolume = 20
    private var pendingPlayDeviceUdn: String? = null
    private var pendingPlayUrl: String? = null
    private var pendingPlayFileName: String? = null
    private var httpServerPort = -1
    private var currentDurationSec = 0
    private var currentPositionSec = 0
    private var isUserSeeking = false
    private var progressRequestInFlight = false
    private var activePlaybackDeviceUdn: String? = null
    private var activePlaybackUrl: String? = null
    private var activePlaybackFileName: String? = null
    private var isPlaybackPaused = false
    private var isExperimentalSelectionEnabled = false
    private var isRepeatPlaybackEnabled = false
    private var repeatPlaybackInFlight = false
    private var currentMediaOrientation = MediaOrientation.LANDSCAPE
    private var lastRandomMediaFileName: String? = null

    private val deviceCleanupRunnable = object : Runnable {
        override fun run() {
            pruneExpiredDevices()
            mainHandler.postDelayed(this, DEVICE_CLEAN_INTERVAL_MS)
        }
    }

    private val progressPollRunnable = object : Runnable {
        override fun run() {
            requestPositionUpdate()
            mainHandler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusTextView)
        currentMediaText = findViewById(R.id.currentMediaTextView)
        deviceAbilityText = findViewById(R.id.deviceAbilityTextView)
        playModeText = findViewById(R.id.playModeTextView)
        progressText = findViewById(R.id.progressTextView)
        progressSeekBar = findViewById(R.id.progressSeekBar)
        volumeText = findViewById(R.id.volumeTextView)
        deviceListView = findViewById(R.id.deviceListView)
        searchBtn = findViewById(R.id.searchButton)
        playBtn = findViewById(R.id.playButton)
        pauseBtn = findViewById(R.id.pauseButton)
        stopBtn = findViewById(R.id.stopButton)
        qualityBtn = findViewById(R.id.qualityButton)
        volumeDownBtn = findViewById(R.id.volumeDownButton)
        volumeUpBtn = findViewById(R.id.volumeUpButton)
        experimentalSelectionCheckBox = findViewById(R.id.experimentalSelectionCheckBox)
        portraitMediaCheckBox = findViewById(R.id.portraitMediaCheckBox)
        repeatPlaybackCheckBox = findViewById(R.id.repeatPlaybackCheckBox)

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice)
        deviceListView.adapter = deviceAdapter
        deviceListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        deviceListView.setOnItemClickListener { _, _, pos, _ ->
            selectedDeviceUdn = devices.getOrNull(pos)?.udn
            devices.getOrNull(pos)?.let {
                resetProgressUi()
                updateDeviceAbilityText(it.deviceAbilityRaw)
                updateStatus("选中设备: ${it.name}")
            }
        }

        searchBtn.setOnClickListener { startSearch() }
        playBtn.setOnClickListener { handlePlayButton() }
        pauseBtn.setOnClickListener { sendTransportAction("Pause") }
        stopBtn.setOnClickListener { sendTransportAction("Stop") }
        qualityBtn.setOnClickListener { showQualitySelectionDialog() }
        volumeDownBtn.setOnClickListener { adjustVolume(-VOLUME_STEP) }
        volumeUpBtn.setOnClickListener { adjustVolume(VOLUME_STEP) }
        experimentalSelectionCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isExperimentalSelectionEnabled = isChecked
            updatePlayModeText()
            updateStatus(
                if (isChecked) {
                    "已切换为随机播放模式"
                } else {
                    "已切换为 DevCapExt 实验选流模式"
                }
            )
        }
        portraitMediaCheckBox.setOnCheckedChangeListener { _, isChecked ->
            currentMediaOrientation = if (isChecked) {
                MediaOrientation.PORTRAIT
            } else {
                MediaOrientation.LANDSCAPE
            }
            lastRandomMediaFileName = null
            reloadLocalMediaSpecs()
            updateStatus("已切换为${currentMediaOrientation.displayName}资源匹配")
        }
        repeatPlaybackCheckBox.setOnCheckedChangeListener { _, isChecked ->
            isRepeatPlaybackEnabled = isChecked
            repeatPlaybackInFlight = false
            updateStatus(if (isChecked) "已开启重播" else "已关闭重播")
        }
        currentMediaText.setOnClickListener { showPlayableMediaListDialog() }
        deviceAbilityText.setOnClickListener { showDeviceAbilityDialog() }
        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateProgressUi(progress, currentDurationSec)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val targetSec = seekBar?.progress ?: 0
                isUserSeeking = false
                seekToPosition(targetSec)
            }
        })

        updateVolumeText()
        updateCurrentMediaText(null)
        updateDeviceAbilityText(null)
        updatePlayModeText()
        resetProgressUi()
        acquireMulticastLock()
        prepareLocalMedia()
        startHttpServer()
        initUpnp()
        mainHandler.post(deviceCleanupRunnable)
        mainHandler.post(progressPollRunnable)
    }

    private fun acquireMulticastLock() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wm.createMulticastLock("dlna_expand_test_mcast").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun prepareLocalMedia() {
        val rootDir = getLocalMediaRootDir()
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        copyAssetPathRecursively("local_media", rootDir)
        reloadLocalMediaSpecs()
    }

    private fun reloadLocalMediaSpecs() {
        val dir = File(getLocalMediaRootDir(), currentMediaOrientation.assetDirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        localMediaSpecs.clear()
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile && isSupportedLocalMediaFile(file.name)) {
                val relativePath = "${currentMediaOrientation.assetDirName}/${file.name}"
                parseLocalMediaSpec(file, relativePath)?.let { spec ->
                    if (spec.dynamicRange != DynamicRange.HDR10) {
                        localMediaSpecs.add(spec)
                    }
                }
            }
        }
        updateStatus(
            "已准备 ${currentMediaOrientation.displayName} ${localMediaSpecs.size} 个可选本地视频资源（已过滤 HDR10）"
        )
    }

    private fun getLocalMediaRootDir(): File {
        return File(cacheDir, "local_media")
    }

    private fun copyAssetPathRecursively(assetPath: String, destination: File) {
        val children = assets.list(assetPath)?.filter { it.isNotBlank() }.orEmpty()
        if (children.isEmpty()) {
            try {
                destination.parentFile?.mkdirs()
                assets.open(assetPath).use { input ->
                    FileOutputStream(destination, false).use { out -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "copy asset failed: $assetPath", e)
            }
            return
        }
        if (!destination.exists()) {
            destination.mkdirs()
        }
        children.forEach { child ->
            copyAssetPathRecursively("$assetPath/$child", File(destination, child))
        }
    }

    private fun isSupportedLocalMediaFile(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(".mp4") || lower.endsWith(".flv")
    }

    private fun buildEncodedRelativePath(relativePath: String): String {
        return relativePath
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { Uri.encode(it) }
    }

    private fun buildLocalMediaUrl(media: LocalMediaSpec): String {
        val encodedPath = buildEncodedRelativePath(media.relativePath)
        return "http://${getLocalIp()}:$httpServerPort/$encodedPath"
    }

    private fun currentOrientationDisplayName(): String {
        return currentMediaOrientation.displayName
    }

    private fun currentOrientationEmptyMediaMessage(): String {
        return "${currentOrientationDisplayName()}资源目录下没有可投送的视频资源"
    }

    private fun currentOrientationEmptyMediaStatus(prefix: String): String {
        return "$prefix: ${currentOrientationEmptyMediaMessage()}"
    }

    private fun currentOrientationSelectionStatus(prefix: String): String {
        return "$prefix（${currentOrientationDisplayName()}）"
    }

    private fun startHttpServer() {
        val dir = getLocalMediaRootDir()
        httpServer = LocalHttpServer(dir, PREFERRED_HTTP_PORT)
        httpServerPort = try {
            httpServer.start()
        } catch (e: Exception) {
            Log.e(TAG, "startHttpServer failed", e)
            -1
        }
        if (httpServerPort <= 0) {
            updateStatus("HTTP Server 启动失败")
        }
    }

    private fun initUpnp() {
        upnpNative = UpnpNative(this)
        val rc = upnpNative.nativeInit(upnpNative)
        if (rc != 0) {
            updateStatus("UPnP 初始化失败: $rc")
            return
        }
        val ip = upnpNative.nativeGetServerIp()
        val serverStatus = if (httpServerPort > 0) "$ip:$httpServerPort" else "启动失败"
        updateStatus("UPnP 已启动 @ $ip, HTTP Server: $serverStatus")
    }

    private fun startSearch() {
        devices.clear()
        selectedDeviceUdn = null
        updateDeviceAbilityText(null)
        resetProgressUi()
        refreshDeviceList()
        updateStatus("开始搜索 MediaRenderer 设备…")
        val rc = upnpNative.nativeSearch(SEARCH_TIMEOUT_SEC)
        if (rc != 0) {
            updateStatus("搜索失败: $rc")
        }
    }

    private fun getLocalIp(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
    }

    private fun getSelectedDevice(): DeviceEntry? {
        val udn = selectedDeviceUdn ?: return null
        return devices.firstOrNull { it.udn == udn }
    }

    private fun playMediaOnSelectedDevice(
        media: LocalMediaSpec,
        statusMessage: String,
        mode: String
    ) {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        if (httpServerPort <= 0) {
            toast("HTTP Server 未启动成功")
            return
        }
        if (localMediaSpecs.isEmpty()) {
            toast(currentOrientationEmptyMediaMessage())
            return
        }
        val url = buildLocalMediaUrl(media)
        pendingPlayDeviceUdn = dev.udn
        pendingPlayUrl = url
        pendingPlayFileName = media.file.name
        Log.i(TAG, "play dispatch: mode=$mode file=${media.file.name} url=$url")
        val rc = upnpNative.nativeSetAVTransportURI(dev.avTransportURL, url)
        if (rc != 0) {
            pendingPlayDeviceUdn = null
            pendingPlayUrl = null
            pendingPlayFileName = null
            updateCurrentMediaText(null)
            updateStatus("SetAVTransportURI 失败: $rc")
        } else {
            resetProgressUi()
            updateCurrentMediaText(media.file.name)
            updateStatus(statusMessage)
        }
    }

    private fun playRandomMediaOnSelectedDevice() {
        if (localMediaSpecs.isEmpty()) {
            toast(currentOrientationEmptyMediaMessage())
            return
        }
        val media = findRandomLocalMedia() ?: run {
            toast(currentOrientationEmptyMediaMessage())
            return
        }
        lastRandomMediaFileName = media.file.name
        playMediaOnSelectedDevice(media, "随机投屏资源: ${media.file.name}", "random")
    }

    private fun playBestMediaOnSelectedDevice() {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        val report = selectBestMedia(dev)
        val media = report.selected
        if (media == null) {
            toast(report.reason)
            updateStatus("实验选流未命中: ${report.reason}")
            return
        }
        playMediaOnSelectedDevice(media, report.reason, "experimental")
    }

    private fun handlePlayButton() {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        if (shouldResumePlayback(dev)) {
            val rc = upnpNative.nativePlay(dev.avTransportURL)
            updateStatus("恢复播放请求已发送，rc=$rc")
            if (rc != 0) {
                toast("恢复播放失败: $rc")
            }
            return
        }
        playDefaultMediaOnSelectedDevice(dev)
    }

    private fun showQualitySelectionDialog() {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        val report = selectBestMedia(dev)
        Log.i(
            "DlnaExpandTest",
            "quality dialog report: device=${dev.name} playable=${report.playable.size} selected=${report.selected?.file?.name ?: "null"} reason=${report.reason}"
        )
        if (report.playable.isEmpty()) {
            toast(report.reason)
            updateStatus("清晰度列表为空: ${report.reason}")
            return
        }
        val options = buildResolutionOptions(report.playable)
        if (options.isEmpty()) {
            toast("没有可选清晰度")
            return
        }
        val labels = options.map { it.label }
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.item_quality_option,
            R.id.qualityOptionTextView,
            labels
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(R.id.qualityOptionTextView)
                val check = view.findViewById<TextView>(R.id.qualityOptionCheckView)
                val isActive = options.getOrNull(position)?.media?.file?.name == activePlaybackFileName
                text.maxLines = 1
                text.ellipsize = android.text.TextUtils.TruncateAt.END
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                text.text = labels[position]
                text.setTextColor(if (isActive) 0xFF1B5E20.toInt() else 0xFF212121.toInt())
                check.visibility = if (isActive) android.view.View.VISIBLE else android.view.View.GONE
                view.setBackgroundColor(if (isActive) 0xFFE8F5E9.toInt() else 0xFFFFFFFF.toInt())
                return view
            }
        }
        AlertDialog.Builder(this)
            .setTitle("清晰度切换")
            .setAdapter(adapter) { dialog, which ->
                val option = options.getOrNull(which) ?: return@setAdapter
                playMediaOnSelectedDevice(
                    media = option.media,
                    statusMessage = "手动选择清晰度: ${option.label}",
                    mode = "manual-quality"
                )
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun playDefaultMediaOnSelectedDevice(device: DeviceEntry) {
        val report = selectBestMedia(device)
        val media = when {
            isExperimentalSelectionEnabled -> findRandomLocalMedia()
            else -> report.selected
        }
        if (media == null) {
            toast(
                if (isExperimentalSelectionEnabled) currentOrientationEmptyMediaMessage() else report.reason
            )
            updateStatus(
                if (isExperimentalSelectionEnabled) {
                    currentOrientationEmptyMediaStatus("随机播放失败")
                } else {
                    "默认最高规格播放失败: ${report.reason}"
                }
            )
            return
        }
        val prefix = if (isExperimentalSelectionEnabled) {
            currentOrientationSelectionStatus("随机播放")
        } else {
            currentOrientationSelectionStatus("实验选流默认播放")
        }
        if (isExperimentalSelectionEnabled) {
            lastRandomMediaFileName = media.file.name
            Log.i(
                TAG,
                "random selection: picked=${media.file.name} last=$lastRandomMediaFileName pool=${localMediaSpecs.size}"
            )
        }
        playMediaOnSelectedDevice(
            media = media,
            statusMessage = "$prefix: ${buildResolutionOptionLabel(resolveQualityBucket(media).displayName, media)}",
            mode = if (isExperimentalSelectionEnabled) "random-default" else "experimental-default-best"
        )
    }

    private fun updatePlayModeText() {
        playModeText.text = if (isExperimentalSelectionEnabled) {
            "当前播放模式: 随机播放"
        } else {
            "当前播放模式: DevCapExt 实验选流"
        }
    }

    private fun shouldResumePlayback(dev: DeviceEntry): Boolean {
        return isPlaybackPaused &&
            activePlaybackDeviceUdn == dev.udn &&
            !activePlaybackUrl.isNullOrEmpty()
    }

    private fun clearPendingPlayback() {
        pendingPlayDeviceUdn = null
        pendingPlayUrl = null
        pendingPlayFileName = null
    }

    private fun clearActivePlayback(resetMediaText: Boolean) {
        activePlaybackDeviceUdn = null
        activePlaybackUrl = null
        activePlaybackFileName = null
        isPlaybackPaused = false
        repeatPlaybackInFlight = false
        if (resetMediaText) {
            updateCurrentMediaText(null)
        }
    }

    private fun triggerRepeatPlaybackIfNeeded(device: DeviceEntry, durationSec: Int, positionSec: Int) {
        if (!isRepeatPlaybackEnabled || repeatPlaybackInFlight || isPlaybackPaused) {
            return
        }
        if (durationSec <= 0 || positionSec < durationSec) {
            return
        }
        if (activePlaybackDeviceUdn != device.udn) {
            return
        }
        val url = activePlaybackUrl ?: return
        repeatPlaybackInFlight = true
        pendingPlayDeviceUdn = device.udn
        pendingPlayUrl = url
        pendingPlayFileName = activePlaybackFileName
        Log.i(TAG, "repeat playback: file=${activePlaybackFileName ?: "null"} url=$url")
        val rc = upnpNative.nativeSetAVTransportURI(device.avTransportURL, url)
        if (rc != 0) {
            repeatPlaybackInFlight = false
            clearPendingPlayback()
            updateStatus("重播下发失败: rc=$rc")
        } else {
            updateStatus("已触发重播: ${activePlaybackFileName ?: url}")
        }
    }

    private fun seekToPosition(targetSec: Int) {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            resetProgressUi()
            return
        }
        val boundedTarget = if (currentDurationSec > 0) {
            targetSec.coerceIn(0, currentDurationSec)
        } else {
            targetSec.coerceAtLeast(0)
        }
        val rc = upnpNative.nativeSeek(dev.avTransportURL, boundedTarget)
        if (rc != 0) {
            updateProgressUi(currentPositionSec, currentDurationSec)
            updateStatus("Seek 失败: $rc")
            return
        }
        currentPositionSec = boundedTarget
        updateProgressUi(currentPositionSec, currentDurationSec)
        updateStatus("Seek 到 ${formatDuration(currentPositionSec)}")
        requestPositionUpdate(force = true)
    }

    private fun sendTransportAction(action: String) {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        val rc = when (action) {
            "Play" -> upnpNative.nativePlay(dev.avTransportURL)
            "Pause" -> upnpNative.nativePause(dev.avTransportURL)
            "Stop" -> upnpNative.nativeStop(dev.avTransportURL)
            else -> -1
        }
        updateStatus("$action 请求已发送，rc=$rc")
    }

    private fun adjustVolume(delta: Int) {
        val target = (currentVolume + delta).coerceIn(0, 100)
        setVolume(target)
    }

    private fun setVolume(volume: Int) {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        if (dev.renderingControlURL.isEmpty()) {
            toast("设备不支持音量控制")
            return
        }
        currentVolume = volume
        updateVolumeText()
        val rc = upnpNative.nativeSetVolume(dev.renderingControlURL, volume)
        updateStatus("SetVolume($volume) 请求已发送，rc=$rc")
    }

    private fun updateVolumeText() {
        volumeText.text = "当前音量: $currentVolume"
    }

    private fun updateProgressUi(positionSec: Int, durationSec: Int) {
        val safeDuration = durationSec.coerceAtLeast(0)
        val safePosition = positionSec.coerceIn(0, if (safeDuration > 0) safeDuration else Int.MAX_VALUE)
        currentDurationSec = safeDuration
        currentPositionSec = safePosition
        progressSeekBar.max = if (safeDuration > 0) safeDuration else 1
        if (!isUserSeeking) {
            progressSeekBar.progress = safePosition.coerceAtMost(progressSeekBar.max)
        }
        progressText.text = "播放进度: ${formatDuration(safePosition)} / ${formatDuration(safeDuration)}"
    }

    private fun resetProgressUi() {
        updateProgressUi(0, 0)
    }

    private fun formatDuration(totalSec: Int): String {
        val safe = totalSec.coerceAtLeast(0)
        val hh = safe / 3600
        val mm = (safe % 3600) / 60
        val ss = safe % 60
        return String.format("%02d:%02d:%02d", hh, mm, ss)
    }

    private fun requestPositionUpdate(force: Boolean = false) {
        val dev = getSelectedDevice() ?: return
        if (progressRequestInFlight && !force) {
            return
        }
        progressRequestInFlight = true
        positionExecutor.execute {
            val result = try {
                upnpNative.nativeGetPositionInfo(dev.avTransportURL)
            } catch (t: Throwable) {
                Log.w(TAG, "nativeGetPositionInfo failed", t)
                intArrayOf(-1, 0, 0)
            }
            mainHandler.post {
                progressRequestInFlight = false
                val rc = result.getOrNull(0) ?: -1
                if (rc != 0) {
                    return@post
                }
                val durationSec = result.getOrNull(1) ?: 0
                val positionSec = result.getOrNull(2) ?: 0
                if (!isUserSeeking) {
                    updateProgressUi(positionSec, durationSec)
                }
                triggerRepeatPlaybackIfNeeded(dev, durationSec, positionSec)
            }
        }
    }

    private fun updateCurrentMediaText(name: String?) {
        currentMediaText.text = if (name.isNullOrEmpty()) {
            "当前播放文件: 未播放"
        } else {
            "当前播放文件: $name"
        }
    }

    private fun updateDeviceAbilityText(raw: String?) {
        deviceAbilityText.text = if (raw.isNullOrBlank()) {
            "DevCapExt: 不支持"
        } else {
            "DevCapExt: $raw"
        }
    }

    private fun showDeviceAbilityDialog() {
        val dev = getSelectedDevice()
        val raw = dev?.deviceAbilityRaw
        val message = if (raw.isNullOrBlank()) {
            "当前选中设备未上报 DevCapExt，表示不支持该扩展能力描述。"
        } else {
            buildDeviceAbilityExplanation(raw)
        }

        val contentView = ScrollView(this).apply {
            val textView = TextView(context).apply {
                text = message
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(24, 16, 24, 16)
                setTextIsSelectable(true)
            }
            addView(textView)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("DevCapExt 解析")
            .setView(contentView)
            .setCancelable(true)
            .create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun showPlayableMediaListDialog() {
        val dev = getSelectedDevice() ?: run {
            toast("请先选择设备")
            return
        }
        val report = selectBestMedia(dev)
        Log.i(
            TAG,
            "playable media dialog: device=${dev.name} playable=${report.playable.size} active=${activePlaybackFileName ?: "null"} reason=${report.reason}"
        )
        if (report.playable.isEmpty()) {
            val contentView = ScrollView(this).apply {
                val textView = TextView(context).apply {
                    text = report.reason
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(24, 16, 24, 16)
                    setTextIsSelectable(true)
                }
                addView(textView)
            }
            AlertDialog.Builder(this)
                .setTitle("当前设备可播放列表")
                .setView(contentView)
                .setPositiveButton("关闭", null)
                .show()
            return
        }
        val labels = report.playable.map { it.file.name }
        val listView = ListView(this).apply {
            dividerHeight = 0
        }
        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.item_quality_option,
            R.id.qualityOptionTextView,
            labels
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(R.id.qualityOptionTextView)
                val check = view.findViewById<TextView>(R.id.qualityOptionCheckView)
                val media = report.playable[position]
                val isActive = media.file.name == activePlaybackFileName
                text.maxLines = 1
                text.ellipsize = android.text.TextUtils.TruncateAt.END
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                text.text = labels[position]
                text.setTextColor(if (isActive) 0xFF1B5E20.toInt() else 0xFF212121.toInt())
                check.visibility = if (isActive) android.view.View.VISIBLE else android.view.View.GONE
                view.setBackgroundColor(if (isActive) 0xFFE8F5E9.toInt() else 0xFFFFFFFF.toInt())
                view.isClickable = false
                view.isFocusable = false
                return view
            }
        }
        listView.adapter = adapter
        AlertDialog.Builder(this)
            .setTitle("当前设备可播放列表（${report.playable.size}）")
            .setView(listView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun buildDeviceAbilityExplanation(raw: String): String {
        val ability = parseDeviceAbility(raw) ?: return "DevCapExt 解析失败"
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 4 || ability.videoCapabilities.isEmpty() && parts.size > 4) {
            return "DevCapExt 解析失败"
        }

        val version = parseHexInt(parts[0])
        val feat = parseHexInt(parts[1])
        val audio = parseHexInt(parts[2])
        val container = parseHexInt(parts[3])
        val videos = ability.videoCapabilities

        return buildString {
            appendLine("版本: ${formatCompactHexField(version, parts[0])}")
            appendLine("feat: ${formatCompactFeatureField(feat, parts[1])}")
            appendLine("音频规格: ${formatCompactBitMaskField(audio, AUDIO_BIT_NAMES, parts[2])}")
            appendLine("容器规格: ${formatCompactBitMaskField(container, CONTAINER_BIT_NAMES, parts[3])}")
            if (videos.isNotEmpty()) {
                appendLine("视频规格:")
                videos.forEach { capability ->
                    appendLine(formatVideoSpec(capability))
                }
            } else {
                append("视频规格: 无")
            }
        }.trim()
    }

    private fun parseHexInt(raw: String): Int? {
        val normalized = raw.removePrefix("0x").removePrefix("0X")
        return normalized.toLongOrNull(16)?.toInt()
    }

    private fun parseHexLong(raw: String): Long? {
        val normalized = raw.removePrefix("0x").removePrefix("0X")
        return normalized.toLongOrNull(16)
    }

    private fun formatCompactHexField(value: Int?, fallbackRaw: String): String {
        return value?.toString() ?: fallbackRaw
    }

    private fun formatCompactFeatureField(value: Int?, fallbackRaw: String): String {
        if (value == null) {
            return fallbackRaw
        }
        if (value == 0) {
            return "无"
        }
        val enabledBits = (0 until 8).filter { ((value shr it) and 0x1) != 0 }
        return enabledBits.joinToString(", ") { "bit$it" }
    }

    private fun formatCompactBitMaskField(
        value: Int?,
        mapping: Map<Int, String>,
        fallbackRaw: String
    ): String {
        if (value == null) {
            return fallbackRaw
        }
        val names = mapping.filter { (bit, _) -> value and bit != 0 }.values.toList()
        if (names.isEmpty()) {
            return "无"
        }
        return names.joinToString(", ")
    }

    private fun formatVideoSpec(capability: DeviceVideoCapability): String {
        val codecText = capability.codec.displayName.uppercase(Locale.US)
        val sizeText = "${capability.decoderMaxWidth}x${capability.decoderMaxHeight}"
        val colorText = capability.supportedRanges
            .joinToString("、") { it.toDisplayName().uppercase(Locale.US) }
            .ifEmpty { "UNKNOWN" }
        val profileText = capability.profile.uppercase(Locale.US)
        val levelText = decodeLevel(capability.levelValue).uppercase(Locale.US)
        val tierText = decodeTier(capability.codec.ordinal, capability.tierValue).uppercase(Locale.US)
        return "$codecText | $sizeText | $colorText | $profileText | $levelText | $tierText"
    }

    private fun decodeCodec(value: Int): String {
        return parseVideoCodec(value).displayName
    }

    private fun decodeColor(value: Int): String {
        if (value == 0) {
            return "未知/未声明"
        }
        val names = mutableListOf<String>()
        if (value and 0x1 != 0) names.add("SDR")
        if (value and 0x2 != 0) names.add("HLG")
        if (value and 0x4 != 0) names.add("HDR10")
        if (value and 0x8 != 0) names.add("HDR10+")
        if (value and 0x10 != 0) names.add("HDR VIVID")
        if (value and 0x20 != 0) names.add("DOLBY VISION")
        return if (names.isEmpty()) "未知($value)" else names.joinToString(", ")
    }

    private fun decodeTier(codec: Int, value: Int): String {
        return when (codec) {
            0 -> "None"
            1, 2, 3 -> when (value) {
                0 -> "MT"
                1 -> "HT"
                else -> "未知($value)"
            }
            else -> "未知($value)"
        }
    }

    private fun decodeLevel(value: Int): String {
        return LEVEL_NAMES[value] ?: "未知($value)"
    }

    private fun decodeProfile(codec: Int, profile: Int): String {
        val name = when (codec) {
            0 -> when (profile) {
                1 -> "BP"
                2 -> "MP"
                3 -> "HP"
                4, 5 -> "HP10"
                else -> null
            }
            1 -> when (profile) {
                1 -> "MP"
                2 -> "MP10"
                3 -> "MP12"
                else -> null
            }
            2 -> when (profile) {
                1 -> "MP10"
                2 -> "MP12"
                else -> null
            }
            3 -> when (profile) {
                1 -> "MP"
                2 -> "HP"
                3 -> "Professional"
                else -> null
            }
            else -> null
        }
        return name ?: "未知($profile)"
    }

    private fun parseLocalMediaSpec(file: File, relativePath: String = file.name): LocalMediaSpec? {
        val match = MEDIA_NAME_REGEX.matchEntire(file.nameWithoutExtension) ?: return null
        val orientationToken = match.groupValues[1]
        val isPortrait = orientationToken.equals("portrait", ignoreCase = true)
        val resolutionToken = match.groupValues[2]
        val fps = match.groupValues[3].toDoubleOrNull() ?: return null
        val bitrateMbps = match.groupValues[4].toDoubleOrNull() ?: return null
        val videoCodec = parseVideoCodecToken(match.groupValues[5])
        val audioCodec = parseAudioCodecToken(match.groupValues[6])
        val dynamicRange = parseDynamicRangeToken(match.groupValues[7])
        val fallbackSize = resolutionTokenToSize(resolutionToken, isPortrait) ?: return null
        val actualSize = readVideoSize(file)
        val width = actualSize?.first ?: fallbackSize.first
        val height = actualSize?.second ?: fallbackSize.second
        Log.i(
            TAG,
            "media spec parse: file=${file.name} relativePath=$relativePath orientation=$orientationToken actual=${actualSize?.first ?: -1}x${actualSize?.second ?: -1} fallback=${fallbackSize.first}x${fallbackSize.second} final=${width}x${height}"
        )
        return LocalMediaSpec(
            file = file,
            relativePath = relativePath,
            container = file.extension.uppercase(Locale.US),
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            dynamicRange = dynamicRange,
            profile = guessMediaProfile(videoCodec, dynamicRange),
            width = width,
            height = height,
            fps = fps,
            bitrateMbps = bitrateMbps
        )
    }

    private fun readVideoSize(file: File): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0) {
                width to height
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readVideoSize failed: ${file.name}", t)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun resolutionTokenToSize(token: String, isPortrait: Boolean = false): Pair<Int, Int>? {
        val landscapeSize = when (token.lowercase(Locale.US)) {
            "480p" -> 854 to 480
            "540p" -> 960 to 540
            "720p" -> 1280 to 720
            "1080p" -> 1920 to 1080
            "2k" -> 2560 to 1440
            "4k" -> 3840 to 2160
            else -> null
        } ?: return null
        return if (isPortrait) {
            landscapeSize.second to landscapeSize.first
        } else {
            landscapeSize
        }
    }

    private fun guessMediaProfile(codec: VideoCodec, dynamicRange: DynamicRange): String {
        return when (codec) {
            VideoCodec.AVC -> "HP"
            VideoCodec.HEVC -> if (dynamicRange == DynamicRange.SDR) "MP" else "MP10"
            VideoCodec.VVC -> "MP10"
            VideoCodec.AV1 -> "MP"
            VideoCodec.UNKNOWN -> "未知"
        }
    }

    private fun parseDeviceAbility(raw: String): ParsedDeviceAbility? {
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 4) {
            return null
        }
        val audioMask = parseHexInt(parts[2]) ?: return null
        val containerMask = parseHexInt(parts[3]) ?: return null
        val videoCapabilitiesRaw = parts.drop(4)
        val videoCapabilities = mutableListOf<DeviceVideoCapability>()
        for (capabilityRaw in videoCapabilitiesRaw) {
            val capability = parseDeviceVideoCapability(capabilityRaw) ?: return null
            videoCapabilities.add(capability)
        }
        return ParsedDeviceAbility(
            containers = decodeContainerSet(containerMask),
            audioCodecs = decodeAudioCodecSet(audioMask),
            videoCapabilities = videoCapabilities
        )
    }

    private fun parseDeviceVideoCapability(raw: String): DeviceVideoCapability? {
        val pair = raw.split("_")
        if (pair.size != 2) {
            return null
        }
        val mask = parseHexLong(pair[0]) ?: return null
        val sizeRaw = pair[1]
        if (sizeRaw.length < 8) {
            return null
        }
        val width = parseHexInt(sizeRaw.substring(0, 4)) ?: return null
        val height = parseHexInt(sizeRaw.substring(4, 8)) ?: return null
        val codecValue = (mask and 0xFL).toInt()
        val codec = parseVideoCodec(codecValue)
        val rangeMask = ((mask ushr 4) and 0x3FL).toInt()
        val tierValue = ((mask ushr 10) and 0x1L).toInt()
        val levelValue = ((mask ushr 11) and 0x1FL).toInt()
        val profileValue = ((mask ushr 16) and 0x1FL).toInt()
        val extensionValue = ((mask ushr 21) and 0x7FFL).toInt()
        val profileName = decodeProfile(codecValue, profileValue)
        val limits = if (codec == VideoCodec.UNKNOWN) {
            null
        } else {
            lookupLevelTierLimit(codec, levelValue, tierValue)
        }
        val capability = DeviceVideoCapability(
            raw = raw,
            codec = codec,
            supportedRanges = decodeDynamicRangeSet(rangeMask),
            profile = profileName,
            profileValue = profileValue,
            levelValue = levelValue,
            tierValue = tierValue,
            extensionValue = extensionValue,
            decoderMaxWidth = width,
            decoderMaxHeight = height,
            levelMaxFrameArea = limits?.maxArea,
            levelMaxPixelsPerSecond = limits?.maxPixelsPerSecond,
            levelReferenceFps = limits?.maxFps,
            maxBitrateMbps = limits?.maxBitrateMbps(tierValue)
        )
        Log.i(
            TAG,
            "DevCapExt video capability: ${formatVideoSpec(capability)} raw=$raw ext=${capability.extensionValue}"
        )
        return capability
    }

    private fun decodeContainerSet(mask: Int): Set<String> {
        val results = linkedSetOf<String>()
        CONTAINER_BIT_NAMES.forEach { (bit, name) ->
            if (mask and bit != 0) {
                results.add(name)
            }
        }
        return results
    }

    private fun decodeAudioCodecSet(mask: Int): Set<AudioCodec> {
        val results = linkedSetOf<AudioCodec>()
        if (mask and 0x0001 != 0) results.add(AudioCodec.AAC)
        if (mask and 0x0002 != 0) results.add(AudioCodec.AC3)
        if (mask and 0x0004 != 0) results.add(AudioCodec.EAC3)
        if (mask and 0x0008 != 0) results.add(AudioCodec.DTS)
        if (mask and 0x0010 != 0) results.add(AudioCodec.AAC_HE)
        if (mask and 0x0020 != 0) results.add(AudioCodec.AAC_HE_V2)
        return results
    }

    private fun decodeDynamicRangeSet(mask: Int): Set<DynamicRange> {
        val results = linkedSetOf<DynamicRange>()
        if (mask and 0x1 != 0) results.add(DynamicRange.SDR)
        if (mask and 0x2 != 0) results.add(DynamicRange.HLG)
        if (mask and 0x4 != 0) results.add(DynamicRange.HDR10)
        if (mask and 0x8 != 0) results.add(DynamicRange.HDR10PLUS)
        if (mask and 0x10 != 0) results.add(DynamicRange.HDR_VIVID)
        if (mask and 0x20 != 0) results.add(DynamicRange.DOLBY_VISION)
        return results
    }

    private fun lookupLevelTierLimit(
        codec: VideoCodec,
        levelValue: Int,
        tierValue: Int
    ): LevelTierLimit? {
        val table = when (codec) {
            VideoCodec.AVC -> AVC_LIMITS
            VideoCodec.HEVC -> HEVC_LIMITS
            VideoCodec.VVC -> VVC_LIMITS
            VideoCodec.AV1 -> AV1_LIMITS
            VideoCodec.UNKNOWN -> emptyMap<Int, LevelTierLimit>()
        }
        return table[levelValue] ?: run {
            Log.w(
                TAG,
                "DevCapExt unknown level/tier: codec=${codec.displayName} level=$levelValue tier=$tierValue"
            )
            null
        }
    }

    private fun selectBestMedia(device: DeviceEntry): MediaSelectionReport {
        val ability = parseDeviceAbility(device.deviceAbilityRaw)
        if (ability == null) {
            val fallbackPlayable = buildNoDeviceAbilityFallbackPlayable()
            val fallbackSelected = fallbackPlayable.firstOrNull()
            val report = MediaSelectionReport(
                playable = fallbackPlayable,
                selected = fallbackSelected,
                reason = if (fallbackSelected == null) {
                    "设备未上报可解析的 DevCapExt，且本地无 H264/AVC 1080P 及以下可播资源"
                } else {
                    "设备未上报可解析的 DevCapExt，回退到默认 H264/AVC 逻辑（最高 1080P，向下选择），候选=${fallbackPlayable.size}"
                }
            )
            Log.i(
                "DlnaExpandTest",
                "selectBestMedia fallback: device=${device.name} raw=${device.deviceAbilityRaw} playable=${fallbackPlayable.size} selected=${fallbackSelected?.file?.name ?: "null"} reason=${report.reason}"
            )
            return report
        }

        val playable = localMediaSpecs.filter { media ->
            if (!ability.containers.contains(media.container)) {
                return@filter false
            }
            if (!ability.audioCodecs.contains(media.audioCodec)) {
                return@filter false
            }
            ability.videoCapabilities.any { capability ->
                isMediaCompatible(media, capability)
            }
        }.sortedWith(
            compareByDescending<LocalMediaSpec> { it.area }
                .thenByDescending { it.videoCodec.sortPriority }
                .thenByDescending { it.fps }
                .thenByDescending { it.bitrateMbps }
                .thenBy { it.file.name }
        )

        val selected = playable.firstOrNull()
        val reason = if (selected == null) {
            "无兼容资源: 本地=${localMediaSpecs.size}，设备=${device.name}"
        } else {
            "优选资源: ${selected.file.name}，候选=${playable.size}，按分辨率/编码/码率/FPS/色彩模式排序"
        }
        Log.i(
            "DlnaExpandTest",
            "selectBestMedia ability: device=${device.name} raw=${device.deviceAbilityRaw} playable=${playable.size} selected=${selected?.file?.name ?: "null"} reason=$reason"
        )
        return MediaSelectionReport(playable = playable, selected = selected, reason = reason)
    }

    private fun buildNoDeviceAbilityFallbackPlayable(): List<LocalMediaSpec> {
        val maxFallbackArea = 1920L * 1080L
        return localMediaSpecs
            .filter { media ->
                media.videoCodec == VideoCodec.AVC &&
                    media.area <= maxFallbackArea
            }
            .sortedWith(mediaPriorityComparator())
    }

    private fun buildResolutionOptions(playable: List<LocalMediaSpec>): List<ResolutionOption> {
        val bestByBucket = linkedMapOf<String, LocalMediaSpec>()
        val bucketPriorityByName = mutableMapOf<String, Int>()
        playable.forEach { media ->
            val bucket = resolveQualityBucket(media)
            if (!bestByBucket.containsKey(bucket.displayName)) {
                bestByBucket[bucket.displayName] = media
                bucketPriorityByName[bucket.displayName] = bucket.sortPriority
            }
        }
        return bestByBucket.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, LocalMediaSpec>> {
                    bucketPriorityByName[it.key] ?: 0
                }.thenByDescending { it.value.area }
            )
            .map { (bucketName, media) ->
            ResolutionOption(
                bucketName = bucketName,
                media = media,
                label = buildResolutionOptionLabel(bucketName, media)
            )
            }
    }

    private fun findBestLocalMedia(): LocalMediaSpec? {
        return localMediaSpecs
            .sortedWith(mediaPriorityComparator())
            .firstOrNull()
    }

    private fun findRandomLocalMedia(): LocalMediaSpec? {
        if (localMediaSpecs.isEmpty()) {
            return null
        }
        val previous = lastRandomMediaFileName
        val candidates = if (previous.isNullOrEmpty()) {
            localMediaSpecs
        } else {
            val filtered = localMediaSpecs.filterNot { it.file.name == previous }
            if (filtered.isNotEmpty()) filtered else localMediaSpecs
        }
        val picked = candidates.random()
        Log.i(
            TAG,
            "findRandomLocalMedia: previous=${previous ?: "null"} candidates=${candidates.size} picked=${picked.file.name}"
        )
        return picked
    }

    private fun mediaPriorityComparator(): Comparator<LocalMediaSpec> {
        return compareByDescending<LocalMediaSpec> { it.area }
            .thenByDescending { it.videoCodec.sortPriority }
            .thenByDescending { it.bitrateMbps }
            .thenByDescending { it.fps }
            .thenByDescending { dynamicRangeSortPriority(it.dynamicRange) }
            .thenBy { it.file.name }
    }

    private fun resolveQualityBucket(media: LocalMediaSpec): QualityBucket {
        val major = maxOf(media.width, media.height)
        val minor = minOf(media.width, media.height)
        return QualityBucket.values().firstOrNull { bucket ->
            bucket.majorSize == major && bucket.minorSize == minor
        } ?: when {
            major >= 3840 && minor >= 2160 -> QualityBucket.K4
            major >= 2560 && minor >= 1440 -> QualityBucket.K2
            major >= 1920 && minor >= 1080 -> QualityBucket.P1080
            major >= 1280 && minor >= 720 -> QualityBucket.P720
            major >= 960 && minor >= 540 -> QualityBucket.P540
            else -> QualityBucket.P480
        }
    }

    private fun dynamicRangeSortPriority(dynamicRange: DynamicRange): Int {
        return when (dynamicRange) {
            DynamicRange.DOLBY_VISION -> 6
            DynamicRange.HDR_VIVID -> 5
            DynamicRange.HDR10PLUS -> 4
            DynamicRange.HLG -> 3
            DynamicRange.HDR10 -> 2
            DynamicRange.SDR -> 1
            DynamicRange.UNKNOWN -> 0
        }
    }

    private fun buildResolutionOptionLabel(bucketName: String, media: LocalMediaSpec): String {
        val fpsText = if (kotlin.math.abs(media.fps - media.fps.toInt()) < 0.001) {
            "${media.fps.toInt()}fps"
        } else {
            String.format(Locale.US, "%.1ffps", media.fps)
        }
        val bitrateText = if (kotlin.math.abs(media.bitrateMbps - media.bitrateMbps.toInt()) < 0.001) {
            "${media.bitrateMbps.toInt()}M"
        } else {
            String.format(Locale.US, "%.1fM", media.bitrateMbps)
        }
        return "$bucketName | ${media.videoCodec.displayName} | ${dynamicRangeLabel(media.dynamicRange)} | $fpsText | $bitrateText"
    }

    private fun buildPlayableMediaListLabel(media: LocalMediaSpec): String {
        return media.file.name
    }

    private fun dynamicRangeLabel(dynamicRange: DynamicRange): String {
        return when (dynamicRange) {
            DynamicRange.SDR -> "SDR"
            DynamicRange.HLG -> "HLG"
            DynamicRange.HDR10 -> "HDR10"
            DynamicRange.HDR10PLUS -> "HDR10+"
            DynamicRange.HDR_VIVID -> "HDR VIVID"
            DynamicRange.DOLBY_VISION -> "DOLBY VISION"
            DynamicRange.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun isMediaCompatible(
        media: LocalMediaSpec,
        capability: DeviceVideoCapability
    ): Boolean {
        if (media.videoCodec != capability.codec) {
            return false
        }
        if (!capability.supportedRanges.contains(media.dynamicRange)) {
            return false
        }
        if (!isProfileCompatible(media.videoCodec, media.profile, capability.profile)) {
            return false
        }
        if (media.width > capability.decoderMaxWidth || media.height > capability.decoderMaxHeight) {
            return false
        }
        val levelMaxFrameArea = capability.levelMaxFrameArea ?: return false
        if (media.area > levelMaxFrameArea) {
            return false
        }
        val levelMaxPixelsPerSecond = capability.levelMaxPixelsPerSecond ?: return false
        if (media.pixelsPerSecond > levelMaxPixelsPerSecond + 0.001) {
            return false
        }
        val maxBitrateMbps = capability.maxBitrateMbps ?: return false
        if (media.bitrateMbps > maxBitrateMbps + 0.001) {
            return false
        }
        return true
    }

    private fun isProfileCompatible(codec: VideoCodec, mediaProfile: String, deviceProfile: String): Boolean {
        val mediaRank = profileRank(codec, mediaProfile) ?: return false
        val deviceRank = profileRank(codec, deviceProfile) ?: return false
        return mediaRank <= deviceRank
    }

    private fun profileRank(codec: VideoCodec, profile: String): Int? {
        return when (codec) {
            VideoCodec.AVC -> when (profile) {
                "BP" -> 1
                "MP" -> 2
                "HP" -> 3
                "HP10" -> 4
                else -> null
            }
            VideoCodec.HEVC -> when (profile) {
                "MP" -> 1
                "MP10" -> 2
                "MP12" -> 3
                else -> null
            }
            VideoCodec.VVC -> when (profile) {
                "MP10" -> 1
                "MP12" -> 2
                else -> null
            }
            VideoCodec.AV1 -> when (profile) {
                "MP" -> 1
                "HP" -> 2
                "Professional" -> 3
                else -> null
            }
            VideoCodec.UNKNOWN -> null
        }
    }

    private fun parseVideoCodec(value: Int): VideoCodec {
        return when (value) {
            0 -> VideoCodec.AVC
            1 -> VideoCodec.HEVC
            2 -> VideoCodec.VVC
            3 -> VideoCodec.AV1
            else -> VideoCodec.UNKNOWN
        }
    }

    private fun parseVideoCodecToken(token: String): VideoCodec {
        return when (token.lowercase(Locale.US)) {
            "avc" -> VideoCodec.AVC
            "hevc" -> VideoCodec.HEVC
            "vvc" -> VideoCodec.VVC
            "av1" -> VideoCodec.AV1
            else -> VideoCodec.UNKNOWN
        }
    }

    private fun parseAudioCodecToken(token: String): AudioCodec {
        return when (token.lowercase(Locale.US)) {
            "aac" -> AudioCodec.AAC
            "ac3" -> AudioCodec.AC3
            "eac3" -> AudioCodec.EAC3
            "dts" -> AudioCodec.DTS
            "aac_he" -> AudioCodec.AAC_HE
            "aac_he_v2" -> AudioCodec.AAC_HE_V2
            else -> AudioCodec.UNKNOWN
        }
    }

    private fun parseDynamicRangeToken(token: String): DynamicRange {
        return when (token.lowercase(Locale.US)) {
            "sdr" -> DynamicRange.SDR
            "hlg" -> DynamicRange.HLG
            "hdr10" -> DynamicRange.HDR10
            "hdr10plus" -> DynamicRange.HDR10PLUS
            "hdrvivid", "hdr_vivid" -> DynamicRange.HDR_VIVID
            "dolbyvision", "dolby_vision", "dv" -> DynamicRange.DOLBY_VISION
            else -> DynamicRange.UNKNOWN
        }
    }

    private fun DynamicRange.toDisplayName(): String {
        return when (this) {
            DynamicRange.SDR -> "SDR"
            DynamicRange.HLG -> "HLG"
            DynamicRange.HDR10 -> "HDR10"
            DynamicRange.HDR10PLUS -> "HDR10+"
            DynamicRange.HDR_VIVID -> "HDR VIVID"
            DynamicRange.DOLBY_VISION -> "DOLBY VISION"
            DynamicRange.UNKNOWN -> "未知"
        }
    }

    private fun refreshDeviceList() {
        val selectedUdn = selectedDeviceUdn
        deviceAdapter.clear()
        devices.forEach { device -> deviceAdapter.add(device.name) }

        val selectedIndex = devices.indexOfFirst { it.udn == selectedUdn }
        if (selectedIndex >= 0) {
            deviceListView.setItemChecked(selectedIndex, true)
        } else {
            selectedDeviceUdn = null
            deviceListView.clearChoices()
        }
    }

    private fun pruneExpiredDevices() {
        val now = System.currentTimeMillis()
        val removed = devices.filter { it.expiresAtMs in 1 until now }
        if (removed.isEmpty()) {
            return
        }
        devices.removeAll(removed.toSet())
        if (removed.any { it.udn == selectedDeviceUdn }) {
            selectedDeviceUdn = null
        }
        refreshDeviceList()
        updateStatus("设备下线，当前设备数: ${devices.size}")
    }

    override fun onDeviceFound(
        udn: String,
        friendlyName: String,
        location: String,
        avTransportControlURL: String,
        renderingControlControlURL: String,
        deviceAbilityRaw: String,
        expiresSeconds: Int
    ) {
        mainHandler.post {
            val ttlSec = if (expiresSeconds > 0) expiresSeconds else DEFAULT_EXPIRES_SEC
            val expiresAtMs = System.currentTimeMillis() + ttlSec * 1000L
            val existing = devices.firstOrNull { it.udn == udn }
            if (existing == null) {
                devices.add(
                    DeviceEntry(
                        udn = udn,
                        name = friendlyName.ifEmpty { udn },
                        location = location,
                        avTransportURL = avTransportControlURL,
                        renderingControlURL = renderingControlControlURL,
                        deviceAbilityRaw = deviceAbilityRaw,
                        expiresAtMs = expiresAtMs
                    )
                )
                refreshDeviceList()
                updateStatus("发现设备: ${friendlyName.ifEmpty { udn }} (共 ${devices.size})")
            } else {
                existing.name = friendlyName.ifEmpty { udn }
                existing.location = location
                existing.avTransportURL = avTransportControlURL
                existing.renderingControlURL = renderingControlControlURL
                existing.deviceAbilityRaw = deviceAbilityRaw
                existing.expiresAtMs = expiresAtMs
                refreshDeviceList()
            }
            if (selectedDeviceUdn == udn) {
                updateDeviceAbilityText(deviceAbilityRaw)
            }
        }
    }

    override fun onDeviceLost(udn: String) {
        mainHandler.post {
            val removed = devices.removeAll { it.udn == udn }
            if (removed) {
                if (selectedDeviceUdn == udn) {
                    selectedDeviceUdn = null
                    updateDeviceAbilityText(null)
                }
                refreshDeviceList()
                updateStatus("设备已下线，当前设备数: ${devices.size}")
            }
        }
    }

    override fun onActionResult(action: String, errCode: Int, message: String) {
        mainHandler.post {
            if (action == "SetAVTransportURI" && errCode == 0) {
                val dev = getSelectedDevice()
                if (dev != null && dev.udn == pendingPlayDeviceUdn) {
                    val rc = upnpNative.nativePlay(dev.avTransportURL)
                    updateStatus("SetAVTransportURI 成功，开始播放，rc=$rc")
                    if (rc != 0) {
                        clearPendingPlayback()
                    }
                } else {
                    updateStatus("SetAVTransportURI 成功，但目标设备已变化")
                    clearPendingPlayback()
                }
                return@post
            }

            if (action == "Play" && errCode == 0 && pendingPlayUrl != null) {
                activePlaybackDeviceUdn = pendingPlayDeviceUdn
                activePlaybackUrl = pendingPlayUrl
                activePlaybackFileName = pendingPlayFileName
                isPlaybackPaused = false
                repeatPlaybackInFlight = false
                updateStatus("投屏播放成功: $pendingPlayUrl")
                clearPendingPlayback()
                requestPositionUpdate(force = true)
                return@post
            }

            if (action == "Play" && errCode == 0) {
                isPlaybackPaused = false
                repeatPlaybackInFlight = false
                updateStatus("恢复播放成功")
                requestPositionUpdate(force = true)
                return@post
            }

            if (action == "SetAVTransportURI" && errCode != 0) {
                repeatPlaybackInFlight = false
                clearPendingPlayback()
                updateCurrentMediaText(null)
            }

            if (action == "Pause" && errCode == 0) {
                isPlaybackPaused = true
                updateStatus("已暂停")
                return@post
            }

            if (action == "Stop" && errCode == 0) {
                clearPendingPlayback()
                clearActivePlayback(resetMediaText = false)
                resetProgressUi()
                updateStatus("已停止")
                return@post
            }

            if (action == "Play" && errCode != 0) {
                repeatPlaybackInFlight = false
            }

            updateStatus("[$action] errCode=$errCode $message")
        }
    }

    private fun updateStatus(msg: String) {
        Log.i(TAG, msg)
        statusText.text = msg
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(deviceCleanupRunnable)
        mainHandler.removeCallbacks(progressPollRunnable)
        try {
            httpServer.stop()
        } catch (_: Exception) {
        }
        try {
            upnpNative.nativeFinish()
        } catch (_: Exception) {
        }
        try {
            multicastLock?.release()
        } catch (_: Exception) {
        }
        positionExecutor.shutdownNow()
        super.onDestroy()
    }
}
