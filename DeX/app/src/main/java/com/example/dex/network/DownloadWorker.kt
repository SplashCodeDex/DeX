package com.example.dex.network

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import com.example.dex.R
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val client by inject<ClientEngine>()

    private val notificationId = 1002
    private val channelId = "download_channel"

    // PC's HTTPS host port: serves /download over HTTP/1.1 (TCP 53317) and, via Alt-Svc, HTTP/3 (UDP 53316)
    private val httpsPort = 53317


    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ip = inputData.getString("ip") ?: return@withContext Result.failure()
        val tcpPort = inputData.getInt("port", -1)
        val fileId = inputData.getString("fileId") ?: return@withContext Result.failure()
        val fileName = inputData.getString("fileName") ?: "downloaded_file"
        val fileSize = inputData.getLong("fileSize", 100L)
        val destDirUri = inputData.getString("destDirUri") ?: return@withContext Result.failure()

        if (tcpPort == -1) return@withContext Result.failure()

        TcpDownloadService.updateState(DownloadState(fileName = fileName, isDownloading = true))
        setForeground(createForegroundInfo(0, "Preparing download..."))

        val docUri = SafStorage.createDocumentUri(context, destDirUri.toUri(), fileName)
        if (docUri == null) {
            TcpDownloadService.updateState(DownloadState(fileName = fileName, error = "Cannot write to Downloads/DeX", isDownloading = false))
            return@withContext Result.failure()
        }

        val out = context.contentResolver.openOutputStream(docUri)
        if (out == null) {
            TcpDownloadService.updateState(DownloadState(fileName = fileName, error = "Cannot write to Downloads/DeX", isDownloading = false))
            return@withContext Result.failure()
        }

        val outcome = try {
            if (client.quicAvailable()) {
                quicDownload(ip, fileId, fileName, fileSize, out)
            } else {
                tcpDownload(ip, tcpPort, fileId, fileName, fileSize, out)
            }
        } finally {
            out.close()
        }

        if (outcome.ok) {
            TransferHistory.log(applicationContext, fileName, outcome.bytes, "received", docUri.toString())
            TcpDownloadService.updateState(DownloadState(fileName = fileName, progress = 1f, isSuccess = true))

            val successNotification = NotificationCompat.Builder(context, channelId)
                .setContentTitle("File Received")
                .setContentText("Saved to Downloads/DeX/$fileName")
                .setSmallIcon(R.drawable.ic_stat_dex)
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(fileName.hashCode(), successNotification)

            Result.success()
        } else {
            TcpDownloadService.updateState(DownloadState(fileName = fileName, error = outcome.error, isDownloading = false))
            Result.failure()
        }
    }

    private suspend fun quicDownload(
        ip: String,
        fileId: String,
        fileName: String,
        fileSize: Long,
        out: java.io.OutputStream
    ): DownloadOutcome {
        var downloaded = 0L
        var lastUpdateMillis = System.currentTimeMillis()
        val result = client.downloadFileQuic(ip, httpsPort, fileId, out) { bytes ->
            downloaded = bytes
            val now = System.currentTimeMillis()
            // Update UI and Notification every ~200ms so we don't spam the OS
            if (now - lastUpdateMillis > 200) {
                try {
                    val progress = if (fileSize > 0) bytes.toFloat() / fileSize else 1f
                    TcpDownloadService.updateState(
                        DownloadState(fileName = fileName, progress = progress, isDownloading = true)
                    )
                    setForeground(createForegroundInfo((progress * 100).toInt(), "Downloading: $fileName"))
                } catch (e: Exception) {
                    // UI updates must never kill the transfer
                }
                lastUpdateMillis = now
            }
        }
        return if (result.ok) {
            DownloadOutcome(ok = true, bytes = downloaded)
        } else {
            DownloadOutcome(
                ok = false,
                httpStatus = result.httpStatus,
                error = if (result.httpStatus > 0) "Download failed (HTTP ${result.httpStatus})"
                else "Download failed: no connection to PC"
            )
        }
    }

    private suspend fun tcpDownload(
        ip: String,
        port: Int,
        fileId: String,
        fileName: String,
        fileSize: Long,
        out: java.io.OutputStream
    ): DownloadOutcome {
        var downloaded = 0L
        var lastUpdateMillis = System.currentTimeMillis()
        try {
            val socketChannel = SocketChannel.open(InetSocketAddress(ip, port))
            val fileIdBytes = fileId.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.wrap(fileIdBytes)
            while (buffer.hasRemaining()) {
                if (isStopped) {
                    socketChannel.close()
                    return DownloadOutcome(ok = false, error = "Download cancelled")
                }
                socketChannel.write(buffer)
            }

            val ioBuffer = ByteBuffer.allocateDirect(81920)
            while (socketChannel.read(ioBuffer) != -1) {
                if (isStopped) {
                    socketChannel.close()
                    return DownloadOutcome(ok = false, error = "Download cancelled")
                }

                ioBuffer.flip()
                downloaded += ioBuffer.remaining()

                val bytes = ByteArray(ioBuffer.remaining())
                ioBuffer.get(bytes)
                out.write(bytes)
                ioBuffer.clear()

                val now = System.currentTimeMillis()
                // Update UI and Notification every ~200ms so we don't spam the OS
                if (now - lastUpdateMillis > 200) {
                    val progress = if (fileSize > 0) downloaded.toFloat() / fileSize else 0f
                    TcpDownloadService.updateState(
                        DownloadState(
                            fileName = fileName,
                            progress = progress,
                            isDownloading = true
                        )
                    )
                    setForeground(createForegroundInfo((progress * 100).toInt(), "Downloading: $fileName"))
                    lastUpdateMillis = now
                }
            }

            socketChannel.close()
            return DownloadOutcome(ok = true, bytes = downloaded)
        } catch (e: Exception) {
            e.printStackTrace()
            return DownloadOutcome(ok = false, error = e.message)
        }
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
