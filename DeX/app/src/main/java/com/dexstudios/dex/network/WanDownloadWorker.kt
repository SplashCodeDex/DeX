package com.dexstudios.dex.network

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dexstudios.dex.R
import com.dexstudios.dex.network.crypto.RelayCrypto
import com.dexstudios.dex.network.crypto.RelayCryptoException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Android streaming receiver for WAN cloud relay transfers (Plan 032 / Option 3).
 *
 * THE STREAMING LAW: Content is decrypted frame-by-frame directly to disk.
 * Memory consumption is strictly bounded at ~256 KiB chunks (never holds full files in RAM).
 * Tampered, replayed, or truncated frames fail closed via [RelayCrypto].
 */
class WanDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val notificationHelper by inject<NotificationHelper>()

    private val notificationId = 1004
    private val channelId = NotificationHelper.CHANNEL_DOWNLOAD

    companion object {
        private const val CHUNK_SIZE = 256 * 1024
        private const val LENGTH_PREFIX_BYTES = 4
        private const val MAX_FRAME_BYTES = CHUNK_SIZE + RelayCrypto.NONCE_LENGTH_BYTES + 64

        private const val CONNECT_TIMEOUT_SEC = 15L
        private const val READ_INACTIVITY_SEC = 60L
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_INACTIVITY_SEC, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val speedCalculator = TransferSpeedCalculator()
    private val lastUiUpdate = AtomicLong(0L)
    @Volatile private var lastForegroundPercent = -1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(TransferWorkKeys.SESSION_ID) ?: return@withContext Result.failure()
        val streamToken = inputData.getString(TransferWorkKeys.STREAM_TOKEN) ?: return@withContext Result.failure()
        val relayUrl = inputData.getString(TransferWorkKeys.RELAY_URL) ?: return@withContext Result.failure()
        val pairedToken = inputData.getString(TransferWorkKeys.PAIRED_TOKEN) ?: return@withContext Result.failure()
        val fileName = inputData.getString(TransferWorkKeys.FILE_NAME) ?: "shared_file"
        val totalBytes = inputData.getLong(TransferWorkKeys.TOTAL_BYTES, 0L)
        val sourceFingerprint = inputData.getString(TransferWorkKeys.SOURCE_FINGERPRINT)
        val sourceAlias = inputData.getString(TransferWorkKeys.SOURCE_ALIAS) ?: "Remote Device"
        val destDirUri = inputData.getString(TransferWorkKeys.DEST_DIR_URI)?.toUri()

        Timber.i("WanDownloadWorker: Starting WAN pull for $fileName ($totalBytes bytes) via session $sessionId")

        setForeground(
            TransferProgressNotifier.createForegroundInfo(
                context = applicationContext,
                workId = id,
                title = "Receiving File (WAN)",
                text = "Connecting to relay...",
                progress = 0,
                notificationId = notificationId,
                channelId = channelId
            )
        )

        // Create target document
        val targetDocUri: Uri? = if (destDirUri != null) {
            SafStorage.createDocumentUri(context, destDirUri, fileName)
        } else {
            SafStorage.createMediaStoreUri(context, fileName)
        }

        if (targetDocUri == null) {
            Timber.e("WanDownloadWorker: Failed to create target document for $fileName")
            TcpDownloadService.updateState(
                DownloadState(fileName = fileName, error = "Cannot create target file in Downloads/DeX", isDownloading = false)
            )
            return@withContext Result.failure()
        }

        var received = 0L
        var expectedSeq = 0L

        try {
            val key = RelayCrypto.deriveSessionKey(pairedToken, sessionId)
            val base = relayUrl.trim().trimEnd('/')
            val streamEndpoint = "$base/relay/v1/session/$sessionId/data?streamToken=$streamToken"

            val request = Request.Builder()
                .url(streamEndpoint)
                .header("X-DeX-Stream-Token", streamToken)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Relay HTTP error ${response.code}"
                    Timber.e("WanDownloadWorker: $err")
                    deleteDoc(targetDocUri)
                    TcpDownloadService.updateState(
                        DownloadState(fileName = fileName, error = err, isDownloading = false)
                    )
                    return@withContext Result.failure()
                }

                val body = response.body
                val stream = body.byteStream()

                context.contentResolver.openOutputStream(targetDocUri)?.use { output ->
                    val prefix = ByteArray(LENGTH_PREFIX_BYTES)

                    while (!isStopped) {
                        val hasPrefix = readExactOrNull(stream, prefix)
                        if (!hasPrefix) {
                            // Clean EOF: stream successfully completed
                            break
                        }

                        val frameLength = decodeFrameLength(prefix)
                        if (frameLength <= 0 || frameLength > MAX_FRAME_BYTES) {
                            throw RelayCryptoException("Hostile frame length: $frameLength")
                        }

                        val frame = ByteArray(frameLength)
                        val frameRead = readExactOrNull(stream, frame)
                        if (!frameRead) {
                            throw RelayCryptoException("Relay stream ended mid-frame")
                        }

                        val plaintext = RelayCrypto.openFrame(key, expectedSeq++, frame)
                        output.write(plaintext)
                        received += plaintext.size

                        reportProgress(received, totalBytes, fileName, sourceAlias, sourceFingerprint)
                    }
                } ?: throw IllegalStateException("Cannot open output stream for $targetDocUri")
            }

            if (isStopped) {
                deleteDoc(targetDocUri)
                return@withContext Result.failure()
            }

            // Transfer succeeded: log history and present completion notification
            TransferHistory.log(
                context = applicationContext,
                name = fileName,
                size = received,
                direction = "received",
                uri = targetDocUri.toString(),
                peerDevice = sourceAlias
            )

            TcpDownloadService.updateState(
                DownloadState(
                    fileName = fileName,
                    progress = 1f,
                    isSuccess = true,
                    doneFiles = 1,
                    totalFiles = 1,
                    protocol = "relay-e2ee",
                    sourceFingerprint = sourceFingerprint,
                    peerName = sourceAlias
                )
            )

            notificationHelper.showTransferCompleteNotification(fileName, targetDocUri)
            Timber.i("WanDownloadWorker: Successfully downloaded $fileName ($received bytes)")
            Result.success()

        } catch (e: CancellationException) {
            Timber.i("WanDownloadWorker: Transfer cancelled by user")
            deleteDoc(targetDocUri)
            TcpDownloadService.updateState(
                DownloadState(fileName = fileName, error = "Transfer cancelled", isDownloading = false)
            )
            throw e
        } catch (e: Exception) {
            Timber.e(e, "WanDownloadWorker: Transfer failed")
            deleteDoc(targetDocUri)
            TransferHistory.log(
                context = applicationContext,
                name = fileName,
                size = received,
                direction = "received",
                uri = null,
                peerDevice = sourceAlias,
                status = "failed"
            )
            TcpDownloadService.updateState(
                DownloadState(fileName = fileName, error = e.message ?: "Transfer failed", isDownloading = false)
            )
            Result.failure()
        }
    }

    private suspend fun reportProgress(
        received: Long,
        totalBytes: Long,
        fileName: String,
        sourceAlias: String,
        sourceFingerprint: String?
    ) {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate.get() > 250) {
            lastUiUpdate.set(now)
            val progress = if (totalBytes > 0) (received.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
            val sample = speedCalculator.sample(received, totalBytes, now)

            TcpDownloadService.updateState(
                DownloadState(
                    fileName = fileName,
                    progress = progress,
                    isDownloading = true,
                    doneFiles = 0,
                    totalFiles = 1,
                    protocol = "relay-e2ee",
                    speedBps = sample.speedBps,
                    etaSeconds = sample.etaSeconds,
                    sourceFingerprint = sourceFingerprint,
                    peerName = sourceAlias
                )
            )

            val percent = (progress * 100).toInt()
            if (percent != lastForegroundPercent) {
                lastForegroundPercent = percent
                val speedText = if (sample.formattedSpeed.isNotBlank()) " • ${sample.formattedSpeed}" else ""
                val notifText = "Receiving $fileName ($percent%)$speedText"
                setForeground(
                    TransferProgressNotifier.createForegroundInfo(
                        context = applicationContext,
                        workId = id,
                        title = "Receiving File (WAN)",
                        text = notifText,
                        progress = percent,
                        notificationId = notificationId,
                        channelId = channelId
                    )
                )
            }
        }
    }

    private fun readExactOrNull(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                if (offset == 0) return false
                throw RelayCryptoException("stream ended unexpectedly ($offset/${buffer.size} bytes read)")
            }
            offset += count
        }
        return true
    }

    private fun decodeFrameLength(prefix: ByteArray): Int =
        ((prefix[0].toInt() and 0xFF) shl 24) or
        ((prefix[1].toInt() and 0xFF) shl 16) or
        ((prefix[2].toInt() and 0xFF) shl 8) or
        (prefix[3].toInt() and 0xFF)

    private fun deleteDoc(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Timber.w(e, "WanDownloadWorker: Could not delete partial document $uri")
        }
    }
}
