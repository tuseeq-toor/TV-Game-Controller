package com.tvgamecontroller.mobile.net

import com.tvgamecontroller.protocol.BinaryCodec
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.JsonCodec
import com.tvgamecontroller.protocol.WebSocketFrame
import com.tvgamecontroller.protocol.WebSocketFrames
import com.tvgamecontroller.protocol.WebSocketHandshake
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TvConnection(
    private val onReady: (String) -> Unit,
    private val onClosed: (String) -> Unit,
    private val onRumble: (Float, Float, Int) -> Unit,
) {
    private val io = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val writeLock = Any()
    private var socket: Socket? = null
    private var output: OutputStream? = null

    fun connect(host: String, port: Int, pin: String, deviceName: String) {
        close("reconnecting")
        running.set(true)
        io.execute {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(host, port), 4000)
                socket = sock
                val input = BufferedInputStream(sock.getInputStream())
                val out = sock.getOutputStream()
                output = out
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
                val request = buildString {
                    append("GET /controller HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $key\r\n")
                    append("Sec-WebSocket-Version: 13\r\n\r\n")
                }
                out.write(request.toByteArray(Charsets.US_ASCII))
                out.flush()
                val response = readHttp(input)
                val accept = WebSocketHandshake.parseHeaders(response)["sec-websocket-accept"]
                if (accept != WebSocketHandshake.acceptKey(key)) {
                    throw IllegalStateException("WebSocket handshake failed")
                }
                sendText(JsonCodec.hello(deviceName))
                sendText(JsonCodec.auth(pin))
                while (running.get()) {
                    val frame = WebSocketFrames.decode(input) ?: break
                    when (frame.opcode) {
                        WebSocketFrame.OP_TEXT -> handleText(frame.text)
                        WebSocketFrame.OP_PING -> sendRaw(WebSocketFrames.encode(
                            WebSocketFrame(WebSocketFrame.OP_PONG, frame.payload),
                            masked = true,
                        ))
                        WebSocketFrame.OP_CLOSE -> {
                            running.set(false)
                        }
                    }
                }
                onClosed("Disconnected")
            } catch (error: Exception) {
                if (running.get()) {
                    onClosed(error.message ?: "Connection failed")
                }
            } finally {
                runCatching { socket?.close() }
                socket = null
                output = null
                running.set(false)
            }
        }
    }

    fun sendState(state: GamepadState) {
        sendRaw(WebSocketFrames.binary(BinaryCodec.encode(state), masked = true))
    }

    fun close(reason: String = "closed") {
        running.set(false)
        runCatching { socket?.close() }
        if (reason != "reconnecting") {
            onClosed(reason)
        }
    }

    private fun handleText(text: String) {
        when (JsonCodec.messageType(text)) {
            "welcome" -> Unit
            "ready" -> onReady(JsonCodec.stringField(text, "serverName") ?: "Android TV")
            "error" -> onClosed(JsonCodec.stringField(text, "message") ?: "Server error")
            "rumble" -> onRumble(
                JsonCodec.floatField(text, "low") ?: 0.4f,
                JsonCodec.floatField(text, "high") ?: 0.7f,
                JsonCodec.intField(text, "ms") ?: 60,
            )
        }
    }

    private fun sendText(text: String) {
        sendRaw(WebSocketFrames.text(text, masked = true))
    }

    private fun sendRaw(bytes: ByteArray) {
        synchronized(writeLock) {
            try {
                output?.write(bytes)
                output?.flush()
            } catch (_: Exception) {
                running.set(false)
            }
        }
    }

    private fun readHttp(input: BufferedInputStream): String {
        val buffer = StringBuilder()
        while (!buffer.contains("\r\n\r\n")) {
            val ch = input.read()
            if (ch < 0) break
            buffer.append(ch.toChar())
        }
        return buffer.toString()
    }
}
