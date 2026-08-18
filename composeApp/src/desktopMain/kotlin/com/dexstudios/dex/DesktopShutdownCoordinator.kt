package com.dexstudios.dex

import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.WebSocketEngine
import com.dexstudios.dex.core.network.server.DeXServer
import org.koin.core.context.GlobalContext

/**
 * Centralized desktop shutdown sequence.
 *
 * Gracefully stops every service that owns non-daemon threads in dependency order:
 *   1. Discovery services (JmDNS spawns non-daemon timer/socket threads; UDP blocks on receive)
 *   2. WebSocket engine (active session + coroutine scope)
 *   3. Ktor Netty HTTP server (port 48424)
 *
 * Callers MUST follow this with `exitApplication()` and then a hard
 * `kotlin.system.exitProcess(0)` to guarantee no ghost JVM process remains
 * (Compose Desktop's `exitApplication()` is graceful and does not force-kill).
 */
object DesktopShutdownCoordinator {

    fun stopAllServices() {
        val koin = GlobalContext.getOrNull()

        // 1. Discovery services — JmDNS + UDP multicast sockets (non-daemon threads)
        runCatching { koin?.getOrNull<DiscoveryEngine>()?.stopDiscovery() }

        // 2. WebSocket engine — active session and coroutine scope
        runCatching { koin?.getOrNull<WebSocketEngine>()?.stop() }

        // 3. Ktor Netty HTTP server
        runCatching { DeXServer.stop() }

        // 4. JNA Services
        runCatching { com.dexstudios.dex.desktop.jna.WiggleToOpenService.stop() }
        runCatching { com.dexstudios.dex.desktop.jna.ClipboardSyncService.stop() }
    }
}
