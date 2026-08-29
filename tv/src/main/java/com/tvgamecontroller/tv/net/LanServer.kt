package com.tvgamecontroller.tv.net

import android.content.res.AssetManager
import com.tvgamecontroller.protocol.BinaryCodec
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.JsonCodec
import com.tvgamecontroller.protocol.WebSocketFrame
import com.tvgamecontroller.protocol.WebSocketFrames
import com.tvgamecontroller.protocol.WebSocketHandshake
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LanServer(
    private val port: Int,
    private val assets: AssetManager,
    private val pin: String,
    private val serverName: String,
    private val onState: (GamepadState, String) -> Unit,
    private val onClientsChanged: (List<String>) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private val clients = CopyOnWriteArrayList<Client>()
    private var server: ServerSocket? = null

    fun start() {
        if (running.getAndSet(true)) return
        pool.execute {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(port))
                server = socket
                while (running.get()) {
                    val client = socket.accept()
                    pool.execute { handle(client) }
                }
            } catch (_: Exception) {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        clients.forEach { runCatching { it.socket.close() } }
        clients.clear()
        runCatching { server?.close() }
        onClientsChanged(emptyList())
    }

    fun rumble(low: Float, high: Float, ms: Int) {
        val payload = WebSocketFrames.text(JsonCodec.rumble(low, high, ms))
        clients.forEach { it.send(payload) }
    }

    private fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        val input = BufferedInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val request = readHttp(input)
        val line = WebSocketHandshake.requestLine(request) ?: run {
            socket.close()
            return
        }
        val path = line.second.substringBefore('?').ifBlank { "/" }
        val headers = WebSocketHandshake.parseHeaders(request)
        val isSocket = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
        if (isSocket && (path == "/controller" || path == "/")) {
            serveWebSocket(socket, input, output, headers)
        } else {
            serveFile(output, path)
            socket.close()
        }
    }

    private fun serveWebSocket(
        socket: Socket,
        input: BufferedInputStream,
        output: OutputStream,
        headers: Map<String, String>,
    ) {
        val key = headers["sec-websocket-key"] ?: return
        output.write(WebSocketHandshake.upgradeResponse(key).toByteArray(Charsets.US_ASCII))
        output.flush()
        output.write(WebSocketFrames.text(JsonCodec.welcome(true, serverName)))
        output.flush()
        val client = Client(socket, output, "Phone")
        var authed = pin.isBlank()
        if (authed) {
            clients += client
            output.write(WebSocketFrames.text(JsonCodec.ready(serverName)))
            output.flush()
            onClientsChanged(clients.map { it.name })
        }
        try {
            while (running.get() && !socket.isClosed) {
                val frame = WebSocketFrames.decode(input) ?: break
                when (frame.opcode) {
                    WebSocketFrame.OP_CLOSE -> break
                    WebSocketFrame.OP_PING -> client.send(WebSocketFrames.encode(WebSocketFrame(WebSocketFrame.OP_PONG, frame.payload)))
                    WebSocketFrame.OP_TEXT -> {
                        val text = frame.text
                        when (JsonCodec.messageType(text)) {
                            "hello" -> client.name = JsonCodec.stringField(text, "name") ?: "Phone"
                            "auth" -> {
                                val sent = JsonCodec.stringField(text, "pin").orEmpty()
                                if (sent == pin || pin.isBlank()) {
                                    authed = true
                                    if (!clients.contains(client)) clients += client
                                    client.send(WebSocketFrames.text(JsonCodec.ready(serverName)))
                                    onClientsChanged(clients.map { it.name })
                                } else {
                                    client.send(WebSocketFrames.text(JsonCodec.error("Wrong PIN")))
                                }
                            }
                            "state" -> if (authed) {
                                JsonCodec.parseState(text)?.let { onState(it, client.name) }
                            }
                        }
                    }
                    WebSocketFrame.OP_BINARY -> if (authed) {
                        BinaryCodec.decode(frame.payload)?.let { onState(it, client.name) }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            clients.remove(client)
            runCatching { socket.close() }
            onClientsChanged(clients.map { it.name })
        }
    }

    private fun serveFile(output: OutputStream, rawPath: String) {
        val path = when {
            rawPath == "/" || rawPath.isBlank() -> "web/index.html"
            rawPath.startsWith("/") -> "web${rawPath}"
            else -> "web/$rawPath"
        }
        val bytes = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            val body = "Not found"
            output.write("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
            return
        }
        val type = when {
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".js") -> "text/javascript; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".png") -> "image/png"
            else -> "application/octet-stream"
        }
        output.write(
            "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n".toByteArray(),
        )
        output.write(bytes)
        output.flush()
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

    private class Client(
        val socket: Socket,
        private val output: OutputStream,
        var name: String,
    ) {
        fun send(bytes: ByteArray) {
            synchronized(output) {
                runCatching {
                    output.write(bytes)
                    output.flush()
                }
            }
        }
    }
}
