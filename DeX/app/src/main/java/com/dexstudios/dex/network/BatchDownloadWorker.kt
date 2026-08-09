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
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads one transfer session (all files the PC pushed) in a single work item.
 *
 * Files are pulled concurrently over HTTP/3 (QUIC) via Cronet — QUIC multiplexes them
 * on one connection — with the raw TCP pull server (port 53319) as fallback when the
 * Cronet engine is unavailable. Aggregate progress is reported, one notification covers
 * the whole session, and a cancel stops every stream, not just the last file.
 */
class BatchDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val client by inject<ClientEngine>()

    private val notificationId = 1002
    private val channelId = "download_channel"
    private val completeNotificationId = 1003

    // PC's HTTPS host port: serves /download over HTTP/1.1 (TCP 53317) and, via Alt-Svc, HTTP/3 (UDP 53316)
    private val httpsPort = 53317

    // Cap of concurrent QUIC streams per session
    private val maxConcurrent = 3

    // Transient transport failures are retried with exponential backoff, capped attempts
    private val maxRetryAttempts = 3

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
    private val speedBytes = AtomicLong(0L)
    private val speedTime = AtomicLong(0L)
    private val smoothedSpeed = AtomicLong(0L)

    // Negotiated protocol of the transfer ("h3", "tcp", ...) once any file has completed
    @Volatile
    private var transferProtocol: String = ""

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ip = inputData.getString("ip") ?: return@withContext Result.failure()
        val httpsPort = inputData.getInt("httpsPort", -1)
        val tcpPort = inputData.getInt("port", -1)
        val filesJson = inputData.getString("files") ?: return@withContext Result.failure()
        val totalBytes = inputData.getLong("totalBytes", 0L)
        val destDirUri = inputData.getString("destDirUri") ?: return@withContext Result.failure()

        val files = try {
            Json.decodeFromString<List<PullFileDto>>(filesJson)
        } catch (e: Exception) {
            return@withContext Result.failure()
        }
        if (files.isEmpty() || httpsPort == -1 || tcpPort == -1) return@withContext Result.failure()
        val dirUri = destDirUri.toUri()

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
                                file, ip, httpsPort, tcpPort, dirUri,
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
                TransferHistory.log(applicationContext, outcome.fileName, outcome.bytes, "received", outcome.docUri.toString())
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
            showCompletionNotification(files.size)
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
        dirUri: Uri,
        totalReceived: AtomicLong,
        doneCount: AtomicInteger,
        totalFiles: Int,
        totalBytes: Long,
        createdDocs: MutableList<Uri>
    ): FileOutcome {
        if (isStopped) return FileOutcome(file.fileName, null, ok = false, error = "Download cancelled")

        // Folder bundles: recreate the relative path structure under Downloads/DeX
        val docUri = if (!file.relativePath.isNullOrBlank()) {
            SafStorage.createDocumentWithPath(context, dirUri, file.relativePath)
        } else {
            SafStorage.createDocumentUri(context, dirUri, file.fileName)
        }
        if (docUri == null) {
            return FileOutcome(file.fileName, null, ok = false, error = "Cannot write to Downloads/DeX")
        }
        createdDocs.add(docUri)

        val out = context.contentResolver.openOutputStream(docUri)
        if (out == null) {
            deleteDocs(listOf(docUri))
            return FileOutcome(file.fileName, null, ok = false, error = "Cannot write to Downloads/DeX")
        }

        try {
            val perFileReceived = AtomicLong(0L)
            val onBytes: suspend (Long) -> Unit = { bytes ->
                val delta = bytes - perFileReceived.getAndSet(bytes)
                reportProgress(doneCount.get(), totalFiles, totalReceived.addAndGet(delta), totalBytes, file.fileName)
            }

            val result = if (client.quicAvailable()) {
                quicDownload(ip, httpsPort, file, out, onBytes)
            } else {
                tcpDownload(ip, tcpPort, file, out, onBytes)
            }

            if (result.ok) {
                doneCount.incrementAndGet()
                reportProgress(doneCount.get(), totalFiles, totalReceived.get(), totalBytes, file.fileName)
                return FileOutcome(file.fileName, docUri, ok = true, bytes = perFileReceived.get())
            }
            return FileOutcome(file.fileName, docUri, ok = false, retryable = result.retryable, error = result.error)
        } finally {
            out.close()
        }
    }

    private suspend fun quicDownload(
        ip: String,
        httpsPort: Int,
        file: PullFileDto,
        out: java.io.OutputStream,
        onBytes: suspend (Long) -> Unit
    ): DownloadResult {
        val result = client.downloadFileQuic(ip, httpsPort, file.fileId, file.token, out, onProgress = { bytes ->
            onBytes(bytes)
        })

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

    private suspend fun tcpDownload(
        ip: String,
        port: Int,
        file: PullFileDto,
        out: java.io.OutputStream,
        onBytes: suspend (Long) -> Unit
    ): DownloadResult {
        var downloaded = 0L
        try {
            val socketChannel = SocketChannel.open(InetSocketAddress(ip, port))
            val fileIdBytes = file.fileId.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.wrap(fileIdBytes)
            while (buffer.hasRemaining()) {
                if (isStopped) {
                    socketChannel.close()
                    return DownloadResult(ok = false, error = "Download cancelled")
                }
                socketChannel.write(buffer)
            }

            val ioBuffer = ByteBuffer.allocateDirect(81920)
            while (socketChannel.read(ioBuffer) != -1) {
                if (isStopped) {
                    socketChannel.close()
                    return DownloadResult(ok = false, error = "Download cancelled")
                }

                ioBuffer.flip()
                downloaded += ioBuffer.remaining()

                val bytes = ByteArray(ioBuffer.remaining())
                ioBuffer.get(bytes)
                out.write(bytes)
                ioBuffer.clear()

                onBytes(downloaded)
            }

            socketChannel.close()
            transferProtocol = "tcp"
            return DownloadResult(ok = true, bytes = downloaded)
        } catch (e: Exception) {
            e.printStackTrace()
            return DownloadResult(ok = false, error = e.message, retryable = true)
        }
    }

    private suspend fun reportProgress(doneFiles: Int, totalFiles: Int, sentBytes: Long, totalBytes: Long, currentFile: String) {
        val now = System.currentTimeMillis()
        if (sentBytes < totalBytes && now - lastUiUpdate.get() < 200) return
        lastUiUpdate.set(now)

        val lastBytes = speedBytes.get()
        val lastTime = speedTime.get()
        if (lastTime != 0L && now - lastTime > 200) {
            val instant = ((sentBytes - lastBytes) * 1000L) / (now - lastTime)
            val prev = smoothedSpeed.get()
            smoothedSpeed.set(if (prev == 0L) instant else (prev * 7 + instant * 3) / 10)
        }
        speedBytes.set(sentBytes)
        speedTime.set(now)

        val progress = when {
            totalBytes > 0 -> sentBytes.toFloat() / totalBytes
            totalFiles > 0 -> doneFiles.toFloat() / totalFiles
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
                    speedBps = smoothedSpeed.get()
                )
            )
            setForeground(createForegroundInfo((progress * 100).toInt(), "Downloading: $displayName"))
        } catch (e: Exception) {
            // UI updates must never kill the transfer
        }
    }

    private fun deleteDocs(docs: List<Uri>) {
        docs.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }

    private fun showCompletionNotification(fileCount: Int) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("File Received")
            .setContentText(if (fileCount == 1) "Saved to Downloads/DeX" else "Saved $fileCount files to Downloads/DeX")
            .setSmallIcon(R.drawable.ic_stat_dex)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(completeNotificationId, notification)
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val channel = android.app.NotificationChannel(
            channelId,
            "Download progress",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Receiving File")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_dex)
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
