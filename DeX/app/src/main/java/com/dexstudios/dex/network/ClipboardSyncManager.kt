package com.dexstudios.dex.network

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** Shared state for 2-way clipboard sync (loop prevention between phone and PC). */
object ClipboardSyncState {
    @Volatile var lastIncoming = ""
    @Volatile var lastPushed = ""
}

/**
 * Watches the phone clipboard and auto-pushes changes to the connected PC over HTTPS
 * (no ADB required). Text that arrived from the PC (or was already pushed by us) is
 * ignored to prevent sync loops.
 */
class ClipboardSyncManager(
    private val context: Context,
    private val webSocketClientService: WebSocketClientService,
    private val clientEngine: ClientEngine,
    private val deviceConfig: DeviceConfig
) {
    private val clipboard: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    fun start() {
        clipboard?.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboard?.removePrimaryClipChangedListener(listener)
    }

    private fun onClipboardChanged() {
        try {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isBlank()) return
            if (text == ClipboardSyncState.lastIncoming || text == ClipboardSyncState.lastPushed) return

            val ip = webSocketClientService.connectedIp ?: return
            val fingerprint = webSocketClientService.connectedFingerprint
            ClipboardSyncState.lastPushed = text
            Timber.i("Clipboard changed, pushing to PC: $ip")
            CoroutineScope(Dispatchers.IO).launch {
                clientEngine.sendClipboard(ip, DeXPorts.HTTPS, text, fingerprint, deviceConfig.identityHash)
            }
        } catch (e: Exception) {
            Timber.e(e, "Clipboard auto-sync failed")
        }
    }
}
