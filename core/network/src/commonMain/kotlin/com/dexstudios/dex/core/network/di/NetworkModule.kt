package com.dexstudios.dex.core.network.di

import com.dexstudios.dex.core.domain.pairing.PairingEngine
import com.dexstudios.dex.core.domain.pairing.PairingGrantStore
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DeviceManagerPairingGrantStore
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.HardwareTelemetry
import com.dexstudios.dex.core.network.IDiscoveryService
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.core.network.MessageHandler
import com.dexstudios.dex.core.network.WebSocketEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
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
            // Bound only at the connect stage: no global request timeout, because this
            // client also streams multi-GB file uploads (ClientEngine.uploadFile).
            // Short-lived calls (e.g. the manual discovery probe) override per-request.
            install(HttpTimeout) {
                connectTimeoutMillis = 10.seconds.inWholeMilliseconds
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
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
    single<PairingGrantStore> { DeviceManagerPairingGrantStore() }
    single { PairingEngine(grantStore = get()) }
    single {
        com.dexstudios.dex.core.domain.transfer.TransferUseCase(
            lingerScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
            ),
        )
    }

    // Sync layer (plan 031): the DataStore-backed SyncStorage adapter lives in core/data;
    // the HLC gets the platform wall clock; the engine's device identity is OUR fingerprint
    // (stable across restarts, unique per device — exactly the sync author semantic).
    // The persisted HLC state is restored (monotonic-only) at graph construction so
    // restarts never reissue timestamps the device already stamped.
    single<com.dexstudios.dex.core.sync.SyncStorage> {
        com.dexstudios.dex.core.network.DataStoreSyncStorage(dataStore = get())
    }
    single {
        val clock = com.dexstudios.dex.core.sync.HybridLogicalClock(
            wallClock = { com.dexstudios.dex.core.network.HashUtils.currentTimeMillis() },
        )
        val saved = runCatching {
            kotlinx.coroutines.runBlocking { get<com.dexstudios.dex.core.sync.SyncStorage>().loadClock() }
        }.getOrNull()
        if (saved != null) clock.restore(saved)
        clock
    }
    single {
        com.dexstudios.dex.core.sync.SyncEngine(
            storage = get(),
            clock = get(),
            deviceId = get<com.dexstudios.dex.core.network.DeviceConfig>().fingerprint,
            wallClock = { com.dexstudios.dex.core.network.HashUtils.currentTimeMillis() },
        )
    }
}
