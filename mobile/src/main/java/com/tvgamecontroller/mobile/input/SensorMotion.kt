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
            Sensor.TYPE_ACCELEROMETER -> sample.copy(
                accelX = event.values[0],
                accelY = event.values[1],
                accelZ = event.values[2],
            )
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                sample.copy(
                    yaw = orientation[0],
                    pitch = orientation[1],
                    roll = orientation[2],
                )
            }
            else -> sample
        }
        onSample(sample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
