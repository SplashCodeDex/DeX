package com.dexstudios.dex.core.network

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** Shared state for 2-way clipboard sync (loop prevention between phone and PC). */


/**
 * Watches the phone clipboard and auto-pushes changes to the connected PC over HTTPS
 * (no ADB required). Text that arrived from the PC (or was already pushed by us) is
 * ignored to prevent sync loops.
 */
class ClipboardSyncManager(
    private val context: Context,
    private val WebSocketEngine: WebSocketEngine,
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
        if (!deviceConfig.clipboardSyncEnabled) return
        try {
            val clip = clipboard?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isBlank()) return
            if (text == ClipboardSyncState.lastIncoming || text == ClipboardSyncState.lastPushed) return

            val ip = WebSocketEngine.connectedIp ?: return
            val fingerprint = WebSocketEngine.connectedFingerprint
            ClipboardSyncState.lastPushed = text
            Timber.i("Clipboard changed, pushing to PC: $ip")
            CoroutineScope(Dispatchers.IO).launch {
                clientEngine.sendClipboard(ip, WebSocketEngine.connectedPort, text, fingerprint, deviceConfig.identityHash)
            }
        } catch (e: Exception) {
            Timber.e(e, "Clipboard auto-sync failed")
        }
    }
}



