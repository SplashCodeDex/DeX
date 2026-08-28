package com.dexstudios.dex.overlay

import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.network.ClipboardEvent
import com.dexstudios.dex.core.network.ClipboardSyncState
import com.dexstudios.dex.window.components.truncateMiddle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Bridges the centralized Clipboard Sync State flow into the Overlay System.
 * Automatically dispatches visual Toasts when clipboard data is received from paired devices.
 */
class ClipboardOverlayBridge(private val scope: CoroutineScope, private val overlayManager: OverlayManager) {
    private var observeJob: Job? = null

    fun start() {
        if (observeJob?.isActive == true) return

        observeJob = scope.launch {
            ClipboardSyncState.events.collect { event ->
                when (event) {
                    is ClipboardEvent.Received -> {
                        // Play a subtle pop sound as requested during /grill-me
                        // Extract text preview (e.g., limit to 40 chars)
                        val cleanText = event.text.replace("\n", " ")
                        val preview = cleanText.truncateMiddle(40)

                        overlayManager.showToast(
                            message = "Copied: \"$preview\"",
                            variant = ToastVariant.Success,
                            iconResource = DeXIcons.ClipboardCheckmark,
                            playSound = true,
                            autoDismissTimeoutMs = 3_000L,
                        )
                    }

                    is ClipboardEvent.Sent -> {
                        // Currently ignored based on user preference to avoid spam.
                    }
                }
            }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }
}
