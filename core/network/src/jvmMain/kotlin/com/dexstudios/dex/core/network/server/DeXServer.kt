package com.dexstudios.dex.core.network.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.dexstudios.dex.core.network.server.routes.deviceRoutes
import com.dexstudios.dex.core.network.server.routes.shareRoutes
import com.dexstudios.dex.core.network.server.routes.controlRoutes
import com.dexstudios.dex.core.network.server.routes.webSocketRoutes
import com.dexstudios.dex.core.network.server.routes.fileExplorerRoutes
import com.dexstudios.dex.core.network.server.routes.clipboardRoutes
import com.dexstudios.dex.core.network.server.routes.settingsRoutes
import com.dexstudios.dex.core.network.security.CertificateGenerator
import org.koin.java.KoinJavaComponent.getKoin
import com.dexstudios.dex.core.network.DiscoveryEngine
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.plugins.origin
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.seconds

val LoopbackSecurityPlugin = createApplicationPlugin(name = "LoopbackSecurity") {
    onCall { call ->
        val path = call.request.path()
        if (path.startsWith("/local/")) {
            val remoteHost = call.request.origin.remoteHost
            if (remoteHost != "127.0.0.1" && remoteHost != "0:0:0:0:0:0:0:1" && remoteHost != "localhost") {
                call.respond(HttpStatusCode.Forbidden, "Access Denied")
            }
        }
    }
}

object DeXServer {
    private var server1: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var server2: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var server3: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server1 != null) return

        val appModule: Application.() -> Unit = {
            install(LoopbackSecurityPlugin)
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                val discoveryEngine = getKoin().get<DiscoveryEngine>()
                deviceRoutes(discoveryEngine = discoveryEngine)
                shareRoutes()
                controlRoutes()
                webSocketRoutes()
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
                keyStorePassword = { CertificateGenerator.PASSWORD.toCharArray() },
                privateKeyPassword = { CertificateGenerator.PASSWORD.toCharArray() }
            ) {
                host = "0.0.0.0"
                port = 48424
                keyStorePath = java.io.File(System.getProperty("java.io.tmpdir"), "dex_cert.jks")
            }
        }, module = appModule).start(wait = false)
        server2 = embeddedServer(Netty, port = 28425, host = "127.0.0.1", module = appModule).start(wait = false)
        server3 = embeddedServer(Netty, port = 48426, host = "0.0.0.0", module = appModule).start(wait = false)
        
        println("DeXServer started on HTTPS port 48424, HTTP 28425 (loopback), and HTTP 48426 (tcp fallback)")
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
