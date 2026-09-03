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
            var counter = 1
            while (base.exists()) {
                val nameWithoutExt = base.nameWithoutExtension
                val ext = base.extension
                base = if (ext.isNotEmpty()) {
                    File(base.parentFile, "$nameWithoutExt ($counter).$ext")
                } else {
                    File(base.parentFile, "$nameWithoutExt ($counter)")
                }
                counter++
            }
        }
        return base
    }
}
