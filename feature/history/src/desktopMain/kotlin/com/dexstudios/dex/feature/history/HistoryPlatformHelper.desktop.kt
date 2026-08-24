package com.dexstudios.dex.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class HistoryPlatformHelper {
    actual fun openFolder(uriStr: String) {
        // Desktop implementation (e.g. java.awt.Desktop.getDesktop().open())
    }

    actual fun openFile(uriStr: String, mimeType: String?) {
        // Desktop implementation
    }

    actual fun showToast(message: String) {
        println("Toast: $message")
    }

    actual fun shareFile(uriStr: String) {
        println("Share file: $uriStr")
    }
}

@Composable
actual fun rememberHistoryPlatformHelper(): HistoryPlatformHelper = remember { HistoryPlatformHelper() }
