package com.example.dex.network

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import okhttp3.*
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

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
    private var connectedFingerprint: String? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Timber.i("Starting WebSocketClientService...")

        messageHandler.onSendMessage = { sendMessage(it) }

        serviceScope.launch {
            // Monitor discovered devices for PCs to connect to (prefer an already-paired PC)
            discoveryEngine.devices.collectLatest { devices ->
                val pcDevice = findTargetPc(devices.values)
                if (pcDevice != null && activeSocket == null) {
                    connectToPC(pcDevice)
                }
            }
        }
    }

    private fun findTargetPc(devices: Collection<DiscoveredDevice>): DiscoveredDevice? {
        val desktops = devices.filter { it.info.deviceType.equals("desktop", ignoreCase = true) }
        if (desktops.isEmpty()) return null
        // 1. Prefer the PC used last time (persistent memory across restarts)
        val lastFingerprint = PcMemory.fingerprint(context)
        if (lastFingerprint != null) {
            desktops.firstOrNull { it.info.fingerprint == lastFingerprint }?.let { return it }
        }
        // 2. Fall back to an already-paired PC
        return desktops.firstOrNull { AuthState.pairedTokens.containsKey(it.info.fingerprint) }
            ?: desktops.firstOrNull()
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

        // Paired devices present their pairing token; unpaired devices connect tokenless
        val token = AuthState.pairedTokens[pcFingerprint]
        val tokenParam = if (!token.isNullOrEmpty()) "&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else ""

        // The PC serves TLS on port 53317, so we must use wss:// (plain ws:// fails TLS negotiation)
        val url = "wss://${pcDevice.ip}:${pcDevice.info.port}/ws?fingerprint=$fingerprint&alias=$alias$tokenParam"
        Timber.i("Connecting to PC via WebSocket: ${pcDevice.ip}:${pcDevice.info.port}")

        val request = Request.Builder().url(url).build()

        // Remember this PC so we reconnect to the same one after restarts
        PcMemory.save(context, pcFingerprint, pcDevice.ip)

        activeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connectedFingerprint = pcFingerprint
                Timber.i("WebSocket connected to PC: ${pcDevice.ip}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received WebSocket message: $text")
                messageHandler.handleMessage(text, pcDevice.ip, pcDevice.info.port)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket closed: $code $reason")
                activeSocket = null
                connectedFingerprint = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket failure")
                activeSocket = null
                connectedFingerprint = null
                scheduleReconnect()
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

    /** Asks the PC we are connected to (if it is [targetFingerprint]) to start a PIN pairing. */
    fun sendPairRequest(targetFingerprint: String): Boolean {
        val socket = activeSocket ?: return false
        if (connectedFingerprint != targetFingerprint) {
            Timber.w("Not connected to requested PC, cannot send pair request")
            return false
        }
        return socket.send("""{"type":"pair-request"}""")
    }
}
