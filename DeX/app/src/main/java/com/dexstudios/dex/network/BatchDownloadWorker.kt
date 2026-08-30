@file:Suppress("GrazieStyle")

package com.dexstudios.dex.network

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import com.dexstudios.dex.R
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads one transfer session (all files the PC pushed) in a single work item.
 *
 * Files are pulled concurrently over HTTP/3 (QUIC) via Cronet — QUIC multiplexes them
 * on one connection — with the raw TCP pull server (port 48426) as fallback when the
 * Cronet engine is unavailable. Aggregate progress is reported, one notification covers
 * the whole session, and a cancel stops every stream, not just the last file.
 */
class BatchDownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val client by inject<ClientEngine>()
    private val notificationHelper by inject<NotificationHelper>()

    private val notificationId = 1002
    private val channelId = NotificationHelper.CHANNEL_DOWNLOAD
    private val completeNotificationId = 1003

    // Cap of concurrent QUIC streams per session
    private val maxConcurrent = 3

    // Transient transport failures are retried with exponential backoff, capped attempts
    private val maxRetryAttempts = 3

    // Plain-HTTP fallback client for the PC's dedicated pull port (no TLS on 48426)
    private val httpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private data class DownloadResult(
        val ok: Boolean,
        val bytes: Long = 0L,
        val error: String? = null,
        val retryable: Boolean = false
    )

    private data class FileOutcome(
        val fileName: String,
        val docUri: Uri?,
        val ok: Boolean,
        val bytes: Long = 0L,
        val retryable: Boolean = false,
        val error: String? = null
    )

    private val lastUiUpdate = AtomicLong(0L)
    private val speedCalculator = TransferSpeedCalculator()

    // Negotiated protocol of the transfer ("h3", "http", ...) once any file has completed
    @Volatile
    private var transferProtocol: String = ""

    @Volatile
    private var lastForegroundPercent: Int = -1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ip = inputData.getString("ip") ?: return@withContext Result.failure()
        val httpsPort = inputData.getInt("httpsPort", -1)
        val tcpPort = inputData.getInt("port", -1)
        val filesJson = inputData.getString("files") ?: return@withContext Result.failure()
        val totalBytes = inputData.getLong("totalBytes", 0L)
        val destDirUri = inputData.getString("destDirUri")
        val sourceAlias = inputData.getString("sourceAlias")

        val files = try {
            Json.decodeFromString<List<PullFileDto>>(filesJson)
        } catch (e: Exception) {
            Timber.e(e, "BatchDownload: failed to parse files JSON")
            return@withContext Result.failure()
        }
        val resolvedHttpsPort = if (httpsPort != -1) httpsPort else DeXPorts.HTTPS
        val resolvedTcpPort = if (tcpPort != -1) tcpPort else DeXPorts.PULL
        if (files.isEmpty()) return@withContext Result.failure()
        val dirUri = destDirUri?.toUri()

        setForeground(createForegroundInfo(0, "Preparing download..."))

        val createdDocs = CopyOnWriteArrayList<Uri>()
        val outcomes = CopyOnWriteArrayList<FileOutcome>()
        val totalReceived = AtomicLong(0L)
        val doneCount = AtomicInteger(0)
        val semaphore = Semaphore(maxConcurrent)

        try {
            coroutineScope {
                files.forEach { file ->
                    launch(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val outcome = downloadOne(
                                file, ip, resolvedHttpsPort, resolvedTcpPort, dirUri,
                                totalReceived, doneCount, files.size, totalBytes, createdDocs
                            )
                            outcomes.add(outcome)
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // User cancelled the session: remove every partial document
            deleteDocs(createdDocs)
            throw e
        }

        if (outcomes.all { it.ok }) {
            outcomes.forEach { outcome ->
                TransferHistory.log(applicationContext, outcome.fileName, outcome.bytes, "received", outcome.docUri.toString(), peerDevice = sourceAlias)
            }
            TcpDownloadService.updateState(
                DownloadState(
                    fileName = if (files.size == 1) files.first().fileName else "${files.size} files",
                    progress = 1f,
                    isSuccess = true,
                    doneFiles = files.size,
                    totalFiles = files.size
                )
            )
            val single = outcomes.firstOrNull()
            if (outcomes.size == 1 && single?.docUri != null) {
                notificationHelper.showTransferCompleteNotification(single.fileName, single.docUri!!)
            } else {
                showCompletionNotification(files.size)
            }
            Result.success()
        } else {
            val failed = outcomes.filter { !it.ok }
            val anyHttpError = failed.any { !it.retryable }

            val retrying = !anyHttpError && !isStopped && runAttemptCount < maxRetryAttempts
            if (retrying) {
                // A retry must start clean, otherwise SAF's collision handling creates "name (1)" duplicates
                deleteDocs(createdDocs)
                TcpDownloadService.updateState(
                    DownloadState(fileName = "Download retrying...", isDownloading = true, doneFiles = doneCount.get(), totalFiles = files.size)
                )
                Result.retry()
            } else {
                // Keep successful files, remove only partial/failed ones
                deleteDocs(failed.mapNotNull { it.docUri })
                val succeeded = outcomes.filter { it.ok }
                succeeded.forEach { outcome ->
                    TransferHistory.log(applicationContext, outcome.fileName, outcome.bytes, "received", outcome.docUri.toString(), peerDevice = sourceAlias)
                }
                failed.forEach { outcome ->
                    TransferHistory.log(applicationContext, outcome.fileName, outcome.bytes, "received", null, peerDevice = sourceAlias, status = "failed")
                }
                val firstError = failed.firstNotNullOfOrNull { it.error } ?: "Download failed"
                TcpDownloadService.updateState(
                    DownloadState(
                        fileName = failed.first().fileName,
                        error = firstError,
                        isDownloading = false,
                        doneFiles = doneCount.get(),
                        totalFiles = files.size
                    )
                )
                Result.failure()
            }
        }
    }

    private suspend fun downloadOne(
        file: PullFileDto,
        ip: String,
        httpsPort: Int,
        tcpPort: Int,
        dirUri: Uri?,
        totalReceived: AtomicLong,
        doneCount: AtomicInteger,
        totalFiles: Int,
        totalBytes: Long,
        createdDocs: MutableList<Uri>
    ): FileOutcome {
        if (isStopped) return FileOutcome(file.fileName, null, ok = false, error = "Download cancelled")

        // Folder bundles: recreate the relative path structure under Downloads/DeX
        val docUri = if (dirUri != null) {
            if (!file.relativePath.isNullOrBlank()) {
                SafStorage.createDocumentWithPath(context, dirUri, file.relativePath)
            } else {
                SafStorage.createDocumentUri(context, dirUri, file.fileName)
            }
        } else {
            SafStorage.createMediaStoreUri(context, file.fileName, file.relativePath)
        }

        if (docUri == null) {
            return FileOutcome(file.fileName, null, ok = false, error = "Cannot write to Downloads/DeX")
        }
        createdDocs.add(docUri)

        val pfd = context.contentResolver.openFileDescriptor(docUri, "w")
        if (pfd != null) {
            pfd.use { descriptor ->
                val fos = java.io.FileOutputStream(descriptor.fileDescriptor)
                val channel = fos.channel
                val perFileReceived = AtomicLong(0L)
                val onBytes: (Long) -> Unit = { bytes ->
                    val delta = bytes - perFileReceived.getAndSet(bytes)
                    reportProgress(doneCount.get(), totalFiles, totalReceived.addAndGet(delta), totalBytes, file.fileName)
                }

                // Transport alternation: QUIC first when available, then the plain-HTTP pull
                // port. A truncated or failed stream falls through to the next transport.
                val transports: List<suspend (java.nio.channels.WritableByteChannel) -> DownloadResult> = buildList {
                    if (client.quicAvailable()) add { channel2 -> quicDownload(ip, httpsPort, file, channel2, onBytes) }
                    add { channel2 -> httpDownload(ip, tcpPort, file, channel2, onBytes) }
                }

                var result: DownloadResult = DownloadResult(ok = false, error = "no transport available")
                for ((index, transport) in transports.withIndex()) {
                    channel.truncate(0)
                    perFileReceived.set(0L)

                    val attemptResult = transport(channel)
                    result = if (attemptResult.ok && !sizeMatches(perFileReceived.get(), file.size)) {
                        // Truncated stream: never present a partial file as success
                        DownloadResult(ok = false, error = "Incomplete download", retryable = true)
                    } else {
                        attemptResult
                    }

                    if (result.ok || isStopped || !result.retryable) break
                    Timber.w("Download attempt ${index + 1} failed (${result.error}); trying next transport")
                }

                if (result.ok) {
                    doneCount.incrementAndGet()
                    reportProgress(doneCount.get(), totalFiles, totalReceived.get(), totalBytes, file.fileName)
                    return FileOutcome(file.fileName, docUri, ok = true, bytes = perFileReceived.get())
                }
                return FileOutcome(file.fileName, docUri, ok = false, retryable = result.retryable, error = result.error)
            }
        } else {
            deleteDocs(listOf(docUri))
            return FileOutcome(file.fileName, null, ok = false, error = "Cannot write to Downloads/DeX")
        }
    }

    private fun sizeMatches(received: Long, expected: Long): Boolean = expected <= 0L || received == expected

    private suspend fun quicDownload(
        ip: String,
        httpsPort: Int,
        file: PullFileDto,
        out: java.nio.channels.WritableByteChannel,
        onBytes: (Long) -> Unit
    ): DownloadResult {
        val result = client.downloadFileQuic(
            ip, httpsPort, file.fileId, file.token, out,
        ) { bytes ->
            onBytes(bytes)
        }

        if (result.protocol.isNotEmpty()) {
            Timber.i("Download negotiated protocol: ${result.protocol}")
            transferProtocol = result.protocol
        }

        return if (result.ok) {
            DownloadResult(ok = true)
        } else {
            DownloadResult(
                ok = false,
                error = if (result.httpStatus > 0) "Download failed (HTTP ${result.httpStatus})"
                else "Download failed: no connection to PC",
                // -1 = transport failure, retryable; HTTP errors (404/500) never retry
                retryable = result.httpStatus == -1
            )
        }
    }

    /**
     * Plain-HTTP fallback pull against the PC's dedicated download port. This replaces the
     * legacy raw-TCP framing: the desktop's 48426 listener serves real HTTP, and speaking a
     * bare fileId protocol to it used to save the HTTP error page as the "received" file.
     * The response is streamed with OkHttp and length-verified by the caller.
     */
    private suspend fun httpDownload(
        ip: String,
        port: Int,
        file: PullFileDto,
        out: java.nio.channels.WritableByteChannel,
        onBytes: (Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        var downloaded = 0L
        try {
            val tokenPart = file.token?.let { "?token=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""
            val url = "http://$ip:$port/download/${java.net.URLEncoder.encode(file.fileId, "UTF-8")}$tokenPart"

            val request = okhttp3.Request.Builder().url(url).build()
            val call = httpClient.newCall(request)
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult(
                        ok = false,
                        error = "Download failed (HTTP ${response.code})",
                        retryable = false
                    )
                }

                val body = response.body
                val input = body.byteStream()
                val inChannel = java.nio.channels.Channels.newChannel(input)
                val directBuffer = java.nio.ByteBuffer.allocateDirect(65536)

                while (true) {
                    if (isStopped) {
                        return@withContext DownloadResult(ok = false, error = "Download cancelled", retryable = false)
                    }
                    directBuffer.clear()
                    val read = inChannel.read(directBuffer)
                    if (read == -1) break
                    directBuffer.flip()
                    downloaded += read
                    while (directBuffer.hasRemaining()) {
                        out.write(directBuffer)
                    }
                    onBytes(downloaded)
                }
            }

            transferProtocol = "http"
            DownloadResult(ok = true, bytes = downloaded)
        } catch (e: Exception) {
            Timber.w(e, "HTTP fallback download failed for ${file.fileName}")
            // A partial stream is transport-level: another attempt/transport may still succeed
            DownloadResult(ok = false, error = e.message, retryable = true)
        }
    }

    private fun reportProgress(doneFiles: Int, totalFiles: Int, sentBytes: Long, totalBytes: Long, currentFile: String) {
        val sourceFingerprint = inputData.getString("sourceFingerprint")
        val now = System.currentTimeMillis()
        if (sentBytes < totalBytes && now - lastUiUpdate.get() < 200) return
        lastUiUpdate.set(now)

        val sample = speedCalculator.sample(sentBytes, totalBytes, now)
        val progress = when {
            totalBytes > 0 -> (sentBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalFiles > 0 -> (doneFiles.toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }
        val displayName = if (totalFiles == 1) currentFile else "$doneFiles of $totalFiles files"
        try {
            TcpDownloadService.updateState(
                DownloadState(
                    fileName = displayName,
                    progress = progress,
                    isDownloading = true,
                    doneFiles = doneFiles,
                    totalFiles = totalFiles,
                    protocol = transferProtocol,
                    speedBps = sample.speedBps,
                    etaSeconds = sample.etaSeconds,
                    sourceFingerprint = sourceFingerprint
                )
            )
            // Rebuilding a foreground notification on every throttled tick churns the shade;
            // only push an update when the integer percentage actually moves.
            val percent = (progress * 100).toInt()
            if (percent != lastForegroundPercent) {
                lastForegroundPercent = percent
                val speedEtaText = listOfNotNull(
                    sample.formattedSpeed.takeIf { it.isNotBlank() },
                    sample.formattedEta.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

                val notifText = if (speedEtaText.isNotBlank()) "Downloading: $displayName ($speedEtaText)" else "Downloading: $displayName"
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                    setForeground(createForegroundInfo(percent, notifText))
                }
            }
        } catch (e: Exception) {
            // UI updates must never kill the transfer
        }
    }

    private fun deleteDocs(docs: List<Uri>) {
        docs.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) { Timber.w(e, "BatchDownload: could not delete partial file") }
        }
    }

    private fun showCompletionNotification(fileCount: Int) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("File Received")
            .setContentText(if (fileCount == 1) "Saved to Downloads/DeX" else "Saved $fileCount files to Downloads/DeX")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(completeNotificationId, notification)
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Receiving File")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
