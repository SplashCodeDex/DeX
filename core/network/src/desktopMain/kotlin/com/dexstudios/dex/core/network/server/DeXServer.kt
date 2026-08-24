package com.dexstudios.dex.core.network.server

import co.touchlab.kermit.Logger
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.security.CertificateGenerator
import com.dexstudios.dex.core.network.server.routes.clipboardRoutes
import com.dexstudios.dex.core.network.server.routes.controlRoutes
import com.dexstudios.dex.core.network.server.routes.deviceRoutes
import com.dexstudios.dex.core.network.server.routes.fileExplorerRoutes
import com.dexstudios.dex.core.network.server.routes.hostedDownloadRoutes
import com.dexstudios.dex.core.network.server.routes.settingsRoutes
import com.dexstudios.dex.core.network.server.routes.shareRoutes
import com.dexstudios.dex.core.network.server.routes.webSocketRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.time.Duration.Companion.seconds

object DeXServer {
    private var server1: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var server2: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var server3: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server1 != null) return

        val appModule: Application.() -> Unit = {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                val discoveryEngine = getKoin().get<DiscoveryEngine>()
                val pairingEngine = getKoin().get<PairingEngine>()
                val mirrorEngine = getKoin().get<com.dexstudios.dex.core.network.IMirrorEngine>()

                pairingEngine.outboundSender = { fp, json ->
                    WebSocketConnectionManager.sendRequest(fp, json)
                }

                deviceRoutes(discoveryEngine = discoveryEngine, pairingEngine = pairingEngine)
                shareRoutes()
                controlRoutes()
                webSocketRoutes(
                    pairingEngine,
                    mirrorEngine,
                    publicAddressService = runCatching {
                        getKoin().get<com.dexstudios.dex.core.network.services.PublicAddressService>()
                    }.getOrNull(),
                )
                fileExplorerRoutes()
                clipboardRoutes()
                settingsRoutes()
            }
        }

        val keyStore = CertificateGenerator.getOrCreateKeyStore()

        server1 = embeddedServer(Netty, configure = {
            sslConnector(
                keyStore = keyStore,
                keyAlias = "dex",
                keyStorePassword = { CertificateGenerator.getPassword().toCharArray() },
                privateKeyPassword = { CertificateGenerator.getPassword().toCharArray() },
            ) {
                host = "0.0.0.0"
                port = 48424
                keyStorePath = java.io.File(System.getProperty("java.io.tmpdir"), "dex_cert.jks")
            }
        }, module = appModule).start(wait = false)
        server2 = embeddedServer(Netty, port = 28425, host = "127.0.0.1", module = appModule).start(wait = false)

        // Plain-HTTP fallback listener: exposes ONLY the hosted-file pull endpoints. Serving the
        // full application over plaintext on 0.0.0.0 let any LAN peer hit /ws, /register and
        // /upload without TLS — the pull fallback never needed more than downloads.
        server3 = embeddedServer(Netty, port = 48426, host = "0.0.0.0", module = {
            routing {
                hostedDownloadRoutes()
            }
        }).start(wait = false)

        Logger.i("DeXServer started on HTTPS port 48424, HTTP 28425 (loopback), and HTTP 48426 (pull fallback, downloads only)")
    }

    fun stop() {
        server1?.stop(1000, 2000)
        server2?.stop(1000, 2000)
        server3?.stop(1000, 2000)
        server1 = null
        server2 = null
        server3 = null
    }
}
