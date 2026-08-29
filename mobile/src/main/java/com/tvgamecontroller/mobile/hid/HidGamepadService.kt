package com.tvgamecontroller.mobile.hid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tvgamecontroller.mobile.R
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.HidGamepad

class HidGamepadService : Service() {
    inner class LocalBinder : Binder() {
        val service: HidGamepadService get() = this@HidGamepadService
    }

    private val binder = LocalBinder()
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    @Volatile var registered = false
        private set
    @Volatile var status: String = "Bluetooth HID is off"
        private set

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidGamepadService.registered = registered
            status = if (registered) "Advertising as a gamepad. Pair this phone from the TV." else "HID app unregistered"
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            host = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            status = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected to ${device?.name ?: "Android TV"}"
                BluetoothProfile.STATE_CONNECTING -> "Connecting…"
                BluetoothProfile.STATE_DISCONNECTED -> "Waiting for the TV to pair"
                else -> status
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            hid = proxy as? BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            registered = false
            status = "Bluetooth HID disconnected"
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(42, notification())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            status = "Bluetooth HID needs Android 9 or newer"
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            status = "This phone has no Bluetooth adapter"
            return
        }
        adapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
    }

    override fun onDestroy() {
        runCatching { hid?.unregisterApp() }
        hid = null
        super.onDestroy()
    }

    fun send(state: GamepadState) {
        val device = host ?: return
        val hidDevice = hid ?: return
        hidDevice.sendReport(device, HidGamepad.REPORT_ID.toInt(), HidGamepad.encode(state))
    }

    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "TV Gamepad",
            "Phone motion gamepad",
            "TV Game Controller",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            HidGamepad.REPORT_DESCRIPTOR,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
        hid?.registerApp(sdp, null, qos, ContextCompat.getMainExecutor(this), callback)
        status = "Registering HID gamepad…"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gamepad",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.hid_notification_title))
            .setContentText(getString(R.string.hid_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "hid_gamepad"
    }
}
