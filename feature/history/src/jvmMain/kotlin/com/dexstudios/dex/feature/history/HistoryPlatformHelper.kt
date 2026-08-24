package com.dexstudios.dex.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class HistoryPlatformHelper {
    actual fun openFolder(uriStr: String) {
        // Desktop implementation: open folder
    }
    actual fun openFile(uriStr: String, mimeType: String?) {
        // Desktop implementation: open file
    }
    actual fun showToast(message: String) {
        // Desktop implementation: print or use desktop notification
        println("Toast: $message")
    }
    actual fun shareFile(uriStr: String) {
        // Desktop implementation: copy to clipboard or open share dialog
    }
}

@Composable
actual fun rememberHistoryPlatformHelper(): HistoryPlatformHelper = remember { HistoryPlatformHelper() }
