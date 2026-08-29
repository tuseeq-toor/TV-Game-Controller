package com.tvgamecontroller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HatTest {
    @Test
    fun cardinalAndDiagonalDirections() {
        assertEquals(Hat.NORTH, Hat.fromDpad(up = true, down = false, left = false, right = false))
        assertEquals(Hat.SOUTH, Hat.fromDpad(up = false, down = true, left = false, right = false))
        assertEquals(Hat.WEST, Hat.fromDpad(up = false, down = false, left = true, right = false))
        assertEquals(Hat.EAST, Hat.fromDpad(up = false, down = false, left = false, right = true))
        assertEquals(Hat.NORTHEAST, Hat.fromDpad(up = true, down = false, left = false, right = true))
        assertEquals(Hat.SOUTHWEST, Hat.fromDpad(up = false, down = true, left = true, right = false))
        assertEquals(Hat.NEUTRAL, Hat.fromDpad(up = false, down = false, left = false, right = false))
        assertEquals(Hat.NEUTRAL, Hat.fromDpad(up = true, down = true, left = false, right = false))
    }

    @Test
    fun vectorLengthIsUnitOrZero() {
        for (hat in 0..8) {
            val (x, y) = Hat.toVector(hat)
            val length = kotlin.math.sqrt(x * x + y * y)
            if (hat == Hat.NEUTRAL) {
                assertEquals(0f, length, 0.001f)
            } else {
                assertEquals(1f, length, 0.01f)
            }
        }
    }
}

class BinaryCodecTest {
    @Test
    fun roundTripPreservesControlsAndMotion() {
        val original = GamepadState(
            seq = 42,
            leftStickX = -0.5f,
            leftStickY = 0.25f,
            rightStickX = 1f,
            rightStickY = -1f,
            leftTrigger = 0.3f,
            rightTrigger = 0.9f,
            buttons = Buttons.A or Buttons.R1 or Buttons.START,
            hat = Hat.WEST,
            gyroX = 0.4f,
            gyroY = -0.2f,
            gyroZ = 1.1f,
            accelX = 0.1f,
            accelY = 9.8f,
            accelZ = -0.3f,
            motionEnabled = true,
        )
        val decoded = BinaryCodec.decode(BinaryCodec.encode(original))
        assertNotNull(decoded)
        assertEquals(original.seq, decoded!!.seq)
        assertEquals(original.buttons, decoded.buttons)
        assertEquals(original.hat, decoded.hat)
        assertTrue(decoded.motionEnabled)
        assertNear(original.leftStickX, decoded.leftStickX)
        assertNear(original.leftStickY, decoded.leftStickY)
        assertNear(original.rightStickX, decoded.rightStickX)
        assertNear(original.rightStickY, decoded.rightStickY)
        assertNear(original.leftTrigger, decoded.leftTrigger, 0.002f)
        assertNear(original.rightTrigger, decoded.rightTrigger, 0.002f)
        assertNear(original.gyroX, decoded.gyroX, 0.002f)
        assertNear(original.accelY, decoded.accelY, 0.02f)
    }

    @Test
    fun rejectsShortOrBadMagic() {
        assertNull(BinaryCodec.decode(ByteArray(10)))
        val bad = BinaryCodec.encode(GamepadState())
        bad[0] = 'X'.code.toByte()
        assertNull(BinaryCodec.decode(bad))
    }

    @Test
    fun encodeSizeIsFixed() {
        assertEquals(BinaryCodec.SIZE, BinaryCodec.encode(GamepadState()).size)
    }
}

class JsonCodecTest {
    @Test
    fun stateRoundTrip() {
        val original = GamepadState(
            seq = 7,
            leftStickX = 0.33f,
            rightStickY = -0.8f,
            buttons = Buttons.X or Buttons.Y,
            hat = Hat.SOUTH,
            motionEnabled = true,
        )
        val parsed = JsonCodec.parseState(JsonCodec.stateJson(original))
        assertNotNull(parsed)
        assertEquals(7L, parsed!!.seq)
        assertEquals(original.buttons, parsed.buttons)
        assertEquals(Hat.SOUTH, parsed.hat)
        assertTrue(parsed.motionEnabled)
        assertNear(0.33f, parsed.leftStickX, 0.001f)
        assertNear(-0.8f, parsed.rightStickY, 0.001f)
    }

