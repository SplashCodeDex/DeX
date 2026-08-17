package com.dexstudios.dex.core.network.di

import com.dexstudios.dex.core.network.AndroidNsdService
import com.dexstudios.dex.core.network.AndroidUdpService
import com.dexstudios.dex.core.network.IDiscoveryService
import com.dexstudios.dex.core.network.HardwareTelemetry
import com.dexstudios.dex.core.network.AndroidHardwareTelemetry
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.core.network.AndroidMirrorEngine
import com.dexstudios.dex.core.network.engine.IPlatformEngine
import com.dexstudios.dex.core.network.INotificationHelper
import com.dexstudios.dex.core.network.IDownloadStateUpdater
import com.dexstudios.dex.core.network.FileShareManager
import com.dexstudios.dex.core.network.QuicClient
import com.dexstudios.dex.core.network.ClipboardSyncManager
import com.dexstudios.dex.core.network.PunchSession
import com.dexstudios.dex.core.network.IQuicClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import kotlin.time.Duration.Companion.seconds

val androidNetworkModule = module {
    single { FileShareManager(get(), get(), get(), androidContext()) }
    single<IQuicClient> { QuicClient(androidContext()) }
    single { ClipboardSyncManager(androidContext(), get(), get(), get()) }
    single { PunchSession(get(), get(), get(), get(), androidContext()) }

    single<IDiscoveryService> { AndroidNsdService(androidContext()) }
    single<IDiscoveryService> { AndroidUdpService(androidContext()) }
    single<HardwareTelemetry> { AndroidHardwareTelemetry(androidContext()) }
    single<IMirrorEngine> { AndroidMirrorEngine() }
}
