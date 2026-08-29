package com.dexstudios.dex.core.network

import co.touchlab.kermit.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry tracking temporary `.part` file checkpoints for resumable HTTP/1.1 and HTTP/3 transfers.
 *
 * Prevents re-downloading/re-uploading already transferred byte chunks when transfers
 * are temporarily interrupted or resumed.
 */
object TransferCheckpointRegistry {
    data class CheckpointEntry(val partFile: File, val targetFileName: String, val expectedSize: Long, val lastUpdatedMs: Long = System.currentTimeMillis())

    private val checkpoints = ConcurrentHashMap<String, CheckpointEntry>()

    private fun key(sessionId: String, fileId: String): String = "$sessionId:$fileId"

    /**
     * Creates or retrieves a `.part` staging file for this transfer session item.
     */
    fun getOrCreatePartFile(parentDir: File, sessionId: String, fileId: String, fileName: String, expectedSize: Long): File {
        val k = key(sessionId, fileId)
        val existing = checkpoints[k]
        if (existing != null && existing.partFile.exists()) {
            return existing.partFile
        }

        // Clean sanitize filename and attach session-unique .part staging suffix
        val safeName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        val partFile = File(parentDir, "$safeName.part.$sessionId")
        checkpoints[k] = CheckpointEntry(
            partFile = partFile,
            targetFileName = safeName,
            expectedSize = expectedSize,
            lastUpdatedMs = System.currentTimeMillis(),
        )
        return partFile
    }

    /**
     * Returns the verified byte length of the existing `.part` staging file, or 0 if none.
     */
    fun getExistingOffset(sessionId: String, fileId: String): Long {
        val entry = checkpoints[key(sessionId, fileId)] ?: return 0L
        if (!entry.partFile.exists()) {
            checkpoints.remove(key(sessionId, fileId))
            return 0L
        }
        val len = entry.partFile.length()
        return if (len <= entry.expectedSize) len else 0L
    }

    /**
     * Atomically moves/renames the `.part` staging file to its final destination upon completion.
     */
    fun commitPartFile(sessionId: String, fileId: String, destFile: File): Boolean {
        val k = key(sessionId, fileId)
        val entry = checkpoints.remove(k) ?: return false
        val part = entry.partFile

        if (!part.exists()) {
            Logger.w("TransferCheckpointRegistry: Cannot commit missing part file: ${part.absolutePath}")
            return false
        }

        if (destFile.exists()) {
            destFile.delete()
        }

        val renamed = part.renameTo(destFile)
        if (!renamed) {
            // Fallback: copy and delete if cross-volume or rename fails
            try {
                part.inputStream().buffered().use { input ->
                    destFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                part.delete()
                return true
            } catch (e: Exception) {
                Logger.e("TransferCheckpointRegistry: Failed to copy part file to dest: ${destFile.absolutePath}", e)
                return false
            }
        }
        return true
    }

    /**
     * Discards and deletes a `.part` staging file.
     */
    fun discardPartFile(sessionId: String, fileId: String) {
        val entry = checkpoints.remove(key(sessionId, fileId)) ?: return
        runCatching {
            if (entry.partFile.exists()) {
                entry.partFile.delete()
            }
        }
    }

    /**
     * Prunes stale `.part` files older than [maxAgeMs] (default: 1 hour).
     */
    fun pruneStale(maxAgeMs: Long = 3_600_000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val toRemove = checkpoints.filter { it.value.lastUpdatedMs < cutoff }
        toRemove.forEach { (k, entry) ->
            checkpoints.remove(k)
            runCatching {
                if (entry.partFile.exists()) {
                    entry.partFile.delete()
                }
            }
        }
    }
}