    @Test
    fun handshakeMessages() {
        val hello = JsonCodec.hello("Pixel 8")
        assertEquals("hello", JsonCodec.messageType(hello))
        assertEquals("Pixel 8", JsonCodec.stringField(hello, "name"))
        assertEquals(Protocol.VERSION, JsonCodec.intField(hello, "protocol"))

        val welcome = JsonCodec.welcome(true, "Living Room")
        assertEquals("welcome", JsonCodec.messageType(welcome))
        assertEquals(true, JsonCodec.boolField(welcome, "pinRequired"))
        assertEquals("Living Room", JsonCodec.stringField(welcome, "serverName"))

        val auth = JsonCodec.auth("8472")
        assertEquals("8472", JsonCodec.stringField(auth, "pin"))
    }

    @Test
    fun escapedNamesSurvive() {
        val hello = JsonCodec.hello("Dad's \"TV\"")
        assertEquals("Dad's \"TV\"", JsonCodec.stringField(hello, "name"))
    }
}

class PairingTest {
    @Test
    fun connectUriRoundTrip() {
        val uri = Pairing.connectUri("192.168.1.20", 9842, "1234")
        val parsed = Pairing.parseConnectUri(uri)
        assertNotNull(parsed)
        assertEquals("192.168.1.20", parsed!!.first)
        assertEquals(9842, parsed.second)
        assertEquals("1234", parsed.third)
    }

    @Test
    fun pinIsFourDigits() {
        val pin = Pairing.generatePin(1847)
        assertEquals("1847", pin)
        assertEquals(4, pin.length)
    }

    @Test
    fun rejectsUnknownUri() {
        assertNull(Pairing.parseConnectUri("https://example.com"))
    }
}

class HidGamepadTest {
    @Test
    fun reportRoundTrips() {
        val state = GamepadState(
            leftStickX = -1f,
            leftStickY = 1f,
            rightStickX = 0f,
            rightStickY = 0f,
            leftTrigger = 0f,
            rightTrigger = 1f,
            buttons = Buttons.A or Buttons.START,
            hat = Hat.NORTH,
        )
        val report = HidGamepad.encode(state)
        assertEquals(HidGamepad.REPORT_SIZE, report.size)
        val decoded = HidGamepad.decode(report)!!
        assertTrue(decoded.isPressed(Buttons.A))
        assertTrue(decoded.isPressed(Buttons.START))
        assertFalse(decoded.isPressed(Buttons.B))
        assertEquals(Hat.NORTH, decoded.hat)
        assertNear(-1f, decoded.leftStickX, 0.01f)
        assertNear(1f, decoded.leftStickY, 0.01f)
        assertNear(0f, decoded.rightStickX, 0.02f)
        assertNear(1f, decoded.rightTrigger, 0.01f)
        assertNear(0f, decoded.leftTrigger, 0.01f)
    }

    @Test
    fun centerStickIs128AndNeutralHatUsesNullState() {
        val report = HidGamepad.encode(GamepadState())
        assertEquals(128, report[3].toInt() and 0xFF)
        assertEquals(128, report[4].toInt() and 0xFF)
        assertEquals(8, report[2].toInt() and 0x0F)
    }

    @Test
    fun descriptorLooksLikeAGamepad() {
        assertTrue(HidGamepad.descriptorHasApplicationCollection())
        assertTrue(HidGamepad.REPORT_DESCRIPTOR.size > 40)
    }

