package com.tvgamecontroller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WebSocketSupportTest {
    @Test
    fun acceptKeyMatchesRfc6455Example() {
        val key = "dGhlIHNhbXBsZSBub25jZQ=="
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocketHandshake.acceptKey(key))
    }

    @Test
    fun upgradeResponseContainsAccept() {
        val response = WebSocketHandshake.upgradeResponse("dGhlIHNhbXBsZSBub25jZQ==")
        assertTrue(response.startsWith("HTTP/1.1 101"))
        assertTrue(response.contains("s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
        assertTrue(response.endsWith("\r\n\r\n"))
    }

    @Test
    fun parsesRequestPathAndHeaders() {
        val request = "GET /controller HTTP/1.1\r\nHost: 192.168.1.8:9842\r\nUpgrade: websocket\r\n\r\n"
        val line = WebSocketHandshake.requestLine(request)!!
        assertEquals("GET", line.first)
        assertEquals("/controller", line.second)
        val headers = WebSocketHandshake.parseHeaders(request)
        assertEquals("websocket", headers["upgrade"])
        assertEquals("192.168.1.8:9842", headers["host"])
    }

    @Test
    fun textAndBinaryFramesRoundTripMaskedAndUnmasked() {
        val text = WebSocketFrames.encode(
            WebSocketFrame(WebSocketFrame.OP_TEXT, "hello".toByteArray()),
            masked = true,
            maskKey = byteArrayOf(1, 2, 3, 4),
        )
        val decodedText = WebSocketFrames.decode(ByteArrayInputStream(text))!!
        assertEquals(WebSocketFrame.OP_TEXT, decodedText.opcode)
        assertEquals("hello", decodedText.text)

        val payload = BinaryCodec.encode(GamepadState(seq = 9, buttons = Buttons.A))
        val binary = WebSocketFrames.binary(payload, masked = false)
        val decodedBinary = WebSocketFrames.decode(ByteArrayInputStream(binary))!!
        assertEquals(WebSocketFrame.OP_BINARY, decodedBinary.opcode)
        val state = BinaryCodec.decode(decodedBinary.payload)!!
        assertEquals(9L, state.seq)
        assertTrue(state.isPressed(Buttons.A))
    }

    @Test
    fun rejectsOversizeFrame() {
        val huge = WebSocketFrame(WebSocketFrame.OP_BINARY, ByteArray(8))
        val encoded = WebSocketFrames.encode(huge)
        encoded[1] = 127.toByte()
        for (i in 2..9) encoded[i] = if (i == 2) 1 else 0
        val decoded = WebSocketFrames.decode(ByteArrayInputStream(encoded))
        assertEquals(null, decoded)
    }
}
