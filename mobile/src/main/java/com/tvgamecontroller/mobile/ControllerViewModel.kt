package com.tvgamecontroller.mobile

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvgamecontroller.mobile.hid.HidGamepadService
import com.tvgamecontroller.mobile.input.MotionSample
import com.tvgamecontroller.mobile.input.SensorMotion
import com.tvgamecontroller.mobile.net.DiscoveredTv
import com.tvgamecontroller.mobile.net.TvConnection
import com.tvgamecontroller.mobile.net.TvDiscovery
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.Hat
import com.tvgamecontroller.protocol.MotionMapper
import com.tvgamecontroller.protocol.MotionMode
import com.tvgamecontroller.protocol.MotionSettings
import com.tvgamecontroller.protocol.Protocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ControllerUiState(
    val host: String = "",
    val pin: String = "",
    val discovered: List<DiscoveredTv> = emptyList(),
    val connectionLabel: String = "Not connected",
    val connected: Boolean = false,
    val hidEnabled: Boolean = false,
    val hidStatus: String = "Bluetooth HID is off",
    val motionMode: MotionMode = MotionMode.GYRO_LOOK,
    val sensitivity: Float = 1.4f,
    val invertY: Boolean = false,
    val deadzone: Float = 0.08f,
    val haptics: Boolean = true,
    val state: GamepadState = GamepadState(),
    val showSettings: Boolean = false,
)

class ControllerViewModel(application: Application) : AndroidViewModel(application) {
    private val _ui = MutableStateFlow(ControllerUiState())
    val ui = _ui.asStateFlow()

    private val mapper = MotionMapper()
    private var buttons = 0
    private var hat = Hat.NEUTRAL
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f
    private var leftTrigger = 0f
    private var rightTrigger = 0f
    private var motion = MotionSample()
    private var seq = 0L
    private var hidService: HidGamepadService? = null
    private var sendJob: Job? = null

    private val sensors = SensorMotion(application) { sample ->
        motion = sample
    }

    private val discovery = TvDiscovery(application) { tvs ->
        _ui.update { it.copy(discovered = tvs) }
    }

    private val connection = TvConnection(
        onReady = { name ->
            _ui.update { it.copy(connected = true, connectionLabel = "Wi-Fi · $name") }
        },
        onClosed = { reason ->
            _ui.update { it.copy(connected = false, connectionLabel = reason) }
        },
        onRumble = { _, _, ms -> rumble(ms) },
    )

    private val hidConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            hidService = (service as HidGamepadService.LocalBinder).service
            _ui.update { it.copy(hidEnabled = true, hidStatus = hidService?.status ?: it.hidStatus) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            _ui.update { it.copy(hidEnabled = false, hidStatus = "Bluetooth HID is off") }
        }
    }

    init {
        sensors.start()
        discovery.start()
        sendJob = viewModelScope.launch {
            while (isActive) {
                publishFrame()
                delay(16)
            }
        }
    }

    fun setHost(value: String) = _ui.update { it.copy(host = value) }
    fun setPin(value: String) = _ui.update { it.copy(pin = value.filter(Char::isDigit).take(4)) }
    fun pickTv(tv: DiscoveredTv) = _ui.update { it.copy(host = tv.host) }
    fun toggleSettings() = _ui.update { it.copy(showSettings = !it.showSettings) }

    fun connect() {
        val host = _ui.value.host.trim()
        if (host.isEmpty()) {
            _ui.update { it.copy(connectionLabel = "Enter the TV address first") }
            return
        }
        _ui.update { it.copy(connectionLabel = "Connecting…") }
        val name = Settings.Global.getString(getApplication<Application>().contentResolver, "device_name")
            ?: Build.MODEL
        connection.connect(host, Protocol.DEFAULT_PORT, _ui.value.pin, name)
    }

    fun disconnect() {
        connection.close("Disconnected")
    }

    fun setButton(mask: Int, pressed: Boolean) {
        buttons = if (pressed) buttons or mask else buttons and mask.inv()
        if (pressed && _ui.value.haptics) rumble(28)
    }

    fun setStick(left: Boolean, x: Float, y: Float) {
        if (left) {
            leftX = x
            leftY = y
        } else {
            rightX = x
            rightY = y
        }
    }

    fun setTrigger(left: Boolean, value: Float) {
        if (left) leftTrigger = value else rightTrigger = value
    }

    fun setHat(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        hat = Hat.fromDpad(up, down, left, right)
    }

    fun setMotionMode(mode: MotionMode) {
        mapper.settings = currentSettings().copy(mode = mode)
        _ui.update { it.copy(motionMode = mode) }
    }

    fun setSensitivity(value: Float) {
        mapper.settings = currentSettings().copy(sensitivity = value, tiltSensitivity = value + 0.4f)
        _ui.update { it.copy(sensitivity = value) }
    }

    fun setInvertY(value: Boolean) {
        mapper.settings = currentSettings().copy(invertY = value)
        _ui.update { it.copy(invertY = value) }
    }

    fun setDeadzone(value: Float) {
        mapper.settings = currentSettings().copy(deadzone = value)
        _ui.update { it.copy(deadzone = value) }
    }

    fun setHaptics(value: Boolean) = _ui.update { it.copy(haptics = value) }

    fun recenter() {
        mapper.recenter()
        rumble(40)
    }

    fun setHidEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            val intent = Intent(context, HidGamepadService::class.java)
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, hidConnection, Context.BIND_AUTO_CREATE)
        } else {
            runCatching { context.unbindService(hidConnection) }
            context.stopService(Intent(context, HidGamepadService::class.java))
            hidService = null
            _ui.update { it.copy(hidEnabled = false, hidStatus = "Bluetooth HID is off") }
        }
    }

    private fun currentSettings(): MotionSettings = MotionSettings(
        mode = _ui.value.motionMode,
        sensitivity = _ui.value.sensitivity,
        tiltSensitivity = _ui.value.sensitivity + 0.4f,
        invertY = _ui.value.invertY,
        deadzone = _ui.value.deadzone,
    )

    private fun publishFrame() {
        mapper.settings = currentSettings()
        seq += 1
        val raw = GamepadState(
            seq = seq,
            leftStickX = leftX,
            leftStickY = leftY,
            rightStickX = rightX,
            rightStickY = rightY,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            buttons = buttons,
            hat = hat,
            accelX = motion.accelX,
            accelY = motion.accelY,
            accelZ = motion.accelZ,
        )
        val next = mapper.apply(
            state = raw,
            gyroX = motion.gyroX,
            gyroY = motion.gyroY,
            gyroZ = motion.gyroZ,
            gravX = motion.gravX,
            gravY = motion.gravY,
            gravZ = motion.gravZ,
        ).copy(
            accelX = motion.accelX,
            accelY = motion.accelY,
            accelZ = motion.accelZ,
        )
        _ui.update {
            it.copy(
                state = next,
                hidStatus = hidService?.status ?: it.hidStatus,
            )
        }
        if (_ui.value.connected) {
            connection.sendState(next)
        }
        hidService?.send(next)
    }

    private fun rumble(ms: Int) {
        if (!_ui.value.haptics) return
        val context = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCleared() {
        sendJob?.cancel()
        sensors.stop()
        discovery.stop()
        connection.close("closed")
        runCatching { getApplication<Application>().unbindService(hidConnection) }
        super.onCleared()
    }
}
