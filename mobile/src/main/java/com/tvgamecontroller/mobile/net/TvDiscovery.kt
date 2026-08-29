package com.tvgamecontroller.mobile.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.tvgamecontroller.protocol.Protocol

class TvDiscovery(
    context: Context,
    private val onChanged: (List<DiscoveredTv>) -> Unit,
) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val found = linkedMapOf<String, DiscoveredTv>()
    private var watching = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            manager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = resolved.host?.hostAddress ?: return
                    found[resolved.serviceName] = DiscoveredTv(
                        name = resolved.serviceName,
                        host = host,
                        port = if (resolved.port > 0) resolved.port else Protocol.DEFAULT_PORT,
                    )
                    onChanged(found.values.toList())
                }
            })
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            found.remove(serviceInfo.serviceName)
            onChanged(found.values.toList())
        }
    }

    fun start() {
        if (watching) return
        watching = true
        manager?.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        if (!watching) return
        watching = false
        runCatching { manager?.stopServiceDiscovery(listener) }
        found.clear()
    }
}
