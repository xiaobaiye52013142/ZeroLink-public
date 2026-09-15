package com.helium4.localscreenshare.desktop.discovery

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class DiscoveredDevice(val name: String, val ip: String, val port: Int)

object MdnsDiscovery {
    private const val SERVICE_TYPE = "_localscreenshare._tcp.local."
    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null

    fun startListening(onFound: (DiscoveredDevice) -> Unit, onLost: (String) -> Unit) {
        Thread {
            try {
                val j = JmDNS.create()
                jmdns = j
                val l = object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        j.requestServiceInfo(event.type, event.name)
                    }
                    override fun serviceRemoved(event: ServiceEvent) {
                        onLost(event.name)
                    }
                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val ip = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                        onFound(DiscoveredDevice(info.name, ip, info.port))
                    }
                }
                listener = l
                j.addServiceListener(SERVICE_TYPE, l)
            } catch (_: Exception) {
                // mDNS unavailable on this network interface — manual IP entry still works
            }
        }.apply { isDaemon = true; start() }
    }

    fun stopListening() {
        try {
            listener?.let { jmdns?.removeServiceListener(SERVICE_TYPE, it) }
            jmdns?.close()
        } catch (_: Exception) {}
        jmdns = null
        listener = null
    }
}
