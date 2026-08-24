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
}
