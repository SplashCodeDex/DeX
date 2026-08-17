package com.dexstudios.dex.feature.history

import androidx.compose.runtime.Composable

expect class HistoryPlatformHelper {
    fun openFolder(uriStr: String)
    fun openFile(uriStr: String, mimeType: String?)
    fun showToast(message: String)
    fun shareFile(uriStr: String)
}

@Composable
expect fun rememberHistoryPlatformHelper(): HistoryPlatformHelper

