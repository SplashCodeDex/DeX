package com.dexstudios.dex.core.network

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.domain.transfer.TransferUseCase
import com.dexstudios.dex.core.network.server.ReceiveStorage
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * PC-side pull receiver: downloads a batch of hosted files from another device that pushed
 * us a `prepare-upload` prompt over the control channel. This is the desktop counterpart of
 * the phone's [BatchDownloadWorker] and completes the relay/pull transfer model between two
 * PCs across networks.
 *
 * Files stream to `<name>.part` first, then rename atomically; per-file transport fallback
 * goes HTTPS -> plain-HTTP pull port; received byte counts are verified against the manifest
 * so truncated streams never land as complete files.
 */
class DesktopPullService(private val httpClient: HttpClient) {

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val COPY_BUFFER = 256 * 1024
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun downloadBatch(senderIp: String, httpsPort: Int, tcpFallbackPort: Int, files: List<PullFileDto>, fingerprint: String, sourceAlias: String) {
        if (files.isEmpty()) return
        scope.launch { pullSession(senderIp, httpsPort, tcpFallbackPort, files, sourceAlias) }
    }

    private suspend fun pullSession(senderIp: String, httpsPort: Int, tcpFallbackPort: Int, files: List<PullFileDto>, sourceAlias: String) {
        val sessionId = UUID.randomUUID().toString()
        val alias = sourceAlias.ifBlank { "Device" }
        val monitor = TransferStateMonitor

        val totalBytes = files.sumOf { it.size }
        val downloadsFolder = ReceiveStorage.downloadsDir()
        if (downloadsFolder.freeSpace < totalBytes) {
            Logger.i("[DesktopPull] Insufficient disk space for ${files.size} file(s) from $alias")
            return
        }

        monitor.updateIncomingProgress(sessionId, alias, files.size, 0)
        val doneCount = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

        try {
            coroutineScope {
                files.map { file ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val dest = uniqueDest(downloadsFolder, file.fileName, file.relativePath)
                            val ok = pullOne(senderIp, httpsPort, tcpFallbackPort, file, dest)
                            if (ok) {
                                TransferHistoryRecorder.recordCompleted(
                                    name = dest.name,
                                    size = dest.length(),
                                    direction = TransferUseCase.DIRECTION_RECEIVED,
                                    uri = dest.absolutePath,
                                    peerDevice = alias,
                                )
                            } else {
                                runCatching { dest.delete() }
                                TransferHistoryRecorder.recordFailed(
                                    name = file.fileName,
                                    size = file.size,
                                    direction = TransferUseCase.DIRECTION_RECEIVED,
                                    peerDevice = alias,
                                )
                            }
                            val count = doneCount.incrementAndGet()
                            monitor.updateIncomingProgress(sessionId, alias, files.size, count, count == files.size)
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Logger.i("[DesktopPull] Session failed: ${e.message}")
        } finally {
            scope.launch {
                kotlinx.coroutines.delay(6000)
                monitor.removeSession(sessionId)
            }
        }
    }

    /** Streams one file over HTTPS with a plain-HTTP pull-port fallback; verifies length. */
    private suspend fun pullOne(senderIp: String, httpsPort: Int, tcpFallbackPort: Int, file: PullFileDto, dest: File): Boolean {
        val tokenPart = file.token?.let { "?token=$it" } ?: ""
        val attempts = buildList {
            if (httpsPort > 0) add("https://$senderIp:$httpsPort/download/${file.fileId}$tokenPart")
            if (tcpFallbackPort > 0) add("http://$senderIp:$tcpFallbackPort/download/${file.fileId}$tokenPart")
        }

        for ((index, url) in attempts.withIndex()) {
            val lastAttempt = index == attempts.lastIndex
            val ok = runCatching { streamToFile(url, dest, file.size, expectComplete = lastAttempt) }
                .getOrElse { false }
            if (ok) return true
            // A partial write must not be resumed into by the next attempt
            runCatching { dest.delete() }
        }
        return false
    }

    private suspend fun streamToFile(url: String, dest: File, expectedSize: Long, expectComplete: Boolean): Boolean = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, "${dest.name}.part")
        part.outputStream().use { output ->
            val response = httpClient.get(url) {
                // Connect + inactivity guards only — a WHOLE-request timeout would abort
                // legitimate multi-GB streams mid-flight.
                timeout {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = null
                }
            }
            if (!response.status.isSuccess()) return@withContext false
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(COPY_BUFFER)
            var received = 0L
            while (!channel.isClosedForRead) {
                channel.awaitContent()
                val packet = channel.readRemaining(buffer.size.toLong())
                if (packet.exhausted()) break
                while (!packet.exhausted()) {
                    val n = packet.readAtMostTo(buffer, 0, buffer.size)
                    output.write(buffer, 0, n)
                    received += n
                }
            }
            output.flush()

            // Integrity: trust Content-Length when present, otherwise the manifest size.
            val authoritative = declaredLength ?: expectedSize
            if (authoritative > 0 && received != authoritative) return@withContext false
            if (authoritative <= 0 && !expectComplete && received == 0L) return@withContext false
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        true
    }

    private fun uniqueDest(downloadsFolder: File, fileName: String, relativePath: String?): File = ReceiveStorage.uniqueDest(downloadsFolder, fileName, relativePath)
}
