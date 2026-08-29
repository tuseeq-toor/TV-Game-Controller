package com.tvgamecontroller.protocol

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

object WebSocketHandshake {
    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun acceptKey(clientKey: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest((clientKey.trim() + GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(hash)
    }

    fun upgradeResponse(clientKey: String): String = buildString {
        append("HTTP/1.1 101 Switching Protocols\r\n")
        append("Upgrade: websocket\r\n")
        append("Connection: Upgrade\r\n")
        append("Sec-WebSocket-Accept: ").append(acceptKey(clientKey)).append("\r\n")
        append("\r\n")
    }

    fun parseHeaders(request: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        request.split("\r\n").drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        return headers
    }

    fun requestLine(request: String): Triple<String, String, String>? {
        val line = request.substringBefore("\r\n")
        val parts = line.split(' ')
        if (parts.size < 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }
}

data class WebSocketFrame(
    val opcode: Int,
    val payload: ByteArray,
    val fin: Boolean = true,
) {
    companion object {
        const val OP_CONTINUATION = 0x0
        const val OP_TEXT = 0x1
        const val OP_BINARY = 0x2
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA
    }

    val text: String get() = payload.toString(Charsets.UTF_8)
}

object WebSocketFrames {
    fun encode(frame: WebSocketFrame, masked: Boolean = false, maskKey: ByteArray = randomMask()): ByteArray {
        val payload = frame.payload
        val length = payload.size
        val header = ByteArrayOutputStream()
        val b0 = ((if (frame.fin) 0x80 else 0) or (frame.opcode and 0x0F))
        header.write(b0)
        val maskBit = if (masked) 0x80 else 0
        when {
            length <= 125 -> header.write(maskBit or length)
            length <= 0xFFFF -> {
                header.write(maskBit or 126)
                header.write((length shr 8) and 0xFF)
                header.write(length and 0xFF)
            }
            else -> {
                header.write(maskBit or 127)
                repeat(4) { header.write(0) }
                header.write((length ushr 24) and 0xFF)
                header.write((length ushr 16) and 0xFF)
                header.write((length ushr 8) and 0xFF)
                header.write(length and 0xFF)
            }
        }
        val data = if (masked) {
            header.write(maskKey)
            ByteArray(length) { i -> (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte() }
        } else {
            payload
        }
        val out = header.toByteArray() + data
        return out
    }

    fun decode(input: InputStream): WebSocketFrame? {
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = input.read()
        if (b1 < 0) return null
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var length = (b1 and 0x7F).toLong()
        if (length == 126L) {
            val hi = input.read()
            val lo = input.read()
            if (hi < 0 || lo < 0) return null
            length = ((hi shl 8) or lo).toLong()
        } else if (length == 127L) {
            length = 0
            repeat(8) {
                val b = input.read()
                if (b < 0) return null
                length = (length shl 8) or b.toLong()
            }
        }
        if (length > 1_000_000) return null
        val mask = if (masked) {
            ByteArray(4) {
                val b = input.read()
                if (b < 0) return null
                b.toByte()
            }
        } else {
            null
        }
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val n = input.read(payload, read, payload.size - read)
            if (n < 0) return null
            read += n
        }
        if (mask != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }
        return WebSocketFrame(opcode = opcode, payload = payload, fin = fin)
    }

    fun text(message: String, masked: Boolean = false): ByteArray =
        encode(WebSocketFrame(WebSocketFrame.OP_TEXT, message.toByteArray(Charsets.UTF_8)), masked)

    fun binary(payload: ByteArray, masked: Boolean = false): ByteArray =
        encode(WebSocketFrame(WebSocketFrame.OP_BINARY, payload), masked)

    private fun randomMask(): ByteArray = byteArrayOf(
        (Math.random() * 255).toInt().toByte(),
        (Math.random() * 255).toInt().toByte(),
        (Math.random() * 255).toInt().toByte(),
        (Math.random() * 255).toInt().toByte(),
    )
}
