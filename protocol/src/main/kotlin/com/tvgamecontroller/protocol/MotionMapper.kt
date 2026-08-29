package com.tvgamecontroller.protocol

enum class MotionMode {
    OFF,
    GYRO_LOOK,
    TILT_MOVE,
}

data class MotionSettings(
    val mode: MotionMode = MotionMode.OFF,
    val sensitivity: Float = 1.4f,
    val tiltSensitivity: Float = 1.8f,
    val invertY: Boolean = false,
    val deadzone: Float = 0.08f,
)

/**
 * Turns phone sensors into stick deflections.
 *
 * GYRO_LOOK uses angular velocity so the right stick returns to center when the
 * phone is still — the usual FPS / camera-look feel.
 *
 * TILT_MOVE uses orientation offset from a recenter pose so the left stick
 * holds a direction while the phone stays tilted — useful for racing or
 * balance games.
 */
class MotionMapper(
    var settings: MotionSettings = MotionSettings(),
) {
    private var yawOffset = 0f
    private var pitchOffset = 0f

    fun recenter(yawRad: Float = 0f, pitchRad: Float = 0f) {
        yawOffset = yawRad
        pitchOffset = pitchRad
    }

    fun apply(
        state: GamepadState,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        yawRad: Float,
        pitchRad: Float,
        rollRad: Float,
    ): GamepadState {
        return when (settings.mode) {
            MotionMode.OFF -> state.copy(
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ,
                motionEnabled = false,
            )
            MotionMode.GYRO_LOOK -> {
                val lookX = applyDeadzone(gyroY * settings.sensitivity)
                val lookY = applyDeadzone(gyroX * settings.sensitivity) * invertSign()
                state.copy(
                    rightStickX = (state.rightStickX + lookX).coerceIn(-1f, 1f),
                    rightStickY = (state.rightStickY + lookY).coerceIn(-1f, 1f),
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    motionEnabled = true,
                )
            }
            MotionMode.TILT_MOVE -> {
                val dx = applyDeadzone(wrapAngle(rollRad) * settings.tiltSensitivity)
                val dy = applyDeadzone(wrapAngle(pitchRad - pitchOffset) * settings.tiltSensitivity) * invertSign()
                state.copy(
                    leftStickX = mixStick(state.leftStickX, dx),
                    leftStickY = mixStick(state.leftStickY, dy),
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    motionEnabled = true,
                )
            }
        }
    }

    private fun invertSign(): Float = if (settings.invertY) 1f else -1f

    private fun applyDeadzone(value: Float): Float {
        val v = value.coerceIn(-2f, 2f)
        val dz = settings.deadzone
        return when {
            v > dz -> ((v - dz) / (1f - dz)).coerceIn(0f, 1f)
            v < -dz -> ((v + dz) / (1f - dz)).coerceIn(-1f, 0f)
            else -> 0f
        }
    }

    private fun mixStick(manual: Float, motion: Float): Float {
        return if (kotlin.math.abs(manual) > 0.05f) {
            (manual + motion * 0.35f).coerceIn(-1f, 1f)
        } else {
            motion.coerceIn(-1f, 1f)
        }
    }

    private fun wrapAngle(radians: Float): Float {
        var a = radians
        while (a > Math.PI) a -= (2.0 * Math.PI).toFloat()
        while (a < -Math.PI) a += (2.0 * Math.PI).toFloat()
        return a
    }
}
