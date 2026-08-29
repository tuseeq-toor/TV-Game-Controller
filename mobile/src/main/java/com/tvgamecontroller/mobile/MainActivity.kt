package com.tvgamecontroller.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tvgamecontroller.mobile.ui.ControllerScreen
import com.tvgamecontroller.mobile.ui.theme.TvGamepadTheme
import com.tvgamecontroller.protocol.Pairing

class MainActivity : ComponentActivity() {
    private val viewModel: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        requestBluetoothPermissions()
        intent?.data?.toString()?.let { uri ->
            Pairing.parseConnectUri(uri)?.let { (host, _, pin) ->
                viewModel.setHost(host)
                viewModel.setPin(pin)
                viewModel.connect()
            }
        }
        setContent {
            TvGamepadTheme {
                val state by viewModel.ui.collectAsState()
                ControllerScreen(
                    state = state,
                    onHost = viewModel::setHost,
                    onPin = viewModel::setPin,
                    onPickTv = viewModel::pickTv,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onButton = viewModel::setButton,
                    onStick = viewModel::setStick,
                    onTrigger = viewModel::setTrigger,
                    onHat = viewModel::setHat,
                    onMotionMode = viewModel::setMotionMode,
                    onSensitivity = viewModel::setSensitivity,
                    onInvertY = viewModel::setInvertY,
                    onDeadzone = viewModel::setDeadzone,
                    onHaptics = viewModel::setHaptics,
                    onRecenter = viewModel::recenter,
                    onToggleSettings = viewModel::toggleSettings,
                    onHid = viewModel::setHidEnabled,
                )
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestBluetoothPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 21)
        }
    }
}
