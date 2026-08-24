package com.dexstudios.dex

import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.WebSocketEngine
import com.dexstudios.dex.core.network.services.DesktopUpnpService
import com.dexstudios.dex.desktop.jna.ClipboardSyncService
import com.dexstudios.dex.desktop.jna.GlobalShortcutService
import com.dexstudios.dex.desktop.jna.WiggleToOpenService
import org.koin.core.context.GlobalContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized desktop shutdown sequence.
 *
 * Gracefully stops every service that owns non-daemon threads in dependency order:
 *   0. Pending DataStore writes (a quit must never drop the latest settings change)
 *   1. JNA input services (wiggle poller, clipboard listener, global keyboard hook)
 *   2. UPnP port mappings (best-effort, parallel, bounded)
 *   3. Discovery services (JmDNS spawns non-daemon timer/socket threads; UDP blocks on receive)
 *   4. WebSocket engine (close frame is sent BEFORE its scope is cancelled)
 *   5. Ktor Netty HTTP servers (all listeners concurrently under one deadline)
 *
 * [stopAllServices] is idempotent: it is safe to invoke from both explicit Quit handlers and
 * the JVM shutdown hook registered in `main()` (which covers crash paths, OS logoff and
 * `taskkill` that bypass those handlers).
 *
 * Callers MUST follow this with `exitApplication()` and then a hard
 * `kotlin.system.exitProcess(0)` to guarantee no ghost JVM process remains
 * (Compose Desktop's `exitApplication()` is graceful and does not force-kill).
 */
object DesktopShutdownCoordinator {

    private val stopped = AtomicBoolean(false)

    fun stopAllServices() {
        if (!stopped.compareAndSet(false, true)) return

        val koin = GlobalContext.getOrNull()

        // 0. Flush pending DataStore writes BEFORE tearing anything down.
        runCatching {
            koin?.getOrNull<DeviceConfig>()?.let { deviceConfig ->
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(2_500L) { deviceConfig.flushPersistedWrites() }
                }
            }
        }

        // 1. JNA input services — cheap synchronous cancels/unhooks.
        runCatching { WiggleToOpenService.stop() }
        runCatching { ClipboardSyncService.stop() }
        runCatching { GlobalShortcutService.stop() }

        // 2. UPnP mapping release runs in parallel with the network teardown below;
        //    joined with a deadline so a slow router can never stall Quit.
        val upnpRelease = Thread({
            runCatching { koin?.getOrNull<DesktopUpnpService>()?.releaseMappedPorts() }
        }, "dex-upnp-release").apply { isDaemon = true }
        upnpRelease.start()

        // 3. Discovery services — JmDNS + UDP multicast sockets (non-daemon threads).
        runCatching { koin?.getOrNull<DiscoveryEngine>()?.stopDiscovery() }

        // 4. WebSocket engine — sends the close frame before scope teardown.
        runCatching { koin?.getOrNull<WebSocketEngine>()?.stop() }

        // 5. Ktor Netty HTTP servers — all listeners concurrently under one deadline.
        runCatching { com.dexstudios.dex.core.network.server.DeXServer.stop() }

        runCatching { upnpRelease.join(2_000L) }
    }
}
