package com.dexstudios.dex.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class NsdManagerHelper(
    context: Context,
    private val localInfo: RegisterDto,
    private val onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start() {
        registerService()
        if (onDeviceDiscovered != null) {
            discoverServices()
        }
    }

    fun stop() {
        runCatching { registrationListener?.let { nsdManager.unregisterService(it) } }
        registrationListener = null
        runCatching { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        stopAllServiceInfoCallbacks()
    }

    private fun registerService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "DeX_Android"
            serviceType = "_dex._udp"
            port = localInfo.port
            setAttribute("alias", localInfo.alias)
            setAttribute("fingerprint", localInfo.fingerprint)
            setAttribute("identityHash", localInfo.identityHash)
            localInfo.googleSub?.let { setAttribute("googleSub", it) }
            setAttribute("deviceModel", localInfo.deviceModel)
            setAttribute("deviceType", localInfo.deviceType)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
    }

    /**
     * Parses the TXT attributes + address of a resolved service into a [DiscoveredDevice]
     * and reports it. Shared by the modern (API 34+) callback path and the legacy resolve path.
     */
    private fun handleResolved(serviceInfo: NsdServiceInfo) {
        try {
            val attributes = serviceInfo.attributes
            val fp = attributes["fingerprint"]?.let { String(it) }
            val alias = attributes["alias"]?.let { String(it) }

            if (fp.isNullOrEmpty() || fp == localInfo.fingerprint) return

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

            // host is deprecated in API 34+ in favor of hostAddresses; use the
            // version-appropriate accessor to avoid the deprecated call.
            val ip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceInfo.hostAddresses?.firstOrNull()?.hostAddress
            } else {
                @Suppress("DEPRECATION")
                serviceInfo.host?.hostAddress
            }
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
        }
    }

    // ---- Resolution: modern callback (API 34+) with legacy resolve fallback ----

    /**
     * On API 34+ each discovered service gets a persistent [NsdManager.ServiceInfoCallback]:
     * the system pushes updated service info (including IP changes) instead of answering a
     * single one-shot lookup, so no resolve queue is needed and stale IPs self-heal
     * immediately. On older Android we keep the original one-shot resolveService queue.
     */
    private val serviceInfoCallbacks = java.util.concurrent.ConcurrentHashMap<NsdServiceInfo, NsdManager.ServiceInfoCallback>()
    private val serviceInfoExecutor: Executor = Executors.newSingleThreadExecutor()

    private val resolveQueue = java.util.concurrent.ConcurrentLinkedQueue<NsdServiceInfo>()
    private var isResolving = false

    private fun resolveService(service: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerServiceInfoCallback(service)
        } else {
            resolveQueue.add(service)
            resolveNext()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerServiceInfoCallback(service: NsdServiceInfo) {
        // One callback per discovered service; re-registering the same instance is wasteful
        if (serviceInfoCallbacks.containsKey(service)) return

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Timber.e("NSD ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                handleResolved(serviceInfo)
            }

            override fun onServiceLost() {
                Timber.d("NSD service lost (ServiceInfoCallback)")
            }

            override fun onServiceInfoCallbackUnregistered() {}
        }

        try {
            nsdManager.registerServiceInfoCallback(service, serviceInfoExecutor, callback)
            serviceInfoCallbacks[service] = callback
        } catch (e: Exception) {
            Timber.e(e, "Failed to register ServiceInfoCallback")
        }
    }

    private fun stopAllServiceInfoCallbacks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        serviceInfoCallbacks.forEach { (_, callback) ->
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
        serviceInfoCallbacks.clear()
    }

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
                    handleResolved(serviceInfo)
                } finally {
                    isResolving = false
                    resolveNext()
                }
            }
        }

        runCatching {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(nextService, resolveListener)
        }.onFailure {
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
                    resolveService(service)
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
