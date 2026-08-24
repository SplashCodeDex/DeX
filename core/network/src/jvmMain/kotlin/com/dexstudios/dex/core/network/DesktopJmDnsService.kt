package com.dexstudios.dex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class DesktopJmDnsService : IDiscoveryService {
    private val logger = LoggerFactory.getLogger(DesktopJmDnsService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    private var jmdns: JmDNS? = null
    private var serviceInfo: ServiceInfo? = null
    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null
    private var jmDnsJob: Job? = null

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns?.requestServiceInfo(event.type, event.name)
        }

        override fun serviceRemoved(event: ServiceEvent) {}

        override fun serviceResolved(event: ServiceEvent) {
            val info = localInfo ?: return
            try {
                val resolvedInfo = event.info
                val fp = resolvedInfo.getPropertyString("fingerprint")
                val alias = resolvedInfo.getPropertyString("alias")

                if (fp.isNullOrEmpty() || fp == info.fingerprint) return

                val identityHash = resolvedInfo.getPropertyString("identityHash")
                val googleSub = resolvedInfo.getPropertyString("googleSub")
                val deviceModel = resolvedInfo.getPropertyString("deviceModel") ?: "Unknown"
                val deviceType = resolvedInfo.getPropertyString("deviceType") ?: "unknown"

                val quicPort = resolvedInfo.getPropertyString("quicPort")?.toIntOrNull() ?: DeXPorts.QUIC
                val tcpFallbackPort = resolvedInfo.getPropertyString("tcpFallbackPort")?.toIntOrNull() ?: DeXPorts.PULL

                val dto = RegisterDto(
                    alias = alias ?: "Unknown",
                    version = "2.0",
                    deviceModel = deviceModel,
                    deviceType = deviceType,
                    fingerprint = fp,
                    port = resolvedInfo.port,
                    protocol = "https",
                    download = true,
                    quicPort = quicPort,
                    tcpFallbackPort = tcpFallbackPort,
                    identityHash = identityHash,
                    googleSub = googleSub,
                )

                val ip = resolvedInfo.hostAddresses?.firstOrNull()
                if (!ip.isNullOrEmpty()) {
                    val device = DiscoveredDevice(
                        ip = ip,
                        info = dto,
                        viaWan = false,
                        viaRoster = false,
                    )
                    onDeviceDiscovered?.invoke(device)
                }
            } catch (e: Exception) {
                logger.error("Error parsing JmDNS resolved service", e)
            }
        }
    }

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered

        jmDnsJob = scope.launch {
            runCatching {
                val localInetAddress = getActiveLanInterface() ?: InetAddress.getLocalHost()
                jmdns = JmDNS.create(localInetAddress, "dex-desktop-jmdns")

                val props = mutableMapOf<String, String>()
                props["alias"] = localInfo.alias
                props["fingerprint"] = localInfo.fingerprint
                localInfo.identityHash?.let { props["identityHash"] = it }
                localInfo.googleSub?.let { props["googleSub"] = it }
                props["deviceModel"] = localInfo.deviceModel
                props["deviceType"] = localInfo.deviceType
                props["port"] = localInfo.port.toString()
                props["quicPort"] = localInfo.quicPort.toString()
                props["tcpFallbackPort"] = localInfo.tcpFallbackPort.toString()

                serviceInfo = ServiceInfo.create("_dex._udp.local.", "DeX_Desktop", localInfo.port, 0, 0, props)

                jmdns?.registerService(serviceInfo)
                jmdns?.addServiceListener("_dex._udp.local.", serviceListener)
            }.onFailure {
                logger.error("Failed to start JmDNS", it)
            }
        }
    }

    override fun stop() {
        jmDnsJob?.cancel()
        runCatching {
            jmdns?.removeServiceListener("_dex._udp.local.", serviceListener)
            serviceInfo?.let { jmdns?.unregisterService(it) }
            jmdns?.close()
        }
        jmdns = null
        serviceInfo = null
    }

    private fun getActiveLanInterface(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue

                val name = ni.displayName.lowercase()
                if (name.contains("vmware") || name.contains("virtualbox") ||
                    name.contains("vethernet") || name.contains("wsl") || name.contains("hyper-v")
                ) {
                    continue
                }

                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        return addr
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to discover active LAN interface", e)
        }
        return null
    }
}
