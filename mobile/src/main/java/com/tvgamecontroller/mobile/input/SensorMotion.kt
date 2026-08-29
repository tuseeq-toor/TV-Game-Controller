package com.tvgamecontroller.mobile.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class MotionSample(
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    /** Unit vector pointing away from the earth, in device coordinates. */
    val gravX: Float = 0f,
    val gravY: Float = 0f,
    val gravZ: Float = 0f,
)

class SensorMotion(
    context: Context,
    private val onSample: (MotionSample) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val gyro = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotation = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var sample = MotionSample()
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    val hasGyro: Boolean get() = gyro != null
    val hasRotation: Boolean get() = rotation != null

    fun start() {
        val delay = SensorManager.SENSOR_DELAY_GAME
        gyro?.let { manager?.registerListener(this, it, delay) }
        accel?.let { manager?.registerListener(this, it, delay) }
        rotation?.let { manager?.registerListener(this, it, delay) }
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        sample = when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> sample.copy(
                gyroX = event.values[0],
                gyroY = event.values[1],
                gyroZ = event.values[2],
            )
            Sensor.TYPE_ACCELEROMETER -> {
                var next = sample.copy(
                    accelX = event.values[0],
                    accelY = event.values[1],
                    accelZ = event.values[2],
                )
                if (rotation == null) {
                    // No rotation vector sensor: low-pass the accelerometer to
                    // approximate the gravity direction.
                    filteredX += 0.15f * (event.values[0] - filteredX)
                    filteredY += 0.15f * (event.values[1] - filteredY)
                    filteredZ += 0.15f * (event.values[2] - filteredZ)
                    val norm = kotlin.math.sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)
                    if (norm > 1f) {
                        next = next.copy(gravX = filteredX / norm, gravY = filteredY / norm, gravZ = filteredZ / norm)
                    }
                }
                next
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                sample.copy(
                    yaw = orientation[0],
                    pitch = orientation[1],
                    roll = orientation[2],
                    // Third row of the device-to-world matrix = world "up" in
                    // device coordinates.
                    gravX = rotationMatrix[6],
                    gravY = rotationMatrix[7],
                    gravZ = rotationMatrix[8],
                )
            }
            else -> sample
        }
        onSample(sample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
