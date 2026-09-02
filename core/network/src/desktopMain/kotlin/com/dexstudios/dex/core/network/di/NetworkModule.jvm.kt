package com.dexstudios.dex.core.network.di

import com.dexstudios.dex.core.network.DesktopJmDnsService
import com.dexstudios.dex.core.network.DesktopUdpService
import com.dexstudios.dex.core.network.HardwareTelemetry
import com.dexstudios.dex.core.network.IDiscoveryService
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.core.network.JvmHardwareTelemetry
import com.dexstudios.dex.core.network.JvmMirrorEngine
import com.dexstudios.dex.core.network.engine.DesktopPlatformEngine
import com.dexstudios.dex.core.network.engine.IPlatformEngine
import com.dexstudios.dex.core.network.services.DesktopUpnpService
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.core.network.services.PublicAddressService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

val desktopNetworkModule = module {
    single<IDiscoveryService> { DesktopJmDnsService() }
    single<IDiscoveryService> { DesktopUdpService() }
    single<IPlatformEngine> { DesktopPlatformEngine(get()) }
    single<HardwareTelemetry> { JvmHardwareTelemetry() }
    single<IMirrorEngine> { JvmMirrorEngine() }
    single { FileExplorerService() }

    // WAN reachability: UPnP port mapping + public-address discovery for same-account phones.
    // Always-on core behavior — no settings surface (plan 023).
    single { DesktopUpnpService(get()) }
    single { PublicAddressService(get(), getOrNull()) }
    single { com.dexstudios.dex.core.network.services.DesktopWallpaperWatcherService() }

    // Clipboard sync (plan 029): the ONE domain use case instance — AWT clipboard access,
    // WS-broadcast sender (ADB fallback stays in the composeApp sender), enable policy
    // read live from DeviceConfig. Wired into ClipboardSyncState so BOTH the server
    // receive path and the AWT change listener consult the same echo guard.
    single {
        com.dexstudios.dex.core.domain.clipboard.ClipboardSyncUseCase(
            access = com.dexstudios.dex.core.network.sync.DesktopClipboardPorts.awtAccess(),
            sender = com.dexstudios.dex.core.network.sync.DesktopClipboardPorts.wsSender(),
            enabled = { get<com.dexstudios.dex.core.network.DeviceConfig>().clipboardSyncEnabled },
            hash = com.dexstudios.dex.core.network.sync.DesktopClipboardPorts::sha256Base64,
        ).also { com.dexstudios.dex.core.network.ClipboardSyncState.useCase = it }
    }

    // Sync client loop (plan 031): transport against the user-configured sync host
    // (DeviceConfig.syncHostUrl; empty = disabled), live ID token from GoogleOAuth
    // (in-memory only), flushed periodically by the scheduler. Started/stopped with the
    // server lifecycle (DeXServer) — never a self-starting hidden network loop.
    single {
        com.dexstudios.dex.core.network.sync.HttpSyncTransport(
            client = get(),
            baseUrlProvider = { get<com.dexstudios.dex.core.network.DeviceConfig>().syncHostUrl },
            tokenProvider = { com.dexstudios.dex.core.network.auth.GoogleOAuth.currentIdToken() ?: "" },
            deviceIdProvider = { get<com.dexstudios.dex.core.network.DeviceConfig>().fingerprint },
        )
    }
    factory {
        com.dexstudios.dex.core.network.sync.DesktopSyncScheduler(
            engine = get(),
            transport = get(),
            syncHostUrlProvider = { get<com.dexstudios.dex.core.network.DeviceConfig>().syncHostUrl },
            tokenProvider = { com.dexstudios.dex.core.network.auth.GoogleOAuth.currentIdToken() },
            scope = get(),
        )
    }
}
