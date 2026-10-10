// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

package com.example.upnpdemo

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简本地 HTTP 文件服务器
 * - 服务一个目录下的文件（供 DMR 拉流）
 * - 支持 HEAD / GET / Range（大多数 DMR 需要）
 * - 不依赖第三方库
 *
 * 安全提示：仅作为演示 demo，服务接口未鉴权。为使局域网内的 DMR（电视等）
 * 能够拉流，服务监听全部网卡；请勿用于生产环境。
 */
class LocalHttpServer(
    private val rootDir: File,
    private val port: Int = 8080
) {
    companion object { private const val TAG = "LocalHttpServer" }

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    val listeningPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start(): Int {
        if (running.get()) return listeningPort
        serverSocket = tryBindServerSocket()
        running.set(true)
        val actualPort = serverSocket?.localPort ?: -1
        Thread {
            Log.i(TAG, "HTTP server started at port $actualPort, root=${rootDir.absolutePath}")
            while (running.get()) {
                try {
                    val client = serverSocket?.accept() ?: break
                    executor.submit { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "accept error", e)
                }
            }
        }.start()
        return actualPort
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { sock ->
                val input = sock.getInputStream()
                val reader = input.bufferedReader()
                val requestLine = reader.readLine() ?: return
                Log.d(TAG, "Request: $requestLine")

                val parts = requestLine.split(" ")
                if (parts.size < 3) return
                val method = parts[0]
                val rawPath = parts[1]

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(":")
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                }

                val decodedPath = java.net.URLDecoder.decode(rawPath, "UTF-8").trimStart('/')
                val file = File(rootDir, decodedPath)
                if (!file.exists() || !file.isFile) {
                    writeStatus(sock.getOutputStream(), 404, "Not Found")
                    return
                }

                val rangeHeader = headers["range"]
                serveFile(sock.getOutputStream(), file, method, rangeHeader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleClient error", e)
        }
    }

    private fun serveFile(out: OutputStream, file: File, method: String, range: String?) {
        val total = file.length()
        var start = 0L
        var end = total - 1
        var partial = false

        if (range != null && range.startsWith("bytes=")) {
            val span = range.substring("bytes=".length)
            val pos = span.indexOf('-')
            if (pos >= 0) {
                val s = span.substring(0, pos)
                val e = span.substring(pos + 1)
                if (s.isNotEmpty()) start = s.toLong()
                if (e.isNotEmpty()) end = e.toLong()
                partial = true
            }
        }
        if (end >= total) end = total - 1
        val contentLen = end - start + 1

        val mime = guessMime(file.name)
        val pw = PrintWriter(out)
        val statusLine = if (partial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
        pw.print("$statusLine\r\n")
        pw.print("Content-Type: $mime\r\n")
        pw.print("Content-Length: $contentLen\r\n")
        pw.print("Accept-Ranges: bytes\r\n")
        if (partial) {
            pw.print("Content-Range: bytes $start-$end/$total\r\n")
        }
        pw.print("Connection: close\r\n")
        pw.print("\r\n")
        pw.flush()

        if (method.equals("HEAD", ignoreCase = true)) return

        FileInputStream(file).use { fis ->
            fis.skip(start)
            val buf = ByteArray(64 * 1024)
            var remain = contentLen
            while (remain > 0) {
                val toRead = if (remain > buf.size) buf.size else remain.toInt()
                val n = fis.read(buf, 0, toRead)
                if (n <= 0) break
                try {
                    out.write(buf, 0, n)
                } catch (e: IOException) {
                    return
                }
                remain -= n
            }
            out.flush()
        }
    }

    private fun writeStatus(out: OutputStream, code: Int, msg: String) {
        val pw = PrintWriter(out)
        pw.print("HTTP/1.1 $code $msg\r\n")
        pw.print("Content-Length: 0\r\n")
        pw.print("Connection: close\r\n\r\n")
        pw.flush()
    }

    private fun tryBindServerSocket(): ServerSocket {
        // 安全提示：仅作为演示 demo，此 HTTP 服务接口未鉴权。
        // DLNA 投屏需要局域网内的 DMR（电视等）从本机 IP 拉流，故绑定所有网卡。
        // 请勿在生产环境使用，也不要在此基础上扩大暴露面（本已监听全部网卡）。
        return try {
            ServerSocket(port)
        } catch (e: BindException) {
            Log.w(TAG, "preferred port $port is busy, fallback to ephemeral port")
            ServerSocket(0)
        }
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".flv") -> "video/x-flv"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }
}
