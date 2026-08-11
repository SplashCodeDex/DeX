package com.dexstudios.dex.network

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
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
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import com.dexstudios.dex.R

/**
 * Uploads one transfer session (all shared files) to the PC. Files are sent concurrently
 * over HTTP/3 (QUIC) via Cronet — QUIC multiplexes them on one connection — with the
 * HTTP/1.1 path as fallback when the Cronet engine is unavailable.
 *
 * Aggregate progress is computed from a shared byte counter. The work retries with
 * exponential backoff (capped) when the WHOLE session failed at transport level, so a
 * retried session never leaves duplicate files on the PC.
 */
class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val client by inject<ClientEngine>()

    private val notificationId = 1001
    private val channelId = "upload_channel"

    // Cap of concurrent QUIC streams per session
    private val maxConcurrentUploads = 3

    // Transient transport failures are retried with exponential backoff, capped attempts
    private val maxRetryAttempts = 3

    init {
        val channel = android.app.NotificationChannel(
            channelId,
            applicationContext.getString(R.string.upload_worker_channel),
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)
    }

    private val lastUiUpdate = AtomicLong(0L)
    private val speedBytes = AtomicLong(0L)
    private val speedTime = AtomicLong(0L)
    private val smoothedSpeed = AtomicLong(0L)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ip = inputData.getString("ip") ?: return@withContext Result.failure()
        val port = inputData.getInt("port", -1)
        if (port == -1) return@withContext Result.failure()
        val urisJson = inputData.getString("uris") ?: return@withContext Result.failure()

        val deviceConfig by inject<DeviceConfig>()

        val uriStrings = try {
            Json.decodeFromString<List<String>>(urisJson)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
        }
        val uris = uriStrings.map { it.toUri() }

        // Folder bundles: a picked tree is enumerated into (uri, relativePath) pairs
        val folderTreeUri = inputData.getString("folderTreeUri")?.toUri()
        var relativePaths: Map<String, String> = emptyMap()

        // Initial foreground notification
        setForeground(createForegroundInfo(0, applicationContext.getString(R.string.upload_worker_preparing)))

        val fileData: Map<String, Triple<Uri, String, Long>> = if (folderTreeUri != null) {
            val treeFiles = SafStorage.listTreeFiles(applicationContext, folderTreeUri)
            if (treeFiles.isEmpty()) {
                client.updateUploadState(UploadState(fileName = "Folder", error = "The folder is empty", isUploading = false))
                return@withContext Result.failure()
            }
            relativePaths = treeFiles.associate { (_, relPath, _) -> UUID.randomUUID().toString() to relPath }
            treeFiles.mapIndexed { index, (uri, relPath, size) ->
                relativePaths.keys.elementAt(index) to Triple(uri, relPath.substringAfterLast('/'), size)
            }.toMap()
        } else {
            uris.associate { uri ->
                try {
                    applicationContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) { /* Ignored */ }

                var name = "shared_file"
                var size = 0L
                applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                    }
                }
                UUID.randomUUID().toString() to Triple(uri, name, size)
            }
        }

        val totalBatchSize = fileData.values.sumOf { it.third }

        val prepareRequest = PrepareUploadRequestDto(
            info = RegisterDto(
                alias = getDeviceName(applicationContext), version = "2.0", deviceModel = android.os.Build.MODEL ?: "Android",
                deviceType = "mobile", fingerprint = deviceConfig.fingerprint,
                port = DeXPorts.HTTPS, protocol = "https", download = false,
                identityHash = deviceConfig.identityHash
            ),
            files = fileData.mapValues { (id, d) ->
                val partial = HashUtils.computePartialHash(applicationContext, d.first, d.third)
                FileDto(id, d.second, d.third, applicationContext.contentResolver.getType(d.first) ?: "application/octet-stream", partialHash = partial, relativePath = relativePaths[id])
            }
        )

        client.resetUploadState()

        val targetFingerprint = inputData.getString("targetFingerprint")
        val targetIdentityHash = inputData.getString("targetIdentityHash")
        val targetGoogleSub = inputData.getString("targetGoogleSub")
        val token = client.authToken(targetFingerprint, targetIdentityHash, targetGoogleSub)
        val prepared = client.prepareUpload(ip, port, prepareRequest, token)
        val response = prepared.response
        if (response == null) {
            // Transport failure (-1) is retryable; auth/HTTP failures are not
            val retrying = prepared.httpStatus == -1 && !isStopped && runAttemptCount < maxRetryAttempts
            return@withContext if (retrying) Result.retry() else Result.failure()
        }

        val totalSent = AtomicLong(0L)
        val doneCount = AtomicInteger(0)
        val outcomes = CopyOnWriteArrayList<Pair<String, UploadOutcome>>()
        val semaphore = Semaphore(maxConcurrentUploads)

        try {
            coroutineScope {
                fileData.forEach { (id, d) ->
                    launch(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            if (isStopped) return@launch

                            val fileToken = response.files[id] ?: run {
                                outcomes.add(id to UploadOutcome(false, 403))
                                return@launch
                            }
                            if (fileToken == "[SKIP]") {
                                doneCount.incrementAndGet()
                                TransferHistory.log(applicationContext, d.second, d.third, "sent", d.first.toString())
                                outcomes.add(id to UploadOutcome(true))
                                return@launch
                            }

                            val stream = applicationContext.contentResolver.openInputStream(d.first)
                            if (stream == null) {
                                // Not a transport failure: never retry a file we cannot read
                                outcomes.add(id to UploadOutcome(false, 403))
                                return@launch
                            }

                            stream.use { input ->
                                val useQuic = client.quicAvailable()
                                val perFile = AtomicLong(0L)
                                val onBytes: suspend (Long) -> Unit = { bytes ->
                                    val delta = bytes - perFile.getAndSet(bytes)
                                    reportProgress(doneCount.get(), fileData.size, totalSent.addAndGet(delta), totalBatchSize, d.second, useQuic)
                                }

                                val outcome = if (useQuic) {
                                    client.uploadFileQuic(ip, port, response.sessionId, id, d.second, fileToken, input, d.third, onProgress = onBytes)
                                } else {
                                    client.uploadFile(ip, port, response.sessionId, id, d.second, fileToken, input, d.third, onProgress = onBytes)
                                }

                                if (outcome.ok) {
                                    doneCount.incrementAndGet()
                                    TransferHistory.log(applicationContext, d.second, d.third, "sent", d.first.toString())
                                }
                                outcomes.add(id to outcome)
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        }

        val failed = outcomes.filter { !it.second.ok }
        val anyHttpError = failed.any { it.second.httpStatus > 0 }
        val retrying = failed.isNotEmpty() &&
            failed.size == outcomes.size &&
            !anyHttpError &&
            !isStopped &&
            runAttemptCount < maxRetryAttempts

        if (retrying) {
            // A retry re-runs the whole session — don't paint a failure state in between
            return@withContext Result.retry()
        }

        client.finishUpload(doneCount.get(), fileData.size)

        if (failed.isNotEmpty() && failed.all { it.second.httpStatus == 403 }) {
            // Sharper than the generic all-failed message when the cause is unreadable files
            client.updateUploadState(
                client.uploadState.value.copy(error = "Cannot read one or more files", isUploading = false)
            )
        }

        when {
            failed.isEmpty() -> Result.success()
            else -> Result.failure()
        }
    }

    private suspend fun reportProgress(
        doneFiles: Int,
        totalFiles: Int,
        sentBytes: Long,
        totalBytes: Long,
        currentFile: String,
        useQuic: Boolean
    ) {
        val targetFingerprint = inputData.getString("targetFingerprint")
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

        val aggregate = if (totalBytes > 0) sentBytes.toFloat() / totalBytes else 0f
        val displayName = if (totalFiles == 1) currentFile else "$doneFiles of $totalFiles files"
        try {
            client.updateUploadState(
                UploadState(
                    fileName = displayName,
                    currentFileIndex = doneFiles + 1,
                    totalFiles = totalFiles,
                    progress = aggregate,
                    aggregateProgress = aggregate,
                    isUploading = true,
                    protocol = if (useQuic) client.lastUploadProtocol() else "http/1.1",
                    speedBps = smoothedSpeed.get(),
                    targetFingerprint = targetFingerprint
                )
            )
            setForeground(createForegroundInfo((aggregate * 100).toInt(), applicationContext.getString(R.string.upload_worker_progress, doneFiles + 1, totalFiles, currentFile)))
        } catch (e: Exception) {
            // UI updates must never kill the transfer
        }
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Sending Files")
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
