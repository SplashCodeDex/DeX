package com.dexstudios.dex.desktop.jna

import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.FlavorListener

object ClipboardSyncService {
    private var processJob: Job? = null

    @Volatile
    private var started = false

    // The sync brain (echo guard, enable policy, payload shaping) lives in the domain
    // use case (plan 029); this service keeps ONLY the AWT event plumbing.
    private var useCase: com.dexstudios.dex.core.domain.clipboard.ClipboardSyncUseCase? = null

    private val flavorListener = FlavorListener {
        // Debounce rapid bursts (e.g. Excel copying 15 formats at once)
        processJob?.cancel()
        processJob = CoroutineScope(Dispatchers.IO).launch {
            delay(300)
            processClipboard()
        }
    }
    private var observeJob: Job? = null
    private var deviceConfig: com.dexstudios.dex.core.network.DeviceConfig? = null

    fun start(config: com.dexstudios.dex.core.network.DeviceConfig) {
        if (started) return // Already listening — never stack a second FlavorListener
        started = true
        this.deviceConfig = config
        // The process-wide instance is wired by NetworkModule (NetworkModule.jvm) — the
        // SAME one the server receive path consults, so the echo guard is shared. The
        // composeApp sender (WS broadcast + ADB fallback) replaces the WS-only one.
        this.useCase = com.dexstudios.dex.core.network.ClipboardSyncState.useCase

        // The domain echo guard now owns suppression; this observer exists only to keep
        // the legacy event stream (Received/Sent) alive for UI listeners.
        observeJob?.cancel()
        observeJob = CoroutineScope(Dispatchers.IO).launch {
            com.dexstudios.dex.core.network.ClipboardSyncState.events.collect { }
        }

        try {
            Toolkit.getDefaultToolkit().systemClipboard.addFlavorListener(flavorListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        started = false
        processJob?.cancel()
        observeJob?.cancel()
        try {
            Toolkit.getDefaultToolkit().systemClipboard.removeFlavorListener(flavorListener)
        } catch (e: Exception) { }
    }

    private fun processClipboard() {
        val sync = useCase ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // The use case applies the enable gate, reads the clipboard, runs the
                // echo guard, and delivers (WS broadcast + ADB fallback inside the sender).
                val sent = sync.onLocalClipboardChanged()
                if (sent) {
                    co.touchlab.kermit.Logger.i("ClipboardSyncService: pushed clipboard change to peer")
                }
            }.onFailure { e ->
                if (e is java.lang.IllegalStateException) {
                    // Clipboard locked by another process (common on Windows) — the user
                    // hasn't finished copying yet; safe to ignore.
                    co.touchlab.kermit.Logger.i("ClipboardSyncService: Clipboard locked (${e.message})")
                } else {
                    e.printStackTrace()
                }
            }
        }
    }
}