    @Test
    fun shouldersUseAndroidGamepadButtonSlots() {
        fun hidMask(button: Int): Int {
            val report = HidGamepad.encode(GamepadState(buttons = button))
            return (report[0].toInt() and 0xFF) or ((report[1].toInt() and 0xFF) shl 8)
        }
        assertEquals(1 shl 0, hidMask(Buttons.A))
        assertEquals(1 shl 1, hidMask(Buttons.B))
        assertEquals(1 shl 3, hidMask(Buttons.X))
        assertEquals(1 shl 4, hidMask(Buttons.Y))
        assertEquals(1 shl 6, hidMask(Buttons.L1))
        assertEquals(1 shl 7, hidMask(Buttons.R1))
        assertEquals(1 shl 8, hidMask(Buttons.L2))
        assertEquals(1 shl 9, hidMask(Buttons.R2))
        assertEquals(1 shl 11, hidMask(Buttons.START))
    }

    @Test
    fun triggersAreDuplicatedOnRxRyAndBrakeGas() {
        val right = HidGamepad.encode(GamepadState(buttons = Buttons.R2))
        assertEquals(0, right[7].toInt() and 0xFF)   // Rx (LT) untouched
        assertEquals(255, right[8].toInt() and 0xFF) // Ry (RT)
        assertEquals(0, right[9].toInt() and 0xFF)   // Brake (LT) untouched
        assertEquals(255, right[10].toInt() and 0xFF) // Gas (RT)
        assertTrue(HidGamepad.decode(right)!!.isPressed(Buttons.R2))

        val analog = HidGamepad.encode(GamepadState(leftTrigger = 0.5f))
        assertEquals(analog[7], analog[9])
        val decoded = HidGamepad.decode(analog)!!
        assertNear(0.5f, decoded.leftTrigger, 0.01f)
    }

    @Test
    fun restReportCentersSticksAndZeroesAllTriggerAxes() {
        val report = HidGamepad.encode(GamepadState())
        assertEquals(HidGamepad.REPORT_SIZE, report.size)
        for (i in 3..6) assertEquals(128, report[i].toInt() and 0xFF)
        for (i in 7..10) assertEquals(0, report[i].toInt() and 0xFF)
    }

    @Test
    fun descriptorDeclaresStandardSticksAndBothTriggerStyles() {
        val desc = HidGamepad.REPORT_DESCRIPTOR.toList()
        assertTrue(desc.contains(0x32.toByte())) // Z right stick X
        assertTrue(desc.contains(0x35.toByte())) // Rz right stick Y
        assertTrue(desc.contains(0x33.toByte())) // Rx trigger
        assertTrue(desc.contains(0x34.toByte())) // Ry trigger
        assertTrue(desc.contains(0xC5.toByte())) // Brake
        assertTrue(desc.contains(0xC4.toByte())) // Gas
    }
}

class MotionMapperTest {
    /** Gravity in device coordinates after turning the phone like a steering
     *  wheel by [theta] radians (clockwise = steering right), starting from a
     *  landscape rest pose where "up" sits along the device axis (restX, restY). */
    private fun MotionMapper.applyWheel(restX: Float, restY: Float, theta: Float): GamepadState {
        val cos = kotlin.math.cos(theta)
        val sin = kotlin.math.sin(theta)
        return apply(
            state = GamepadState(),
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f,
            gravX = restX * cos - restY * sin,
            gravY = restX * sin + restY * cos,
            gravZ = 0f,
        )
    }

