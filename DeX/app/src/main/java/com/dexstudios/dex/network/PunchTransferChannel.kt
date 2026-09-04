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
        val identitySecret = deviceConfig.googleSub.ifBlank { deviceConfig.identityHash }
        val channel = try {
            PunchCryptoChannel.performSenderHandshake(socket, sessionId, identitySecret)
        } catch (e: Exception) {
            Timber.e(e, "Punch E2EE handshake failed")
            if (e.message?.contains("rejected", ignoreCase = true) == true) {
                return@withContext TransferOutcome.REJECTED
            }
            return@withContext TransferOutcome.DROP
        }

        // Announce the transfer inside an encrypted frame
        val manifest = PunchManifestDto(
            sessionId = sessionId,
            fingerprint = deviceConfig.fingerprint,
            identityHash = deviceConfig.identityHash,
            alias = getDeviceName(context),
            files = files
        )
        try {
            channel.writeJson(manifest)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send encrypted punch manifest")
            return@withContext TransferOutcome.DROP
        }

        // The receiver tells us how many bytes it already has per file (encrypted frame)
        val resumeInfo = try {
            withTimeoutOrNull(60_000.milliseconds) { channel.readJson<PunchResumeInfoDto>() }
                ?: return@withContext TransferOutcome.DROP
        } catch (e: Exception) {
            Timber.e(e, "Failed to read encrypted punch resume info")
            return@withContext TransferOutcome.DROP
        }

        val alreadyDone = files.sumOf { minOf(resumeInfo.files[it.id] ?: 0L, it.size) }
        val grandTotal = totalSize.coerceAtLeast(1)
        var sentNew = 0L

        for ((index, file) in files.withIndex()) {
            if (isCancelled()) return@withContext TransferOutcome.DROP

            val resume = minOf(resumeInfo.files[file.id] ?: 0L, file.size)
            try {
                channel.writeJson(PunchFileHeaderDto(fileId = file.id, size = file.size, offset = resume))
            } catch (e: Exception) {
                Timber.e(e, "Failed to send encrypted file header for ${file.fileName}")
                return@withContext TransferOutcome.DROP
            }
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
                        channel.streamFile(s, remaining, isCancelled) { bytes ->
                            sentNew += bytes
                            onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                        }
                    }
                }
            } else {
                val stream = context.contentResolver.openInputStream(uris[index]) ?: return@withContext TransferOutcome.DROP
                stream.use { s ->
                    channel.streamFile(s, remaining, isCancelled) { bytes ->
                        sentNew += bytes
                        onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                    }
                }
            }
            if (!streamed) return@withContext TransferOutcome.DROP
            TransferHistory.log(context, file.fileName, file.size, "sent", uris[index].toString(), peerDevice = targetAlias)
        }

        try {
            channel.writeJson(PunchDoneDto(sessionId = sessionId))
        } catch (e: Exception) {
            Timber.w(e, "Failed to send encrypted punch done marker")
        }
        TransferOutcome.SUCCESS
    }
}
