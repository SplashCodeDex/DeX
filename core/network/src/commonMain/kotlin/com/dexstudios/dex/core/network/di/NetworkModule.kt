package com.dexstudios.dex.core.network.di

import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.IDiscoveryService
import com.dexstudios.dex.core.network.MessageHandler
import com.dexstudios.dex.core.network.WebSocketEngine
import com.dexstudios.dex.core.network.HardwareTelemetry
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.auth.PairingEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

val commonNetworkModule = module {
    single<HttpClientEngine> {
        CIO.create {
            https {
                // The PC serves wss:// with an ephemeral self-signed certificate, so we trust all certs on the LAN
                @Suppress("TrustAllX509TrustManager", "CustomX509TrustManager")
                val trustAllManager = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }
                trustManager = trustAllManager
            }
        }
    }

    single {
        HttpClient(get<HttpClientEngine>()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(WebSockets) {
                pingInterval = 30.seconds
            }
        }
    }

    single { MessageHandler(get(), get()) }
    single { DiscoveryEngine(deviceConfig = get(), discoveryServices = getAll(), httpClient = get()) }
    single { ClientEngine(client = get(), quicClient = getOrNull(), deviceConfig = get()) }
    single { WebSocketEngine(client = get(), deviceConfig = get(), discoveryEngine = get(), messageHandler = get(), hardwareTelemetry = get(), mirrorEngine = get()) }
    single { PairingEngine(get()) }
}
