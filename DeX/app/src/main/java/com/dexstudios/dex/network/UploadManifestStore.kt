package com.dexstudios.dex.network

import android.content.Context
import androidx.work.Data
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** App-private URI manifests survive worker retries and process restarts. No file content is copied. */
class UploadManifestStore(filesDir: File) {
    private val directory = File(filesDir, "upload-manifests")

    fun write(workId: UUID, urisJson: String) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create upload manifest directory" }
        val temporary = File(directory, "$workId.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(urisJson.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            check(temporary.renameTo(file(workId))) { "Cannot publish upload manifest" }
        } finally {
            temporary.delete()
        }
    }

    fun read(workId: UUID): String = file(workId).readText(Charsets.UTF_8)

    fun delete(workId: UUID) {
        val target = file(workId)
        check(!target.exists() || target.delete()) { "Cannot remove upload manifest" }
    }

    // A doWork result is not yet durable: deleting there can break a rescheduled worker.
    // Delete only after WorkManager confirms a terminal state, including cancellation.
    fun pruneFinished(isFinished: (UUID) -> Boolean) {
        directory.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull() ?: return@forEach
            if (isFinished(id)) delete(id)
        }
    }

    private fun file(id: UUID) = File(directory, "$id.json")

    companion object {
        fun readInput(context: Context, input: Data, workId: UUID): String? =
            if (input.getBoolean(TransferWorkKeys.URI_MANIFEST, false)) {
                UploadManifestStore(context.filesDir).read(workId)
            } else {
                input.getString(TransferWorkKeys.URIS) // Already-queued legacy jobs.
            }
    }
}
