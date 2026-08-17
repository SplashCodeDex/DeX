package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.ClipboardSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class DesktopClipboardService(
    private val clipboardSyncState: ClipboardSyncState,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var job: Job? = null
    private var lastObservedText: String = ""

    fun start() {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                // if (clipboardSyncState.isEnabled.value) { // Wait for proper settings state integration
                    val currentText = getClipboardText()
                    if (currentText != null && currentText != lastObservedText) {
                        lastObservedText = currentText
                        // TODO: Broadcast new clipboard text to paired devices via DeviceManager/QuicClient
                    }
                // }
                delay(2000) // Poll every 2s (AWT doesn't provide a reliable flow for clipboard changes across all OS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun setClipboardText(text: String) {
        if (text == lastObservedText) return
        lastObservedText = text

        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {
            println("Failed to set desktop clipboard: ${e.message}")
        }
    }

    private suspend fun getClipboardText(): String? = withContext(Dispatchers.IO) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
