package com.dexstudios.dex.core.network.server

import java.io.File

/**
 * Single authority for where inbound files land.
 *
 * The location is user-configurable via Settings ("Download Location"); the chosen path is
 * persisted in [com.dexstudios.dex.core.network.DeviceConfig.downloadDir] and mirrored into
 * [overridePath] by the app shell. Null/blank override means the legacy default
 * `~/Downloads/DeX`.
 */
object ReceiveStorage {
    private const val DOWNLOAD_DIR_NAME = "Downloads/DeX"

    /** Absolute custom download directory; written only from DeviceConfig's persisted pref. */
    @Volatile
    var overridePath: String? = null

    fun downloadsDir(): File {
        val custom = overridePath?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
        return (custom ?: File(System.getProperty("user.home"), DOWNLOAD_DIR_NAME)).apply { mkdirs() }
    }

    fun uniqueDest(downloadsFolder: File, fileName: String, relativePath: String? = null): File {
        val safeName = fileName.ifEmpty { "unnamed_file" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var base: File = if (relativePath.isNullOrBlank()) {
            File(downloadsFolder, safeName)
        } else {
            val rel = relativePath.replace("\\", "/").removePrefix("/")
            if (rel.contains("..")) {
                File(downloadsFolder, safeName)
            } else {
                val resolved = downloadsFolder.toPath().resolve(rel).normalize()
                if (resolved.startsWith(downloadsFolder.toPath())) resolved.toFile() else File(downloadsFolder, safeName)
            }
        }

        synchronized(this) {
            return firstFreeDestination(base)
        }
    }

    /**
     * Resolves [destFile] to an available destination path, appending sequential indexes
     * "name (1).ext", "name (2).ext" on collision. Extracts base name and extension once so
     * successive collisions never produce nested parentheses like "name (1) (2).ext".
     */
    fun firstFreeDestination(destFile: File): File {
        if (!destFile.exists()) return destFile
        val parent = destFile.parentFile ?: return destFile
        val baseName = destFile.nameWithoutExtension
        val ext = destFile.extension
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        for (counter in 1 until 1000) {
            val candidate = File(parent, "$baseName ($counter)$suffix")
            if (!candidate.exists()) return candidate
        }
        return destFile
    }

    /**
     * Safely promotes [partFile] to [destFile] without ever overwriting or deleting an existing file.
     * If [destFile] already exists, claims the next free sequential name via [firstFreeDestination].
     * Falls back to copy+delete across filesystem boundaries, cleaning up on any failure.
     */
    fun safeCommit(partFile: File, destFile: File): File? {
        if (!partFile.exists()) return null
        synchronized(this) {
            val free = firstFreeDestination(destFile)
            if (partFile.renameTo(free)) {
                return free
            }
            // Rename failed (e.g. cross-volume or filesystem lock); fall back to copy
            return try {
                partFile.inputStream().buffered().use { input ->
                    free.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                partFile.delete()
                free
            } catch (_: Exception) {
                runCatching { free.delete() }
                null
            }
        }
    }
}
