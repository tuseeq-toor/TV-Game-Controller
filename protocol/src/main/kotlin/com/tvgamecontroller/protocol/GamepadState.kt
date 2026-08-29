package com.tvgamecontroller.protocol

data class GamepadState(
    val seq: Long = 0,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val buttons: Int = 0,
    val hat: Int = Hat.NEUTRAL,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val motionEnabled: Boolean = false,
) {
    fun withButton(button: Int, pressed: Boolean): GamepadState {
        val next = if (pressed) buttons or button else buttons and button.inv()
        return copy(buttons = next)
    }

    fun isPressed(button: Int): Boolean = Buttons.isPressed(buttons, button)

    fun clamped(): GamepadState = copy(
        leftStickX = leftStickX.coerceIn(-1f, 1f),
        leftStickY = leftStickY.coerceIn(-1f, 1f),
        rightStickX = rightStickX.coerceIn(-1f, 1f),
        rightStickY = rightStickY.coerceIn(-1f, 1f),
        leftTrigger = leftTrigger.coerceIn(0f, 1f),
        rightTrigger = rightTrigger.coerceIn(0f, 1f),
        hat = hat.coerceIn(0, 8),
    )
}
