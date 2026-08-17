package com.dexstudios.dex.feature.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class HistoryPlatformHelper(private val context: Context) {
    actual fun openFolder(uriStr: String) {
        try {
            val fileUri = Uri.parse(uriStr)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open Folder"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open folders", Toast.LENGTH_SHORT).show()
        }
    }

    actual fun openFile(uriStr: String, mimeType: String?) {
        try {
            val uri = Uri.parse(uriStr)
            val resolvedMimeType = mimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open file", Toast.LENGTH_SHORT).show()
        }
    }

    actual fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    actual fun shareFile(uriStr: String) {
        try {
            val uri = android.net.Uri.parse(uriStr)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                setDataAndType(uri, mime)
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share file"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    
}

@Composable
actual fun rememberHistoryPlatformHelper(): HistoryPlatformHelper {
    val context = LocalContext.current
    return remember(context) { HistoryPlatformHelper(context) }
}


