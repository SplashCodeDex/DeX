package com.dexstudios.dex.core.network.services

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import kotlinx.coroutines.*
import java.nio.file.*

class DesktopWallpaperWatcherService(private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
    private var watcherJob: Job? = null

    fun start() {
        if (watcherJob != null) return
        val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
        if (!isWindows) return // Windows TranscodedWallpaper watcher

        val appData = System.getenv("APPDATA") ?: return
        val themesPath = Paths.get(appData, "Microsoft", "Windows", "Themes")
        if (!Files.exists(themesPath)) return

        watcherJob = scope.launch {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                themesPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                )

                var debounceJob: Job? = null

                while (isActive) {
                    val key = withContext(Dispatchers.IO) {
                        try {
                            watchService.take()
                        } catch (_: InterruptedException) {
                            null
                        } catch (_: ClosedWatchServiceException) {
                            null
                        }
                    } ?: break

                    for (event in key.pollEvents()) {
                        val filename = event.context()?.toString() ?: ""
                        if (filename.startsWith("TranscodedWallpaper", ignoreCase = true)) {
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                delay(1000) // 1-second debounce for write flush
                                DesktopWallpaperService.invalidateCache()
                                val payload = """{"type":"wallpaper-updated","data":{}}"""
                                WebSocketConnectionManager.broadcastToPaired(payload)
                                Logger.i("DesktopWallpaperWatcher: Broadcasted wallpaper-updated event")
                            }
                        }
                    }
                    if (!key.reset()) break
                }
            } catch (e: Exception) {
                Logger.i("DesktopWallpaperWatcher error: ${e.message}")
            }
        }
    }

    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
    }
}
