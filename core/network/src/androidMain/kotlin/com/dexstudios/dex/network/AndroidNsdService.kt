package com.dexstudios.dex.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

import timber.log.Timber

class AndroidNsdService(
    private val context: Context
) : IDiscoveryService {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered
        registerService()
        discoverServices()
    }

    override fun stop() {
        runCatching { registrationListener?.let { nsdManager.unregisterService(it) } }
        registrationListener = null
        runCatching { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        resolveQueue.clear()
        isResolving = false
    }

    private fun registerService() {
        val info = localInfo ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "DeX_Android"
            serviceType = "_dex._udp"
            port = info.port
            setAttribute("alias", info.alias)
            setAttribute("fingerprint", info.fingerprint)
            setAttribute("identityHash", info.identityHash)
            info.googleSub?.let { setAttribute("googleSub", it) }
            setAttribute("deviceModel", info.deviceModel)
            setAttribute("deviceType", info.deviceType)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
    }

    private val resolveQueue = java.util.concurrent.ConcurrentLinkedQueue<NsdServiceInfo>()
    private var isResolving = false

    private fun resolveNext() {
        if (isResolving) return
        val nextService = resolveQueue.poll() ?: return
        isResolving = true

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.e("NSD Resolve failed: $errorCode")
                isResolving = false
                resolveNext()
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                try {
                    val info = localInfo ?: return
                    val attributes = serviceInfo.attributes
                    val fp = attributes["fingerprint"]?.let { String(it) }
                    val alias = attributes["alias"]?.let { String(it) }

                    if (fp.isNullOrEmpty() || fp == info.fingerprint) return

                    val identityHash = attributes["identityHash"]?.let { String(it) }
                    val googleSub = attributes["googleSub"]?.let { String(it) }
                    val deviceModel = attributes["deviceModel"]?.let { String(it) } ?: "Unknown"
                    val deviceType = attributes["deviceType"]?.let { String(it) } ?: "unknown"

                    val quicPort = attributes["quicPort"]?.let { String(it).toIntOrNull() } ?: DeXPorts.QUIC
                    val tcpFallbackPort = attributes["tcpFallbackPort"]?.let { String(it).toIntOrNull() } ?: DeXPorts.PULL

                    val dto = RegisterDto(
                        alias = alias ?: "Unknown",
                        version = "2.0",
                        deviceModel = deviceModel,
                        deviceType = deviceType,
                        fingerprint = fp,
                        port = serviceInfo.port,
                        protocol = "https",
                        download = true,
                        quicPort = quicPort,
                        tcpFallbackPort = tcpFallbackPort,
                        identityHash = identityHash,
                        googleSub = googleSub
                    )

                    val ip = serviceInfo.host?.hostAddress
                    if (!ip.isNullOrEmpty()) {
                        val device = DiscoveredDevice(
                            ip = ip,
                            info = dto,
                            viaWan = false,
                            viaRoster = false
                        )
                        onDeviceDiscovered?.invoke(device)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing NSD TXT records")
                } finally {
                    isResolving = false
                    resolveNext()
                }
            }
        }

        runCatching { nsdManager.resolveService(nextService, resolveListener) }.onFailure {
            Timber.e(it, "Failed to start NSD resolve")
            isResolving = false
            resolveNext()
        }
    }

    private fun discoverServices() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_dex._udp")) {
                    resolveQueue.add(service)
                    resolveNext()
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
        }

        runCatching { nsdManager.discoverServices("_dex._udp", NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
    }
}
