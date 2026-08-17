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
import org.koin.java.KoinJavaComponent.getKoin
import com.dexstudios.dex.core.network.DiscoveryEngine

object DeXServer {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = 48424, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(WebSockets) {
                pingPeriodMillis = 15000
                timeoutMillis = 15000
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
            }
        }.start(wait = false)

        println("DeXServer started on port 48424")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
