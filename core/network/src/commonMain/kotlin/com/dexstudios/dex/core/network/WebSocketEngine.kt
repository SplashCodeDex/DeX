package com.dexstudios.dex.core.network

import com.dexstudios.dex.auth.AuthState
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Builds the JSON telemetry payload for the PC.
 * Returns null when there is nothing to report (no battery, no wifi).
 */
internal fun buildTelemetryPayload(battery: Int, ssid: String?, rssi: Int): String? {
    if ((battery < 0 && ssid == null && rssi == HardwareTelemetry.RSSI_INVALID)) return null
    return buildJsonObject {
        put("type", "telemetry")
        putJsonObject("data") {
            if (battery >= 0) put("battery", battery)
            if (ssid != null) put("wifiSsid", ssid)
            if (rssi != HardwareTelemetry.RSSI_INVALID) put("wifiRssi", rssi)
        }
    }.toString()
}

class WebSocketEngine(
    private val client: HttpClient,
    private val deviceConfig: DeviceConfig,
    private val discoveryEngine: DiscoveryEngine,
    private val messageHandler: MessageHandler,
    private val hardwareTelemetry: HardwareTelemetry,
    private val mirrorEngine: IMirrorEngine,
) {
    private var activeSession: DefaultClientWebSocketSession? = null
    private var _connectedFingerprint: String? = null

    /** Fingerprint of the PC the socket is currently connected to. */
    val connectedFingerprint: String? get() = _connectedFingerprint

    /** The PC address the current socket is connected to (LAN IP or public address). */
    @Volatile
    var connectedIp: String? = null

    /** The PC port the current socket is connected to. */
    @Volatile
    var connectedPort: Int = DeXPorts.HTTPS

    @Volatile
    private var connectedViaWan = false

    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning = false

    /** Reconnect to a known PC without waiting for a new discovery event. */
    fun ensureConnected() {
        if (!isRunning || activeSession != null) return
        serviceScope.launch {
            PcMemory.ip()?.let { discoveryEngine.sendManualDiscovery(it) }
            findTargetPc(discoveryEngine.devices.value.values)?.let { connectToPC(it) }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        messageHandler.onSendMessage = { sendMessage(it) }
        mirrorEngine.textSender = { sendMessage(it) }
        mirrorEngine.frameSender = { sendBinary(it) }

        serviceScope.launch {
            discoveryEngine.devices.collectLatest { devices ->
                val pcDevice = findTargetPc(devices.values)
                if (pcDevice != null && activeSession == null) {
                    connectToPC(pcDevice)
                } else if (pcDevice != null && connectedViaWan && !pcDevice.viaWan &&
                    pcDevice.info.fingerprint == _connectedFingerprint
                ) {
                    activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Switching to LAN"))
                    activeSession = null
                    connectedViaWan = false
                    connectToPC(pcDevice)
                }
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(1.minutes)
                if (isRunning && activeSession != null) {
                    sendMessage("""{"type":"device-roster","data":{}}""")
                    sendTelemetry()
                }
            }
        }
    }

    fun sendTelemetry() {
        val level = hardwareTelemetry.getBatteryLevel()
        val (ssid, rssi) = hardwareTelemetry.getWifiInfo()
        val payload = buildTelemetryPayload(level, ssid, rssi) ?: return
        sendMessage(payload)
    }

    private suspend fun findTargetPc(devices: Collection<DiscoveredDevice>): DiscoveredDevice? {
        val desktops = devices.filter { it.info.deviceType.equals("desktop", ignoreCase = true) }
        val lastFingerprint = PcMemory.fingerprint()
        if (lastFingerprint != null) {
            desktops.firstOrNull { it.info.fingerprint == lastFingerprint }?.let { return it }
        }
        desktops.firstOrNull { AuthState.pairedTokens.value.containsKey(it.info.fingerprint) }?.let { return it }
        desktops.firstOrNull()?.let { return it }
        return wanTarget()
    }

    private suspend fun wanTarget(): DiscoveredDevice? {
        val address = deviceConfig.publicAddress.trim()
        if (address.isBlank()) return null
        val fingerprint = PcMemory.fingerprint() ?: AuthState.pairedTokens.value.keys.firstOrNull() ?: return null
        return DiscoveredDevice(
            ip = address,
            info = RegisterDto(
                alias = "PC (WAN)",
                version = "2.0",
                deviceModel = "PC",
                deviceType = "desktop",
                fingerprint = fingerprint,
                port = PcMemory.port(),
                quicPort = PcMemory.quicPort(),
                tcpFallbackPort = DeXPorts.PULL,
                protocol = "https",
                download = false,
                identityHash = null,
            ),
            viaWan = true,
        )
    }

    fun stop() {
        isRunning = false
        serviceScope.launch {
            activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Service stopping"))
            activeSession = null
        }
        serviceScope.cancel()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun connectToPC(pcDevice: DiscoveredDevice, onConnected: (() -> Unit)? = null) {
        val fingerprint = deviceConfig.fingerprint
        // We use a safe default alias if context isn't directly available, or read from DeviceConfig
        val alias = java.net.URLEncoder.encode(deviceConfig.alias.ifBlank { "DeX Device" }, "UTF-8")
        val pcFingerprint = pcDevice.info.fingerprint

        val isLoggedIn = deviceConfig.email.isNotBlank()
        val identityHash = if (isLoggedIn) deviceConfig.identityHash else ""
        val googleSub = if (isLoggedIn) deviceConfig.googleSub else ""
        val pcIdentityHash = pcDevice.info.identityHash
        val pcGoogleSub = pcDevice.info.googleSub
        val token = when {
            googleSub.isNotEmpty() && googleSub == pcGoogleSub -> googleSub
            identityHash.isNotEmpty() && identityHash == pcIdentityHash -> identityHash
            else -> AuthState.pairedTokens.value[pcFingerprint]
        }
        val tokenParam = if (!token.isNullOrEmpty()) "&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else ""

        val url = "wss://${pcDevice.ip}:${pcDevice.info.port}/ws?fingerprint=$fingerprint&alias=$alias$tokenParam"

        if (!pcDevice.viaWan) {
            serviceScope.launch {
                PcMemory.save(pcFingerprint, pcDevice.ip, pcDevice.info.port, pcDevice.info.quicPort)
            }
        }
        connectedViaWan = pcDevice.viaWan
        connectedIp = pcDevice.ip
        connectedPort = pcDevice.info.port

        serviceScope.launch {
            try {
                client.webSocket(url) {
                    activeSession = this
                    _connectedFingerprint = pcFingerprint

                    if (token == googleSub || (token == identityHash && identityHash.isNotEmpty())) {
                        DeviceManager.savePairedFingerprint(pcFingerprint)
                        DeviceManager.savePairedToken(pcFingerprint, token)
                    }

                    sendMessage("""{"type":"device-roster","data":{}}""")

                    val isTrusted = AuthState.pairedFingerprints.value.contains(pcFingerprint) || token == googleSub || (token == identityHash && identityHash.isNotEmpty())
                    val trustCheck = buildJsonObject {
                        put("type", "trust-check")
                        putJsonObject("data") {
                            put("isTrusted", isTrusted)
                        }
                    }.toString()
                    sendMessage(trustCheck)
                    sendTelemetry()
                    onConnected?.invoke()

                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> messageHandler.handleMessage(frame.readText(), pcDevice.ip, pcDevice.info.port)
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        handleDisconnect()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        activeSession = null
        _connectedFingerprint = null
        connectedViaWan = false
        connectedIp = null
        mirrorEngine.stop()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        serviceScope.launch {
            delay(5.seconds)
            if (isRunning && activeSession == null) {
                findTargetPc(discoveryEngine.devices.value.values)?.let { connectToPC(it) }
            }
        }
    }

    fun sendMessage(jsonMessage: String) {
        serviceScope.launch {
            activeSession?.send(Frame.Text(jsonMessage))
        }
    }

    fun sendBinary(bytes: ByteArray) {
        serviceScope.launch {
            activeSession?.send(Frame.Binary(true, bytes))
        }
    }

    fun sendPairRequest(targetFingerprint: String): Boolean {
        if (_connectedFingerprint != targetFingerprint) return false
        sendMessage("""{"type":"pair-request"}""")
        return true
    }

    fun requestPairingWith(targetPc: DiscoveredDevice, onResult: (Boolean) -> Unit) {
        val fp = targetPc.info.fingerprint
        if (activeSession != null && _connectedFingerprint == fp) {
            onResult(sendPairRequest(fp))
            return
        }
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { ok ->
            if (settled.compareAndSet(false, true)) onResult(ok)
        }
        serviceScope.launch {
            activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Switching to tapped PC"))
            activeSession = null
        }
        connectToPC(targetPc) {
            finish(sendPairRequest(fp))
        }
        serviceScope.launch {
            delay(6.seconds)
            finish(false)
        }
    }

    fun sendUnpairRequest(targetFingerprint: String): Boolean {
        if (_connectedFingerprint != targetFingerprint) return false
        sendMessage("""{"type":"unpair"}""")
        return true
    }

    fun requestUnpairWith(targetPc: DiscoveredDevice, onResult: (Boolean) -> Unit = {}) {
        val fp = targetPc.info.fingerprint
        if (activeSession != null && _connectedFingerprint == fp) {
            onResult(sendUnpairRequest(fp))
            return
        }
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { ok ->
            if (settled.compareAndSet(false, true)) onResult(ok)
        }
        serviceScope.launch {
            activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Switching to tapped PC for unpair"))
            activeSession = null
        }
        connectToPC(targetPc) {
            finish(sendUnpairRequest(fp))
        }
        serviceScope.launch {
            delay(6.seconds)
            finish(false)
        }
    }
}
