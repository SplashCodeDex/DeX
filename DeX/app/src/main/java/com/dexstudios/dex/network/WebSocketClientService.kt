package com.dexstudios.dex.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.*
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

// android.net.wifi.WifiInfo.RSSI_INVALID is a hidden API; mirror its value (-127)
private const val RSSI_INVALID = -127

/**
 * Builds the JSON telemetry payload for the PC.
 * Returns null when there is nothing to report (no battery, no wifi).
 */
internal fun buildTelemetryPayload(battery: Int, ssid: String?, rssi: Int): String? {
    if (battery < 0 && ssid == null && rssi == RSSI_INVALID) return null
    return buildJsonObject {
        put("type", "telemetry")
        putJsonObject("data") {
            if (battery >= 0) put("battery", battery)
            if (ssid != null) put("wifiSsid", ssid)
            if (rssi != RSSI_INVALID) put("wifiRssi", rssi)
        }
    }.toString()
}

class WebSocketClientService(
    private val deviceConfig: DeviceConfig,
    private val discoveryEngine: DiscoveryEngine,
    private val messageHandler: MessageHandler,
    private val context: Context
) {
    // The PC serves wss:// with an ephemeral self-signed certificate, so we trust all certs on the LAN
    @android.annotation.SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val sslContext: SSLContext by lazy {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustAllManager), SecureRandom())
        context
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustAllManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    private var activeSocket: WebSocket? = null
    private var _connectedFingerprint: String? = null

    /** Fingerprint of the PC the socket is currently connected to. */
    val connectedFingerprint: String? get() = _connectedFingerprint

    /** The PC address the current socket is connected to (LAN IP or public address). */
    @Volatile
    var connectedIp: String? = null

    @Volatile
    private var connectedViaWan = false

    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning = false

    /** Reconnect to a known PC without waiting for a new discovery event (background keep-alive). */
    fun ensureConnected() {
        if (!isRunning || activeSocket != null) return
        // Probe the last-known PC so a killed process reconnects in seconds, not on the
        // next discovery broadcast or keep-alive cycle
        PcMemory.ip(context)?.let { discoveryEngine.sendManualDiscovery(it) }
        findTargetPc(discoveryEngine.devices.value.values)?.let { connectToPC(it) }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        Timber.i("Starting WebSocketClientService...")

        messageHandler.onSendMessage = { sendMessage(it) }
        MirrorSession.textSender = { sendMessage(it) }
        MirrorSession.frameSender = { sendBinary(it) }

        serviceScope.launch {
            // Monitor discovered devices for PCs to connect to (prefer an already-paired PC)
            discoveryEngine.devices.collectLatest { devices ->
                val pcDevice = findTargetPc(devices.values)
                if (pcDevice != null && activeSocket == null) {
                    connectToPC(pcDevice)
                } else if (pcDevice != null && connectedViaWan && !pcDevice.viaWan &&
                    pcDevice.info.fingerprint == _connectedFingerprint) {
                    // The PC we reached over WAN just appeared on the LAN: switch to the fast path
                    Timber.i("PC appeared on LAN; switching from WAN to LAN")
                    activeSocket?.close(1000, "Switching to LAN")
                    activeSocket = null
                    connectedViaWan = false
                    connectToPC(pcDevice)
                }
            }
        }

        serviceScope.launch {
            // Keep the same-email roster fresh so devices that come online later show up
            while (isActive) {
                delay(60_000)
                if (isRunning && activeSocket != null) {
                    sendMessage("""{"type":"device-roster","data":{}}""")
                    sendTelemetry()
                }
            }
        }
    }

    /** Current battery percentage, or -1 when unavailable. */
    private fun batteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return -1
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level >= 0) level else -1
    }

    /** Current WiFi network: (SSID, RSSI dBm). SSID is null and RSSI -127 when not connected. */
    private fun wifiInfo(): Pair<String?, Int> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null to RSSI_INVALID
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return null to RSSI_INVALID
        val ssid = info.ssid?.trim()?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return ssid to info.rssi
    }

    /** Reports device telemetry (battery + WiFi) to the connected PC over the WebSocket. */
    fun sendTelemetry() {
        val level = batteryLevel()
        val (ssid, rssi) = wifiInfo()
        val payload = buildTelemetryPayload(level, ssid, rssi) ?: return
        sendMessage(payload)
        Timber.d("Telemetry sent: battery $level%, wifi $ssid ($rssi dBm)")
    }

    private fun findTargetPc(devices: Collection<DiscoveredDevice>): DiscoveredDevice? {
        val desktops = devices.filter { it.info.deviceType.equals("desktop", ignoreCase = true) }
        // 1. Prefer a PC discovered on the LAN (last-used, then paired, then any desktop).
        //    LAN traffic must never route through the internet — WAN is only a fallback.
        val lastFingerprint = PcMemory.fingerprint(context)
        if (lastFingerprint != null) {
            desktops.firstOrNull { it.info.fingerprint == lastFingerprint }?.let { return it }
        }
        desktops.firstOrNull { AuthState.pairedTokens.containsKey(it.info.fingerprint) }?.let { return it }
        desktops.firstOrNull()?.let { return it }
        // 2. WAN override: only when no PC is visible on the LAN
        return wanTarget()
    }

    /** Synthetic target for the configured public (WAN) address. */
    private fun wanTarget(): DiscoveredDevice? {
        val address = deviceConfig.publicAddress.trim()
        if (address.isBlank()) return null
        val fingerprint = PcMemory.fingerprint(context) ?: AuthState.pairedTokens.keys.firstOrNull() ?: return null
        return DiscoveredDevice(
            ip = address,
            info = RegisterDto(
                alias = "PC (WAN)",
                version = "2.0",
                deviceModel = "PC",
                deviceType = "desktop",
                fingerprint = fingerprint,
                port = DeXPorts.HTTPS,
                protocol = "https",
                download = false,
                identityHash = null
            ),
            viaWan = true
        )
    }

    fun stop() {
        isRunning = false
        activeSocket?.close(1000, "Service stopping")
        activeSocket = null
        serviceScope.cancel()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun connectToPC(pcDevice: DiscoveredDevice) {
        val fingerprint = deviceConfig.fingerprint
        val alias = java.net.URLEncoder.encode(getDeviceName(context), "UTF-8")
        val pcFingerprint = pcDevice.info.fingerprint

        // Same-email devices are auto-trusted. Token order: Google account ID (unguessable)
        // when both sides share it, then the identity hash, then the PIN-pairing token.
        val identityHash = deviceConfig.identityHash
        val googleSub = deviceConfig.googleSub
        val pcIdentityHash = pcDevice.info.identityHash
        val pcGoogleSub = pcDevice.info.googleSub
        val token = when {
            googleSub.isNotEmpty() && googleSub == pcGoogleSub -> googleSub
            identityHash.isNotEmpty() && identityHash == pcIdentityHash -> identityHash
            else -> AuthState.pairedTokens[pcFingerprint]
        }
        val tokenParam = if (!token.isNullOrEmpty()) "&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else ""

        // The PC serves TLS on port 48424, so we must use wss:// (plain ws:// fails TLS negotiation)
        val url = "wss://${pcDevice.ip}:${pcDevice.info.port}/ws?fingerprint=$fingerprint&alias=$alias$tokenParam"
        Timber.i("Connecting to PC via WebSocket: ${pcDevice.ip}:${pcDevice.info.port}")

        val request = Request.Builder().url(url).build()

        // Remember this PC so we reconnect to the same one after restarts — but only
        // LAN addresses: a WAN connection must never overwrite the LAN IP memory
        if (!pcDevice.viaWan) {
            PcMemory.save(context, pcFingerprint, pcDevice.ip)
        }
        connectedViaWan = pcDevice.viaWan
        connectedIp = pcDevice.ip

        activeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectedFingerprint = pcFingerprint
                // Same-email trust is permanent, not session-scoped: remember the device so
                // future transfers and UI trust badges work without another handshake
                if (token == googleSub || (token == identityHash && !identityHash.isNullOrEmpty())) {
                    DeviceManager.savePairedFingerprint(pcFingerprint)
                    DeviceManager.savePairedToken(pcFingerprint, token)
                }
                // Ask the PC which of our same-email devices are online (direct punch transfers)
                sendMessage("""{"type":"device-roster","data":{}}""")
                // Report battery immediately so the PC has telemetry on connect, not after 60s
                sendTelemetry()
                Timber.i("WebSocket connected to PC: ${pcDevice.ip}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received WebSocket message: $text")
                messageHandler.handleMessage(text, pcDevice.ip, pcDevice.info.port)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket closed: $code $reason")
                // Only clear state if this socket is still the active one (stale callbacks
                // must never clobber a newer connection)
                if (activeSocket === webSocket) {
                    activeSocket = null
                    _connectedFingerprint = null
                    connectedViaWan = false
                    connectedIp = null
                    // The PC is gone: never keep capturing/streaming into a dead socket
                    MirrorSession.stop()
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket failure")
                if (activeSocket === webSocket) {
                    activeSocket = null
                    _connectedFingerprint = null
                    connectedViaWan = false
                    connectedIp = null
                    MirrorSession.stop()
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        // Simple backoff before trying again; the collectLatest block also retries on new discovery
        serviceScope.launch {
            delay(5000)
            if (isRunning && activeSocket == null) {
                findTargetPc(discoveryEngine.devices.value.values)?.let { connectToPC(it) }
            }
        }
    }

    fun sendMessage(jsonMessage: String) {
        activeSocket?.send(jsonMessage) ?: Timber.w("Cannot send message, socket is null")
    }

    /** Sends a binary frame (used for screen-mirror JPEG frames). */
    fun sendBinary(bytes: ByteArray) {
        activeSocket?.send(okio.ByteString.of(*bytes)) ?: Timber.w("Cannot send binary, socket is null")
    }

    /** Asks the PC we are connected to (if it is [targetFingerprint]) to start a PIN pairing. */
    fun sendPairRequest(targetFingerprint: String): Boolean {
        val socket = activeSocket ?: return false
        if (_connectedFingerprint != targetFingerprint) {
            Timber.w("Not connected to requested PC, cannot send pair request")
            return false
        }
        return socket.send("""{"type":"pair-request"}""")
    }
}
