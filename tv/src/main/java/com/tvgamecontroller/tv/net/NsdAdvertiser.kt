package com.tvgamecontroller.tv.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.tvgamecontroller.protocol.Protocol

class NsdAdvertiser(context: Context) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private var registered = false

    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            registered = true
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            registered = false
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            registered = false
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
    }

    fun start(port: Int, name: String = Protocol.SERVICE_NAME) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = Protocol.SERVICE_TYPE
            setPort(port)
        }
        manager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        if (registered) {
            runCatching { manager?.unregisterService(listener) }
        }
    }
}
