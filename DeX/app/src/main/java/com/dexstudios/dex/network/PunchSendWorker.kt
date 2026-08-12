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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Sends files to another same-email device over a NAT-punched direct connection.
 * Progress reuses the upload state flow so the transfer overlay shows it unchanged.
 */
class PunchSendWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val punchSession: PunchSession by inject()
    private val client: ClientEngine by inject()
    private val wsService: WebSocketClientService by inject()
    private val deviceConfig: DeviceConfig by inject()

    private val notificationId = 1004
    private val channelId = "punch_channel"

    init {
        val channel = android.app.NotificationChannel(
            channelId,
            "Direct transfer progress",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetFingerprint = inputData.getString("targetFingerprint") ?: return@withContext Result.failure()
        val urisJson = inputData.getString("uris") ?: return@withContext Result.failure()

        val uris = try {
            Json.decodeFromString<List<String>>(urisJson).map { it.toUri() }
        } catch (e: Exception) {
            return@withContext Result.failure()
        }

        // Folder bundles: enumerate a picked tree into (uri, relativePath) pairs
        val folderTreeUri = inputData.getString("folderTreeUri")?.toUri()
        if (folderTreeUri == null && uris.isEmpty()) return@withContext Result.failure()
        // Enumerate the folder tree once — listTreeFiles walks recursively.
        val treeEntries = folderTreeUri?.let { tree -> SafStorage.listTreeFiles(applicationContext, tree) }
        if (treeEntries != null && treeEntries.isEmpty()) {
            client.updateUploadState(UploadState(fileName = "Folder", error = "The folder is empty", isUploading = false))
            return@withContext Result.failure()
        }
        val relativePaths: List<String>? = treeEntries?.map { it.second }
        val sendUris = treeEntries?.map { it.first } ?: uris

        setForeground(createForegroundInfo(0, "Connecting directly..."))
        client.updateUploadState(UploadState(fileName = "Connecting directly...", isUploading = true, totalFiles = sendUris.size))

        // 1. Try the direct NAT-punched path first
        val error = punchSession.sendTo(
            targetFingerprint = targetFingerprint,
            uris = sendUris,
            relativePaths = relativePaths,
            isCancelled = { isStopped },
            onProgress = { progress, fileName -> reportProgress(progress, fileName, "direct", sendUris.size) }
        )

        // 2. Punch failed (symmetric NAT / CGNAT): stream through the PC instead
        val fallbackError = if (error == null) null else relayViaPc(targetFingerprint, sendUris, relativePaths)

        if (fallbackError == null) {
            client.updateUploadState(UploadState(fileName = if (sendUris.size == 1) "1 file" else "${sendUris.size} files", isSuccess = true))
            Result.success()
        } else {
            Timber.w("Direct transfer failed: $error; relay fallback failed: $fallbackError")
            client.updateUploadState(UploadState(fileName = "Direct transfer", error = fallbackError, isUploading = false))
            Result.failure()
        }
    }

    /** A→PC→B: upload to the PC (same-email identity token), then ask the PC to push to the target. */
    private suspend fun relayViaPc(targetFingerprint: String, uris: List<Uri>, relativePaths: List<String>?): String? = withContext(Dispatchers.IO) {
        val pcIp = wsService.connectedIp ?: return@withContext "The PC is not connected"
        val identityHash = deviceConfig.identityHash
        val googleSub = deviceConfig.googleSub
        if (identityHash.isBlank() && googleSub.isBlank()) return@withContext "Sign in with your email to send over the internet"

        val fileData = uris.map { uri ->
            var name = "shared_file"
            var size = 0L
            applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            Triple(uri, name, size)
        }

        val prepareRequest = PrepareUploadRequestDto(
            info = RegisterDto(
                alias = getDeviceName(applicationContext), version = "2.0",
                deviceModel = android.os.Build.MODEL ?: "Android", deviceType = "mobile",
                fingerprint = deviceConfig.fingerprint, port = DeXPorts.HTTPS, protocol = "https",
                download = false, identityHash = identityHash
            ),
            files = fileData.mapIndexed { index, d ->
                UUID.randomUUID().toString() to FileDto(
                    id = "relay-$index", fileName = d.second, size = d.third,
                    fileType = applicationContext.contentResolver.getType(d.first) ?: "application/octet-stream",
                    relativePath = relativePaths?.getOrNull(index)
                )
            }.toMap()
        )

        val prepared = client.prepareUpload(pcIp, wsService.connectedPort, prepareRequest, token = googleSub.ifBlank { identityHash })
        val response = prepared.response ?: return@withContext "The PC rejected the upload (HTTP ${prepared.httpStatus})"

        var sent = 0L
        val total = fileData.sumOf { it.third }
        for ((index, d) in fileData.withIndex()) {
            if (isStopped) return@withContext "Transfer cancelled"
            val fileId = prepareRequest.files.keys.elementAt(index)
            val token = response.files[fileId] ?: return@withContext "The PC session expired"
            if (token == "[SKIP]") continue

            val stream = applicationContext.contentResolver.openInputStream(d.first)
                ?: return@withContext "Cannot read ${d.second}"
            stream.use { input ->
                val perFile = java.util.concurrent.atomic.AtomicLong(0L)
                val outcome = if (client.quicAvailable()) {
                    client.uploadFileQuic(pcIp, wsService.connectedPort, response.sessionId, fileId, d.second, token, input, d.third) { bytes ->
                        val delta = bytes - perFile.getAndSet(bytes)
                        sent += delta
                        reportProgress(sent.toFloat() / total, d.second, client.lastUploadProtocol().ifEmpty { "quic" }, fileData.size)
                    }
                } else {
                    client.uploadFile(pcIp, wsService.connectedPort, response.sessionId, fileId, d.second, token, input, d.third) { bytes ->
                        val delta = bytes - perFile.getAndSet(bytes)
                        sent += delta
                        reportProgress(sent.toFloat() / total, d.second, "http/1.1", fileData.size)
                    }
                }
                if (!outcome.ok) return@withContext "Upload to PC failed (HTTP ${outcome.httpStatus})"
                TransferHistory.log(applicationContext, d.second, d.third, "sent", d.first.toString())
            }
        }

        // Ask the PC to push the received files to the target device
        val relayDeferred = CompletableDeferred<Boolean>()
        PunchState.pendingRelay.value = relayDeferred
        wsService.sendMessage(
            buildJsonObject {
                put("type", "relay-transfer")
                putJsonObject("data") {
                    put("targetFingerprint", targetFingerprint)
                    put("sessionId", response.sessionId)
                }
            }.toString()
        )
        val pushed = withTimeoutOrNull(10_000) { relayDeferred.await() } == true
        PunchState.pendingRelay.value = null
        if (pushed != true) return@withContext "The target device is offline"
        null
    }

    private suspend fun reportProgress(progress: Float, fileName: String, protocol: String, totalFiles: Int) {
        val targetFingerprint = inputData.getString("targetFingerprint")
        try {
            client.updateUploadState(
                UploadState(
                    fileName = fileName,
                    progress = progress,
                    aggregateProgress = progress,
                    isUploading = true,
                    totalFiles = totalFiles,
                    protocol = protocol,
                    targetFingerprint = targetFingerprint
                )
            )
            setForeground(createForegroundInfo((progress * 100).toInt(), "Sending: $fileName"))
        } catch (e: Exception) {
            // UI updates must never kill the transfer
        }
    }

    private fun createForegroundInfo(progress: Int, text: String): ForegroundInfo {
        val cancelIntent = androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Direct Transfer")
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
