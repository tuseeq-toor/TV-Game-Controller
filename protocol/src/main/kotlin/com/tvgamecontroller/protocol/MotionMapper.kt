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
 * TILT_MOVE steers with the phone like a wheel. It works from the gravity
 * direction in device coordinates (gravX/gravY/gravZ, a unit vector), so it is
 * independent of how the phone is held: the rest pose is captured
 * automatically on the first tilt frame (or via [recenter]) and steering is
 * the rotation of gravity around the screen normal since that pose. The old
 * implementation fed the raw roll angle to the stick, which is ±90° whenever
 * the phone is held in landscape — pinning the stick to one side.
 */
class MotionMapper(
    var settings: MotionSettings = MotionSettings(),
) {
    private var baselineWheel: Float? = null
    private var baselineElevation = 0f

    fun recenter() {
        baselineWheel = null
    }

    fun apply(
        state: GamepadState,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        gravX: Float,
        gravY: Float,
        gravZ: Float,
    ): GamepadState {
        return when (settings.mode) {
            MotionMode.OFF -> {
                baselineWheel = null
                state.copy(
                    gyroX = gyroX,
                    gyroY = gyroY,
                    gyroZ = gyroZ,
                    motionEnabled = false,
                )
            }
            MotionMode.GYRO_LOOK -> {
                baselineWheel = null
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
                val (dx, dy) = tilt(gravX, gravY, gravZ)
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

    private fun tilt(gravX: Float, gravY: Float, gravZ: Float): Pair<Float, Float> {
        val magnitude = kotlin.math.sqrt(gravX * gravX + gravY * gravY + gravZ * gravZ)
        if (magnitude < 0.5f) return 0f to 0f // no sensor sample yet
        // Rotation of gravity around the screen normal = steering-wheel angle.
        val wheel = kotlin.math.atan2(gravX, gravY)
        // Elevation of the screen normal = forward/back tilt.
        val elevation = kotlin.math.asin((gravZ / magnitude).coerceIn(-1f, 1f))
        // When the phone lies nearly flat, gravity barely projects onto the
        // screen plane and the wheel angle turns into noise — fade it out and
        // wait for a usable pose before capturing the baseline.
        val planar = kotlin.math.sqrt(gravX * gravX + gravY * gravY) / magnitude
        val baseline = baselineWheel ?: run {
            if (planar >= 0.25f) {
                baselineWheel = wheel
                baselineElevation = elevation
            }
            return 0f to 0f
        }
        val steerScale = (planar / 0.3f).coerceAtMost(1f)
        val dx = applyDeadzone(wrapAngle(baseline - wheel) * settings.tiltSensitivity) * steerScale
        val dy = applyDeadzone(wrapAngle(elevation - baselineElevation) * settings.tiltSensitivity) * invertSign()
        return dx to dy
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
