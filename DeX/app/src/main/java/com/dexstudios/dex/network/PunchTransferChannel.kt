package com.dexstudios.dex.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Punch TCP data plane (plan 042 — moved verbatim from PunchSession): manifest
 * announcement, per-file header framing with resume offsets, streaming, and the
 * completion marker. Pure transfer mechanics — no socket acquisition, no
 * lifecycle, no receiver-side prompting.
 */
internal class PunchTransferChannel(
    private val context: Context,
    private val deviceConfig: DeviceConfig,
) {
    private val json = DexJson

    internal enum class TransferOutcome { SUCCESS, REJECTED, DROP }

    internal suspend fun runTransfer(
        socket: Socket,
        sessionId: String,
        uris: List<Uri>,
        files: List<PunchFileDto>,
        totalSize: Long,
        isCancelled: () -> Boolean,
        onProgress: suspend (Float, String) -> Unit,
        targetAlias: String = "Device"
    ): TransferOutcome = withContext(Dispatchers.IO) {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()

        // Announce the transfer (with the resume session id)
        val manifest = PunchManifestDto(
            sessionId = sessionId,
            fingerprint = deviceConfig.fingerprint,
            identityHash = deviceConfig.identityHash,
            alias = getDeviceName(context),
            files = files
        )
        PunchLineProtocol.writeLine(output, json.encodeToString(manifest))

        val reply = withTimeoutOrNull(60_000.milliseconds) { PunchLineProtocol.readLine(input) }
            ?: return@withContext TransferOutcome.DROP
        if (reply.contains("\"reject\"")) return@withContext TransferOutcome.REJECTED

        // The receiver tells us how many bytes it already has per file
        val resumeInfo = try {
            json.decodeFromString<PunchResumeInfoDto>(reply)
        } catch (e: Exception) {
            PunchResumeInfoDto()
        }

        val alreadyDone = files.sumOf { minOf(resumeInfo.files[it.id] ?: 0L, it.size) }
        val grandTotal = totalSize.coerceAtLeast(1)
        var sentNew = 0L

        for ((index, file) in files.withIndex()) {
            if (isCancelled()) return@withContext TransferOutcome.DROP

            val resume = minOf(resumeInfo.files[file.id] ?: 0L, file.size)
            PunchLineProtocol.writeLine(output, json.encodeToString(PunchFileHeaderDto(fileId = file.id, size = file.size, offset = resume)))
            if (resume >= file.size) continue

            val remaining = file.size - resume
            val streamed = if (resume > 0) {
                val afd = context.contentResolver.openAssetFileDescriptor(uris[index], "r") ?: return@withContext TransferOutcome.DROP
                afd.use { descriptor ->
                    val stream = descriptor.createInputStream()
                    stream.use { s ->
                        var skipped = 0L
                        while (skipped < resume) {
                            val n = s.skip(resume - skipped)
                            if (n <= 0) break
                            skipped += n
                        }
                        if (skipped < resume) return@use false
                        streamBytes(s, output, remaining) { bytes ->
                            sentNew += bytes
                            onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                        }
                    }
                }
            } else {
                val stream = context.contentResolver.openInputStream(uris[index]) ?: return@withContext TransferOutcome.DROP
                stream.use { s ->
                    streamBytes(s, output, remaining) { bytes ->
                        sentNew += bytes
                        onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                    }
                }
            }
            if (!streamed) return@withContext TransferOutcome.DROP
            TransferHistory.log(context, file.fileName, file.size, "sent", uris[index].toString(), peerDevice = targetAlias)
        }

        PunchLineProtocol.writeLine(output, json.encodeToString(PunchDoneDto(sessionId = sessionId)))
        TransferOutcome.SUCCESS
    }

    /** Copies [length] bytes from [input] to [output], reporting the delta per chunk. */
    private suspend fun streamBytes(input: InputStream, output: OutputStream, length: Long, onDelta: suspend (Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val buffer = ByteArray(64 * 1024)
        var sent = 0L
        while (sent < length) {
            val toRead = minOf(buffer.size.toLong(), length - sent).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n <= 0) return@withContext false
            output.write(buffer, 0, n)
            sent += n
            onDelta(n.toLong())
        }
        true
    }
}
