package com.tvgamecontroller.tv

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.Pairing
import com.tvgamecontroller.protocol.Protocol
import com.tvgamecontroller.tv.game.OrbHunt
import com.tvgamecontroller.tv.game.OrbHuntSnapshot
import com.tvgamecontroller.tv.net.LanServer
import com.tvgamecontroller.tv.net.NsdAdvertiser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

data class HostUiState(
    val pin: String,
    val host: String,
    val port: Int = Protocol.DEFAULT_PORT,
    val clients: List<String> = emptyList(),
    val pad: GamepadState = GamepadState(),
    val game: OrbHuntSnapshot,
    val connectUri: String,
    val webUrl: String,
)

class HostViewModel(application: Application) : AndroidViewModel(application) {
    private val pin = Pairing.generatePin()
    private val hostAddress = localAddress(application)
    private val game = OrbHunt()
    private val advertiser = NsdAdvertiser(application)
    private var lastPad = GamepadState()
    private var connected = false

    private val server = LanServer(
        port = Protocol.DEFAULT_PORT,
        assets = application.assets,
        pin = pin,
        serverName = "Android TV",
        onState = { state, _ ->
            lastPad = state
            connected = true
        },
        onClientsChanged = { names ->
            connected = names.isNotEmpty()
            _ui.update { it.copy(clients = names) }
        },
    )

    private val _ui = MutableStateFlow(
        HostUiState(
            pin = pin,
            host = hostAddress,
            game = game.step(0f, GamepadState(), false),
            connectUri = Pairing.connectUri(hostAddress, Protocol.DEFAULT_PORT, pin),
            webUrl = "http://$hostAddress:${Protocol.DEFAULT_PORT}/",
        ),
    )
    val ui = _ui.asStateFlow()

    init {
        server.start()
        advertiser.start(Protocol.DEFAULT_PORT)
        viewModelScope.launch {
            var last = System.nanoTime()
            var lastScore = 0
            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.008f, 0.05f)
                last = now
                val snapshot = game.step(dt, lastPad, connected)
                if (snapshot.score > lastScore) {
                    rumbleHit()
                }
                lastScore = snapshot.score
                _ui.update { it.copy(pad = lastPad, game = snapshot) }
                delay(16)
            }
        }
    }

    fun rumbleHit() {
        server.rumble(0.3f, 0.8f, 70)
    }

    override fun onCleared() {
        advertiser.stop()
        server.stop()
        super.onCleared()
    }

    companion object {
        fun localAddress(context: Context): String {
            @Suppress("DEPRECATION")
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff,
                )
            }
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nic ->
                nic.inetAddresses.toList().forEach { address ->
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
            return "0.0.0.0"
        }
    }
}
