package com.dexstudios.dex.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Centralized formatting utilities for byte sizes, durations, and speeds across the DeX UI (Plan 024 Phase 2).
 */
object Formatters {

    data class UriMetadata(val fileName: String, val mimeType: String, val sizeBytes: Long)

    /** Resolves provider metadata off the caller's thread; cancellation prevents stale publication. */
    suspend fun resolveMetadata(
        context: Context,
        uri: Uri,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): UriMetadata = withContext(ioDispatcher) {
        UriMetadata(
            fileName = resolveFileName(context, uri),
            mimeType = resolveMimeType(context, uri),
            sizeBytes = resolveFileSize(context, uri)
        )
    }

    /**
     * Formats byte count into a human-readable string with units (B, KB, MB, GB, TB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            bytes >= tb -> String.format(Locale.US, "%.1f TB", bytes / tb)
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    /**
     * Resolves the byte size of a file given its [Uri], querying MediaStore/ContentResolver
     * for content:// schemes or direct File metadata for file:// schemes.
     */
    fun resolveFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index >= 0) {
                            val size = cursor.getLong(index)
                            if (size > 0L) return size
                        }
                    }
                }
            } catch (_: Exception) {}
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val size = pfd.statSize
                    if (size > 0L) return size
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == "file") {
            try {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) return file.length()
                }
            } catch (_: Exception) {}
        }
        return 0L
    }

    /**
     * Formats duration in milliseconds into "m:ss" format.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /**
     * Formats transfer speed in bytes per second.
     */
    fun formatSpeed(speedBps: Long): String {
        if (speedBps <= 0L) return "0 B/s"
        return "${formatBytes(speedBps)}/s"
    }

    /**
     * Resolves the display filename from a [Uri].
     */
    fun resolveFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val name = File(path).name
                if (name.isNotBlank()) return name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    }

    /**
     * Resolves the MIME type from a [Uri], falling back to file extension mapping or generic binary.
     */
    fun resolveMimeType(context: Context, uri: Uri): String {
        val type = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) { null }
        if (!type.isNullOrBlank()) return type

        val ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!ext.isNullOrBlank()) {
            val mapped = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT))
            if (!mapped.isNullOrBlank()) return mapped
        }
        return "*/*"
    }
}
