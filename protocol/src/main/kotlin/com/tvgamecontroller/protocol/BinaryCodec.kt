package com.tvgamecontroller.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact little-endian snapshot used on the LAN WebSocket / UDP path.
 *
 * Layout (40 bytes):
 *  0-3   magic "TGC1"
 *  4-7   seq uint32
 *  8-11  buttons uint32
 *  12    hat uint8
 *  13    flags uint8 (bit0 = motionEnabled)
 *  14-15 reserved
 *  16-27 lx ly rx ry lt rt as int16 (-32767..32767, triggers 0..32767)
 *  28-39 gx gy gz ax ay az as int16 (gyro: rad/s * 1000, accel: m/s^2 * 100)
 */
object BinaryCodec {
    val MAGIC = byteArrayOf('T'.code.toByte(), 'G'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    const val SIZE = 40
    private const val GYRO_SCALE = 1000f
    private const val ACCEL_SCALE = 100f
    private const val STICK_SCALE = 32767f

    fun encode(state: GamepadState): ByteArray {
        val s = state.clamped()
        val buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC)
        buf.putInt((s.seq and 0xFFFF_FFFFL).toInt())
        buf.putInt(s.buttons)
        buf.put(s.hat.toByte())
        buf.put(if (s.motionEnabled) 1 else 0)
        buf.putShort(0)
        buf.putShort(toStick(s.leftStickX))
        buf.putShort(toStick(s.leftStickY))
        buf.putShort(toStick(s.rightStickX))
        buf.putShort(toStick(s.rightStickY))
        buf.putShort(toUnit(s.leftTrigger))
        buf.putShort(toUnit(s.rightTrigger))
        buf.putShort((s.gyroX * GYRO_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        buf.putShort((s.gyroY * GYRO_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        buf.putShort((s.gyroZ * GYRO_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        buf.putShort((s.accelX * ACCEL_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        buf.putShort((s.accelY * ACCEL_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        buf.putShort((s.accelZ * ACCEL_SCALE).toInt().coerceIn(-32767, 32767).toShort())
        return buf.array()
    }

    fun decode(bytes: ByteArray): GamepadState? {
        if (bytes.size < SIZE) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        if (!magic.contentEquals(MAGIC)) return null
        val seq = buf.int.toLong() and 0xFFFF_FFFFL
        val buttons = buf.int
        val hat = buf.get().toInt() and 0xFF
        val flags = buf.get().toInt() and 0xFF
        buf.short
        return GamepadState(
            seq = seq,
            buttons = buttons,
            hat = hat.coerceIn(0, 8),
            leftStickX = fromStick(buf.short),
            leftStickY = fromStick(buf.short),
            rightStickX = fromStick(buf.short),
            rightStickY = fromStick(buf.short),
            leftTrigger = fromUnit(buf.short),
            rightTrigger = fromUnit(buf.short),
            gyroX = buf.short / GYRO_SCALE,
            gyroY = buf.short / GYRO_SCALE,
            gyroZ = buf.short / GYRO_SCALE,
            accelX = buf.short / ACCEL_SCALE,
            accelY = buf.short / ACCEL_SCALE,
            accelZ = buf.short / ACCEL_SCALE,
            motionEnabled = flags and 1 != 0,
        )
    }

    private fun toStick(value: Float): Short =
        (value.coerceIn(-1f, 1f) * STICK_SCALE).toInt().toShort()

    private fun fromStick(value: Short): Float = value / STICK_SCALE

    private fun toUnit(value: Float): Short =
        (value.coerceIn(0f, 1f) * STICK_SCALE).toInt().toShort()

    private fun fromUnit(value: Short): Float = value.toInt().coerceAtLeast(0) / STICK_SCALE
}