    @Test
    fun gyroLookMovesRightStickAndRecentersWhenStill() {
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.GYRO_LOOK, sensitivity = 1f, deadzone = 0f))
        val moving = mapper.apply(
            state = GamepadState(),
            gyroX = 0.4f,
            gyroY = -0.5f,
            gyroZ = 0f,
            gravX = 0f,
            gravY = 0f,
            gravZ = 1f,
        )
        assertTrue(moving.motionEnabled)
        assertTrue(moving.rightStickX < 0f)
        assertTrue(moving.rightStickY < 0f)

        val still = mapper.apply(
            state = GamepadState(),
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f,
            gravX = 0f,
            gravY = 0f,
            gravZ = 1f,
        )
        assertEquals(0f, still.rightStickX, 0.001f)
        assertEquals(0f, still.rightStickY, 0.001f)
    }

    @Test
    fun tiltSteersRelativeToLandscapeRestPose() {
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.TILT_MOVE, tiltSensitivity = 1.4f, deadzone = 0f))
        // First frame at rest (landscape, top of phone to the left: device X
        // points at the sky) captures the baseline — stick stays centered.
        val rest = mapper.applyWheel(restX = 1f, restY = 0f, theta = 0f)
        assertTrue(rest.motionEnabled)
        assertEquals(0f, rest.leftStickX, 0.001f)

        val right = mapper.applyWheel(restX = 1f, restY = 0f, theta = 0.4f)
        assertTrue("expected steer right, got ${right.leftStickX}", right.leftStickX > 0.3f)

        val left = mapper.applyWheel(restX = 1f, restY = 0f, theta = -0.4f)
        assertTrue("expected steer left, got ${left.leftStickX}", left.leftStickX < -0.3f)

        // Back at rest the stick returns to center.
        val centered = mapper.applyWheel(restX = 1f, restY = 0f, theta = 0f)
        assertEquals(0f, centered.leftStickX, 0.001f)
    }

    @Test
    fun tiltWorksInBothLandscapeOrientations() {
        // Top of the phone to the right: device X points at the ground.
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.TILT_MOVE, tiltSensitivity = 1.4f, deadzone = 0f))
        mapper.applyWheel(restX = -1f, restY = 0f, theta = 0f)
        val right = mapper.applyWheel(restX = -1f, restY = 0f, theta = 0.4f)
        assertTrue("expected steer right, got ${right.leftStickX}", right.leftStickX > 0.3f)
    }

    @Test
    fun recenterMovesTheRestPose() {
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.TILT_MOVE, tiltSensitivity = 1.4f, deadzone = 0f))
        mapper.applyWheel(restX = 1f, restY = 0f, theta = 0f)
        assertTrue(mapper.applyWheel(restX = 1f, restY = 0f, theta = 0.4f).leftStickX > 0.3f)
        // Recenter while held at 0.4 rad: that pose becomes the new zero.
        mapper.recenter()
        mapper.applyWheel(restX = 1f, restY = 0f, theta = 0.4f)
        assertEquals(0f, mapper.applyWheel(restX = 1f, restY = 0f, theta = 0.4f).leftStickX, 0.001f)
    }

    @Test
    fun flatPhoneDoesNotSlamTheStick() {
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.TILT_MOVE, tiltSensitivity = 1.4f, deadzone = 0f))
        val flat = mapper.apply(
            state = GamepadState(),
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f,
            gravX = 0.02f,
            gravY = 0.01f,
            gravZ = 0.999f,
        )
        assertEquals(0f, flat.leftStickX, 0.001f)
        assertEquals(0f, flat.leftStickY, 0.001f)
    }

    @Test
    fun offModeLeavesSticksAlone() {
        val mapper = MotionMapper(MotionSettings(mode = MotionMode.OFF))
        val out = mapper.apply(
            state = GamepadState(leftStickX = 0.4f, rightStickY = -0.2f),
            gyroX = 2f,
            gyroY = 2f,
            gyroZ = 2f,
            gravX = 1f,
            gravY = 0f,
            gravZ = 0f,
        )
        assertFalse(out.motionEnabled)
        assertEquals(0.4f, out.leftStickX, 0.001f)
        assertEquals(-0.2f, out.rightStickY, 0.001f)
    }
}

class GamepadStateTest {
    @Test
    fun buttonToggleAndClamp() {
        val pressed = GamepadState().withButton(Buttons.A, true).withButton(Buttons.B, true)
        assertTrue(pressed.isPressed(Buttons.A))
        val released = pressed.withButton(Buttons.A, false)
        assertFalse(released.isPressed(Buttons.A))
        assertTrue(released.isPressed(Buttons.B))

        val clamped = GamepadState(leftStickX = 4f, leftTrigger = -2f, hat = 99).clamped()
        assertEquals(1f, clamped.leftStickX)
        assertEquals(0f, clamped.leftTrigger)
        assertEquals(8, clamped.hat)
    }
}

private fun assertNear(expected: Float, actual: Float, epsilon: Float = 0.002f) {
    assertTrue(
        "expected $expected but was $actual",
        abs(expected - actual) <= epsilon,
    )
}
