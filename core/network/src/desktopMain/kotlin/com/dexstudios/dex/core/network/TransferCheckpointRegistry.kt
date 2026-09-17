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

    /**
     * Serializes destination-name selection with the rename that claims it. Two uploads may
     * legally race for the same final name (the route's pre-scan cannot reserve it), and a
     * lost race must degrade to "name (1).ext" — never to overwriting a received file.
     */
    private val destinationLock = Any()

    /** Bounded disambiguation attempts before a commit admits it cannot place the file. */
    private const val MAX_DESTINATION_ATTEMPTS = 500

    private fun key(sessionId: String, fileId: String): String = "$sessionId:$fileId"

    /**
     * Creates or retrieves a `.part` staging file for this transfer session item.
     *
     * Staging is keyed per (session, file) AND the generated name carries both ids, because a
     * single batch may carry two files that sanitize to the same basename in the same
     * destination directory (same name sent twice, or the same name from two subfolders).
     * Those two uploads arrive on separate connections; sharing one `.part` file would
     * interleave their bytes and commit a corrupt file for one of them.
     */
    fun getOrCreatePartFile(parentDir: File, sessionId: String, fileId: String, fileName: String, expectedSize: Long): File {
        val k = key(sessionId, fileId)
        val existing = checkpoints[k]
        if (existing != null && existing.partFile.exists()) {
            return existing.partFile
        }

        // Clean sanitize filename and attach session + file unique .part staging suffix
        val safeName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        val partFile = File(parentDir, "$safeName.part.$sessionId.$fileId")
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
     * Promotes the `.part` staging file to a final destination, NEVER deleting another file.
     *
     * Returns the file that actually received the bytes, or null when the commit could not be
     * honoured. Three invariants the caller depends on:
     * - A destination name is chosen under [destinationLock] and then CLAIMED by the rename,
     *   so two concurrent transfers that selected the same name can never resolve to the same
     *   path. On a lost race the name is disambiguated ("name (1).ext"); the previous
     *   implementation deleted whatever already existed, which is how committing one received
     *   file could destroy another.
     * - The copy fallback (cross-volume) writes into a placeholder this method CREATES, so it
     *   can never truncate a file it does not own.
     * - On ANY failure the checkpoint entry and the staging file are KEPT, so the transfer
     *   stays resumable. The old code dropped the entry first, which orphaned the `.part`
     *   file: nothing could resume it and the stale sweeper could no longer see it.
     */
    fun commitPartFile(sessionId: String, fileId: String, destFile: File): File? {
        val k = key(sessionId, fileId)
        val entry = checkpoints[k] ?: return null
        val part = entry.partFile

        if (!part.exists()) {
            Logger.w("TransferCheckpointRegistry: Cannot commit missing part file: ${part.absolutePath}")
            checkpoints.remove(k)
            return null
        }

        synchronized(destinationLock) {
            val free = firstFreeDestination(destFile)
            if (part.renameTo(free)) {
                checkpoints.remove(k)
                return free
            }

            // Rename refused: cross-volume, or a racing writer occupied the name after the scan.
            val reserved = createUniquePlaceholder(destFile)
            if (reserved == null) {
                Logger.e("TransferCheckpointRegistry: Could not place part file at ${destFile.absolutePath}")
                return null
            }
            return try {
                part.inputStream().buffered().use { input ->
                    reserved.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
                part.delete()
                checkpoints.remove(k)
                reserved
            } catch (e: Exception) {
                Logger.e("TransferCheckpointRegistry: Failed to copy part file to dest: ${reserved.absolutePath}", e)
                // Drop only the empty placeholder we created; staging + checkpoint survive so
                // the sender can resume instead of re-uploading from zero.
                runCatching { reserved.delete() }
                null
            }
        }
    }

    /** [destFile] when free, else its first free "name (n)" sibling in the same directory. */
    private fun firstFreeDestination(destFile: File): File {
        if (!destFile.exists()) return destFile
        val parent = destFile.parentFile ?: return destFile
        val baseName = destFile.nameWithoutExtension
        val suffix = destFile.extension.let { if (it.isEmpty()) "" else ".$it" }
        for (counter in 1 until MAX_DESTINATION_ATTEMPTS) {
            val candidate = File(parent, "$baseName ($counter)$suffix")
            if (!candidate.exists()) return candidate
        }
        return destFile
    }

    /**
     * Atomically CREATES a free destination placeholder and returns it (null when none could
     * be created). `createNewFile` is the reservation: it fails instead of truncating when the
     * path is taken, which is exactly the guarantee the copy fallback needs.
     */
    private fun createUniquePlaceholder(destFile: File): File? {
        if (runCatching { destFile.createNewFile() }.getOrDefault(false)) return destFile
        val parent = destFile.parentFile ?: return null
        val baseName = destFile.nameWithoutExtension
        val suffix = destFile.extension.let { if (it.isEmpty()) "" else ".$it" }
        for (counter in 1 until MAX_DESTINATION_ATTEMPTS) {
            val candidate = File(parent, "$baseName ($counter)$suffix")
            if (runCatching { candidate.createNewFile() }.getOrDefault(false)) return candidate
        }
        return null
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
     * Drops every checkpoint entry WITHOUT touching disk.
     *
     * Teardown support for the singleton's process-wide state (the same shape as
     * `ReceivedFileIndex.clear()`): a test that stages files must not leak entries into the
     * next test. Production code uses [discardPartFile] / [pruneStale], which also clean up
     * the files themselves.
     */
    fun clear() = checkpoints.clear()

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
