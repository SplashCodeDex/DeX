package com.dexstudios.dex.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Punch rendezvous + socket acquisition (plan 042 — moved verbatim from PunchSession).
 *
 * Owns everything the punch handshake needs BEFORE any file bytes flow:
 * the TLS identity used to register our public endpoint with the PC
 * (peer-phone certs are pinned trust-on-first-use), the endpoint-reflection
 * call, and the simultaneous-open NAT punch itself.
 *
 * Lifecycle remains owned by [PunchSession]: [serverSocketProvider] always
 * reflects the session's CURRENT listener (created in start(), nulled in
 * stop()), and [isActive] mirrors the session scope so a stopped session
 * aborts a punch mid-loop exactly as before. Foreign inbound connections
 * (a punch that landed on our listener from a DIFFERENT peer) are handed to
 * [onForeignConnection] — PunchSession launches them on its own scope, so
 * cancellation semantics are byte-identical to the pre-split code.
 */
internal class PunchSocketConnector(
    private val context: Context,
    private val wsService: WebSocketClientService,
    private val deviceConfig: DeviceConfig,
    private val serverSocketProvider: () -> ServerSocket?,
    private val isActive: () -> Boolean,
    private val onForeignConnection: (Socket) -> Unit,
) {
    // Peer phones use self-signed certs; pin each peer's cert trust-on-first-use so a
    // changed cert (MITM on the LAN) is rejected instead of silently trusted.
    private val pinnedTrustManager = PinnedTrustManager(context)

    private val sslContext: SSLContext by lazy {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(pinnedTrustManager), SecureRandom())
        ctx
    }

    /**
     * Reflects our public TCP endpoint via the PC: we connect FROM the listener port, so
     * the PC answers with the source address of this connection — our NAT public endpoint.
     */
    internal suspend fun registerEndpoint() = withContext(Dispatchers.IO) {
        val ss = serverSocketProvider() ?: return@withContext
        try {
            val pcIp = wsService.connectedIp ?: PcMemory.ip(context) ?: return@withContext
            val localPort = ss.localPort
            val socket = Socket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", localPort))
            }
            socket.connect(InetSocketAddress(pcIp, wsService.connectedPort), 5000)
            val ssl = sslContext.socketFactory.createSocket(socket, pcIp, wsService.connectedPort, true) as SSLSocket
            pinnedTrustManager.setExpectedHost(pcIp)
            ssl.startHandshake()
            val request = "GET /punch/endpoint?fingerprint=${URLEncoder.encode(deviceConfig.fingerprint, "UTF-8")} HTTP/1.1\r\n" +
                "Host: $pcIp\r\nConnection: close\r\n\r\n"
            ssl.getOutputStream().write(request.toByteArray())
            ssl.getOutputStream().flush()
            val body = String(ssl.getInputStream().readBytes()).substringAfter("\r\n\r\n")
            val reflected = DexJson.decodeFromString<EndpointInfoDto>(body)
            ssl.close()
            Timber.i("Punch endpoint registered: ${reflected.ip}:${reflected.port}")
        } catch (e: Exception) {
            Timber.e(e, "Punch endpoint registration failed")
        }
    }

    /**
     * Simultaneous-open NAT punch: outbound connects bound to the listener port, racing an
     * accept on the same listener. Returns the first usable socket or null on timeout.
     */
    internal suspend fun punch(ip: String, port: Int, isCancelled: () -> Boolean): Socket? = withContext(Dispatchers.IO) {
        val ss = serverSocketProvider() ?: return@withContext null
        val localPort = ss.localPort
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline && !isCancelled()) {
            // Outbound simultaneous-open attempt, source port = listener port
            try {
                val s = Socket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", localPort))
                }
                s.connect(InetSocketAddress(ip, port), 800)
                s.tcpNoDelay = true
                Timber.i("Punch connect succeeded to $ip:$port")
                return@withContext s
            } catch (e: Exception) {
                // expected while the NAT mapping is being established
            }

            // The peer's own punch may have landed on our listener first
            try {
                val accepted = ss.accept()
                if (accepted.inetAddress.hostAddress == ip) {
                    accepted.tcpNoDelay = true
                    Timber.i("Punch accept succeeded from $ip")
                    return@withContext accepted
                }
                onForeignConnection(accepted)
            } catch (_: SocketTimeoutException) {
                // No inbound punch this round — retry the outbound attempt
            } catch (e: Exception) {
                if (!isActive()) return@withContext null
            }
            delay(250.milliseconds)
        }
        Timber.w("Punch failed for $ip:$port")
        null
    }
}

internal fun closeQuietly(socket: Socket) {
    try { socket.close() } catch (_: Exception) {}
}
