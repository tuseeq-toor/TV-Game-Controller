package com.tvgamecontroller.protocol

/**
 * Bluetooth HID Game Pad report used when the phone advertises itself as a
 * real controller to Android TV.
 *
 * Report ID 1 payload (9 bytes, report ID is supplied separately):
 *  0-1 buttons (16-bit, Linux BTN_GAMEPAD order — not our internal mask)
 *  2   hat (low nibble 0-7 or 8=neutral) + 4 bits padding
 *  3   LX  0..255 (128 center)
 *  4   LY  0..255 (128 center)
 *  5   RX  0..255 (128 center)
 *  6   RY  0..255 (128 center)
 *  7   LT  0..255
 *  8   RT  0..255
 *
 * Android Generic.kl maps sequential Game Pad buttons as:
 *  0 A, 1 B, 2 C, 3 X, 4 Y, 5 Z, 6 L1, 7 R1, 8 L2, 9 R2,
 *  10 Select, 11 Start, 12 Mode, 13 L3, 14 R3.
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
        val buttons = toHidButtons(s.buttons)
        val hat = if (s.hat in 0..7) s.hat else 8
        val leftTrigger = if (s.leftTrigger > 0f) s.leftTrigger else if (s.isPressed(Buttons.L2)) 1f else 0f
        val rightTrigger = if (s.rightTrigger > 0f) s.rightTrigger else if (s.isPressed(Buttons.R2)) 1f else 0f
        return byteArrayOf(
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
            (hat and 0x0F).toByte(),
            axisToByte(s.leftStickX),
            axisToByte(s.leftStickY),
            axisToByte(s.rightStickX),
            axisToByte(s.rightStickY),
            triggerToByte(leftTrigger),
            triggerToByte(rightTrigger),
        )
    }

    fun decode(report: ByteArray): GamepadState? {
        if (report.size < REPORT_SIZE) return null
        val hidButtons = (report[0].toInt() and 0xFF) or ((report[1].toInt() and 0xFF) shl 8)
        val hat = report[2].toInt() and 0x0F
        return GamepadState(
            buttons = fromHidButtons(hidButtons),
            hat = if (hat in 0..8) hat else Hat.NEUTRAL,
            leftStickX = byteToAxis(report[3]),
            leftStickY = byteToAxis(report[4]),
            rightStickX = byteToAxis(report[5]),
            rightStickY = byteToAxis(report[6]),
            leftTrigger = byteToTrigger(report[7]),
            rightTrigger = byteToTrigger(report[8]),
        )
    }

    /**
     * Internal [Buttons] bits → HID Game Pad bits that Android TV actually reads
     * as L1/R1/L2/R2. Sending our internal mask raw made L1 look like Y.
     */
    fun toHidButtons(buttons: Int): Int {
        var hid = 0
        fun map(internal: Int, hidBit: Int) {
            if (buttons and internal != 0) hid = hid or (1 shl hidBit)
        }
        map(Buttons.A, 0)
        map(Buttons.B, 1)
        map(Buttons.X, 3)
        map(Buttons.Y, 4)
        map(Buttons.L1, 6)
        map(Buttons.R1, 7)
        map(Buttons.L2, 8)
        map(Buttons.R2, 9)
        map(Buttons.SELECT, 10)
        map(Buttons.START, 11)
        map(Buttons.HOME, 12)
        map(Buttons.L3, 13)
        map(Buttons.R3, 14)
        return hid
    }

    fun fromHidButtons(hid: Int): Int {
        var buttons = 0
        fun map(hidBit: Int, internal: Int) {
            if (hid and (1 shl hidBit) != 0) buttons = buttons or internal
        }
        map(0, Buttons.A)
        map(1, Buttons.B)
        map(3, Buttons.X)
        map(4, Buttons.Y)
        map(6, Buttons.L1)
        map(7, Buttons.R1)
        map(8, Buttons.L2)
        map(9, Buttons.R2)
        map(10, Buttons.SELECT)
        map(11, Buttons.START)
        map(12, Buttons.HOME)
        map(13, Buttons.L3)
        map(14, Buttons.R3)
        return buttons
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
