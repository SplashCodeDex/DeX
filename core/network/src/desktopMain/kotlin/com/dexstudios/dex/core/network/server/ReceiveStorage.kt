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

    private val RESERVED_NAMES_REGEX = Regex("^(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")
    private val ILLEGAL_CHARS_REGEX = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /**
     * Sanitizes a single filename or directory name for cross-platform and Windows filesystem safety:
     * - Replaces illegal Windows characters (\\, /, :, *, ?, ", <, >, | and ASCII 0-31) with '_'
     * - Trims leading/trailing whitespace and trailing dots (e.g. 'file. ' or 'file.')
     * - Guards against Windows reserved device names (CON, PRN, AUX, NUL, COM1-9, LPT1-9) by prepending '_'
     * - Falls back to 'unnamed_file' if the result is empty or blank
     */
    fun sanitizeFileName(fileName: String): String {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return "unnamed_file"

        // Replace illegal filesystem characters
        val scrubbed = trimmed.replace(ILLEGAL_CHARS_REGEX, "_")
        val clean = scrubbed.trimEnd(' ', '.')
        if (clean.isEmpty()) return "unnamed_file"

        // Split into base name and extension
        val dotIndex = clean.lastIndexOf('.')
        val (base, ext) = if (dotIndex > 0) {
            val b = clean.substring(0, dotIndex).trimEnd(' ', '.')
            val e = clean.substring(dotIndex + 1).trimEnd(' ', '.')
            Pair(b, e)
        } else {
            Pair(clean, "")
        }

        val safeBase = base.ifEmpty { "unnamed_file" }
        val primaryStem = safeBase.substringBefore('.').trimEnd(' ', '.')
        val isReserved = RESERVED_NAMES_REGEX.matches(safeBase) || RESERVED_NAMES_REGEX.matches(primaryStem)
        val prefixedBase = if (isReserved) "_$safeBase" else safeBase

        return if (ext.isNotEmpty()) "$prefixedBase.$ext" else prefixedBase
    }

    fun uniqueDest(downloadsFolder: File, fileName: String, relativePath: String? = null): File {
        val safeName = sanitizeFileName(fileName)
        var base: File = if (relativePath.isNullOrBlank()) {
            File(downloadsFolder, safeName)
        } else {
            val normalizedRel = relativePath.replace("\\", "/").removePrefix("/").removeSuffix("/")
            if (normalizedRel.contains("..")) {
                File(downloadsFolder, safeName)
            } else {
                val segments = normalizedRel.split("/").filter { it.isNotBlank() }
                if (segments.isEmpty()) {
                    File(downloadsFolder, safeName)
                } else {
                    val safeSegments = segments.map { sanitizeFileName(it) }
                    val relPath = safeSegments.joinToString(File.separator)
                    val resolved = downloadsFolder.toPath().resolve(relPath).normalize()
                    if (resolved.startsWith(downloadsFolder.toPath())) resolved.toFile() else File(downloadsFolder, safeName)
                }
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
        val parent = destFile.parentFile ?: return destFile
        val sanitizedName = sanitizeFileName(destFile.name)
        val target = if (sanitizedName != destFile.name) File(parent, sanitizedName) else destFile
        if (!target.exists()) return target
        val baseName = target.nameWithoutExtension
        val ext = target.extension
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        for (counter in 1 until 1000) {
            val candidate = File(parent, "$baseName ($counter)$suffix")
            if (!candidate.exists()) return candidate
        }
        return target
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
