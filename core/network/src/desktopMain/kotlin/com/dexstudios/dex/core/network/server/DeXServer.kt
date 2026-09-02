package com.dexstudios.dex.core.network.server

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.domain.pairing.PairingEngine
import com.dexstudios.dex.core.network.DeXPorts
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.security.CertificateGenerator
import com.dexstudios.dex.core.network.server.routes.clipboardRoutes
import com.dexstudios.dex.core.network.server.routes.controlRoutes
import com.dexstudios.dex.core.network.server.routes.deviceRoutes
import com.dexstudios.dex.core.network.server.routes.fileExplorerRoutes
import com.dexstudios.dex.core.network.server.routes.hostedDownloadRoutes
import com.dexstudios.dex.core.network.server.routes.oauthCallbackRoutes
import com.dexstudios.dex.core.network.server.routes.settingsRoutes
import com.dexstudios.dex.core.network.server.routes.shareRoutes
import com.dexstudios.dex.core.network.server.routes.wallpaperRoutes
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

    /**
     * Loopback-only OAuth listener. The legacy WPF app served the Google sign-in redirect
     * on http://127.0.0.1:48425/local/oauth/callback (Kestrel) and that URI is registered in
     * the Google Cloud Console client — the migration kept the redirect but never bound the
     * port, dead-ending every sign-in. This listener restores the legacy contract.
     */
    private var oauthServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        if (server1 != null) return

        val keyStore = CertificateGenerator.getOrCreateKeyStore()

        // Core protocol surface: device/share/control/websocket/explorer/clipboard routes.
        // Deliberately EXCLUDES settingsRoutes — account mutation belongs to the loopback
        // control plane only (plan 021), and oauthCallbackRoutes to the dedicated 48425
        // loopback listener below.
        val baseModule: Application.() -> Unit = {
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
                pairingEngine.deviceFingerprintProvider = {
                    getKoin().get<com.dexstudios.dex.core.network.DeviceConfig>().fingerprint
                }
                pairingEngine.deviceAliasProvider = {
                    getKoin().get<com.dexstudios.dex.core.network.DeviceConfig>().alias
                }
                // Legacy parity: issuing a desktop-side PIN popped the Windows toast
                // "Enter PIN {pin} on {alias}"; the notification layer owns DND policy.
                val platformEngine = runCatching {
                    getKoin().get<com.dexstudios.dex.core.network.engine.IPlatformEngine>()
                }.getOrNull()
                if (platformEngine != null) {
                    pairingEngine.pinOfferNotifier = { pin, alias ->
                        platformEngine.showPairingPinNotification(pin, alias)
                    }
                }

                deviceRoutes(discoveryEngine = discoveryEngine)
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
                wallpaperRoutes()
            }
        }

        server1 = embeddedServer(Netty, configure = {
            sslConnector(
                keyStore = keyStore,
                keyAlias = "dex",
                keyStorePassword = { CertificateGenerator.getPassword().toCharArray() },
                privateKeyPassword = { CertificateGenerator.getPassword().toCharArray() },
            ) {
                host = "0.0.0.0"
                port = DeXPorts.HTTPS
                // SSL material comes ONLY from the `keyStore` instance above
                // (CertificateGenerator -> ~/.dex/security/dex_cert.jks). Ktor's Netty
                // engine never reads `keyStorePath`, so none is set — a path pointing at
                // %TEMP%\dex_cert.jks used to live here and silently mislead readers.
            }
        }, module = baseModule).start(wait = false)

        // Loopback control plane: core protocol surface PLUS the account settings routes,
        // which mutate identity/trust state and must never face the LAN (plan 021).
        server2 = embeddedServer(Netty, port = DeXPorts.LOOPBACK_CONTROL, host = "127.0.0.1", module = {
            baseModule()
            routing {
                settingsRoutes(deviceConfig = getKoin().get())
            }
        }).start(wait = false)

        // Plain-HTTP fallback listener: exposes ONLY the hosted-file pull endpoints. Serving the
        // full application over plaintext on 0.0.0.0 let any LAN peer hit /ws, /register and
        // /upload without TLS — the pull fallback never needed more than downloads.
        server3 = embeddedServer(Netty, port = DeXPorts.PULL, host = "0.0.0.0", module = {
            routing {
                hostedDownloadRoutes()
            }
        }).start(wait = false)

        oauthServer = embeddedServer(Netty, host = "127.0.0.1", port = DeXPorts.OAUTH_CALLBACK, module = {
            routing {
                oauthCallbackRoutes()
            }
        }).start(wait = false)

        runCatching {
            getKoin().getOrNull<com.dexstudios.dex.core.network.services.DesktopWallpaperWatcherService>()?.start()
        }

        // Clipboard sync use case (plan 029): eager resolve so ClipboardSyncState.useCase
        // is wired BEFORE any set-clipboard frame can arrive; the same instance backs the
        // server receive path and the AWT change listener (one shared echo guard).
        runCatching {
            getKoin().get<com.dexstudios.dex.core.domain.clipboard.ClipboardSyncUseCase>()
        }

        // Sync roster card (plan 031): publish THIS device into the synced devices
        // collection so same-account peers render it. Koin-optional by design — a
        // missing graph means local-only operation, never a startup failure.
        runCatching {
            val deviceConfig = getKoin().get<com.dexstudios.dex.core.network.DeviceConfig>()
            val clock = getKoin().get<com.dexstudios.dex.core.sync.SyncEngine>()
            com.dexstudios.dex.core.network.SyncBridge.attach(clock)
            com.dexstudios.dex.core.network.SyncBridge.ownDeviceCard(
                fingerprint = deviceConfig.fingerprint,
                alias = deviceConfig.alias,
                deviceModel = com.dexstudios.dex.core.network.getPlatformDeviceModel(),
                platform = "desktop",
            )
        }.onFailure {
            co.touchlab.kermit.Logger.i("Sync roster card skipped: ${it.message}")
        }

        // Sync flush loop (plan 031, closing the client loop): drains the engine's queued
        // deltas through the configured sync host. Idle when syncHostUrl is empty (the
        // user has not configured a host) or when signed out — queued deltas simply wait.
        // Started with the server lifecycle; stopped by DesktopShutdownCoordinator.
        runCatching {
            val scheduler = getKoin().get<com.dexstudios.dex.core.network.sync.DesktopSyncScheduler>()
            scheduler.start()
        }.onFailure {
            co.touchlab.kermit.Logger.i("Sync scheduler skipped: ${it.message}")
        }

        Logger.i(
            "DeXServer started on HTTPS port 48424, HTTP 28425 (loopback), HTTP 48426 " +
                "(pull fallback, downloads only), HTTP 48425 (loopback OAuth callback)",
        )
    }

    /**
     * Stops every listener CONCURRENTLY under one deadline. The previous sequential
     * `stop(1000, 2000)` per server blocked Quit for up to ~9s with live connections.
     */
    fun stop(gracePeriodMillis: Long = 500L, timeoutMillis: Long = 1_500L) {
        runCatching {
            getKoin().getOrNull<com.dexstudios.dex.core.network.services.DesktopWallpaperWatcherService>()?.stop()
        }

        val servers = listOfNotNull(server1, server2, server3, oauthServer)
        server1 = null
        server2 = null
        server3 = null
        oauthServer = null
        if (servers.isEmpty()) return

        val deadline = gracePeriodMillis + timeoutMillis
        val threads = servers.map { server ->
            Thread({
                runCatching { server.stop(gracePeriodMillis, timeoutMillis) }
            }, "dex-server-stop").apply { isDaemon = true }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(deadline) }
    }
}
