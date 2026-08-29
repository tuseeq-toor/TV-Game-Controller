package com.tvgamecontroller.protocol

/**
 * Bluetooth HID Game Pad report used when the phone advertises itself as a
 * real controller to Android TV.
 *
 * Report ID 1 payload (9 bytes, report ID is supplied separately):
 *  0-1 buttons (16-bit, bit0 = A)
 *  2   hat (low nibble 0-7 or 8=neutral) + 4 bits padding
 *  3   LX  0..255 (128 center)
 *  4   LY  0..255 (128 center)
 *  5   RX  0..255 (128 center)
 *  6   RY  0..255 (128 center)
 *  7   LT  0..255
 *  8   RT  0..255
 */
object HidGamepad {
    const val REPORT_ID: Byte = 0x01
    const val REPORT_SIZE = 9

    val REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x05,        // Usage (Game Pad)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), REPORT_ID, // Report ID (1)
        0x05, 0x09,        //   Usage Page (Button)
        0x19, 0x01,        //   Usage Minimum (Button 1)
        0x29, 0x10,        //   Usage Maximum (Button 16)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95.toByte(), 0x10, // Report Count (16)
        0x81.toByte(), 0x02, // Input (Data,Var,Abs)
        0x05, 0x01,        //   Usage Page (Generic Desktop)
        0x09, 0x39,        //   Usage (Hat switch)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x07,        //   Logical Maximum (7)
        0x35, 0x00,        //   Physical Minimum (0)
        0x46, 0x3B, 0x01,  //   Physical Maximum (315)
        0x65, 0x14,        //   Unit (Degrees)
        0x75, 0x04,        //   Report Size (4)
        0x95.toByte(), 0x01, // Report Count (1)
        0x81.toByte(), 0x42, // Input (Data,Var,Abs,Null)
        0x75, 0x04,        //   padding
        0x95.toByte(), 0x01,
        0x81.toByte(), 0x01, // Input (Const)
        0x09, 0x30,        //   Usage (X)
        0x09, 0x31,        //   Usage (Y)
        0x09, 0x32,        //   Usage (Z)
        0x09, 0x35,        //   Usage (Rz)
        0x15, 0x00,
        0x26, 0xFF.toByte(), 0x00,
        0x75, 0x08,
        0x95.toByte(), 0x04,
        0x81.toByte(), 0x02,
        0x05, 0x02,        //   Usage Page (Simulation Controls)
        0x09, 0xC5.toByte(), // Usage (Brake)
        0x09, 0xC4.toByte(), // Usage (Accelerator)
        0x15, 0x00,
        0x26, 0xFF.toByte(), 0x00,
        0x75, 0x08,
        0x95.toByte(), 0x02,
        0x81.toByte(), 0x02,
        0xC0.toByte(),     // End Collection
    )

    fun encode(state: GamepadState): ByteArray {
        val s = state.clamped()
        val buttons = s.buttons and 0xFFFF
        val hat = if (s.hat in 0..7) s.hat else 8
        return byteArrayOf(
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
            (hat and 0x0F).toByte(),
            axisToByte(s.leftStickX),
            axisToByte(s.leftStickY),
            axisToByte(s.rightStickX),
            axisToByte(s.rightStickY),
            triggerToByte(s.leftTrigger),
            triggerToByte(s.rightTrigger),
        )
    }

    fun decode(report: ByteArray): GamepadState? {
        if (report.size < REPORT_SIZE) return null
        val buttons = (report[0].toInt() and 0xFF) or ((report[1].toInt() and 0xFF) shl 8)
        val hat = report[2].toInt() and 0x0F
        return GamepadState(
            buttons = buttons,
            hat = if (hat in 0..8) hat else Hat.NEUTRAL,
            leftStickX = byteToAxis(report[3]),
            leftStickY = byteToAxis(report[4]),
            rightStickX = byteToAxis(report[5]),
            rightStickY = byteToAxis(report[6]),
            leftTrigger = byteToTrigger(report[7]),
            rightTrigger = byteToTrigger(report[8]),
        )
    }

    fun descriptorHasApplicationCollection(): Boolean =
        REPORT_DESCRIPTOR.size > 8 &&
            REPORT_DESCRIPTOR[0] == 0x05.toByte() &&
            REPORT_DESCRIPTOR[2] == 0x09.toByte() &&
            REPORT_DESCRIPTOR[3] == 0x05.toByte() &&
            REPORT_DESCRIPTOR[4] == 0xA1.toByte() &&
            REPORT_DESCRIPTOR.last() == 0xC0.toByte()

    private fun axisToByte(value: Float): Byte {
        val scaled = kotlin.math.round((value.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255)
        return scaled.toByte()
    }

    private fun byteToAxis(value: Byte): Float {
        val unsigned = value.toInt() and 0xFF
        return ((unsigned / 127.5f) - 1f).coerceIn(-1f, 1f)
    }

    private fun triggerToByte(value: Float): Byte =
        (value.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255).toByte()

    private fun byteToTrigger(value: Byte): Float =
        (value.toInt() and 0xFF) / 255f
}
