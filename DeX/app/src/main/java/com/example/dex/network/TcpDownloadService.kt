package com.example.dex.network

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DownloadState(
    val fileName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val doneFiles: Int = 0,
    val totalFiles: Int = 1,
    val protocol: String = "",
    val speedBps: Long = 0L
)

object TcpDownloadService {
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState = _downloadState.asStateFlow()

    fun resetDownloadState() {
        _downloadState.value = DownloadState()
    }

    var activeWorkId: UUID? = null

    fun updateState(state: DownloadState) {
        _downloadState.value = state
    }

    /**
     * Enqueues one work item for the whole transfer session. The worker downloads all
     * [files] concurrently (QUIC streams) and reports aggregate progress, so a cancel
     * stops the entire session instead of just the last file.
     *
     * @param httpsPort the PC's advertised HTTPS port (serves /download over HTTP/1.1 and, via Alt-Svc, HTTP/3)
     * @param tcpPort the legacy raw-TCP pull server port used as fallback
     */
    fun downloadBatch(context: Context, ip: String, httpsPort: Int, tcpPort: Int, files: List<PullFileDto>, destDirUri: Uri) {
        val totalBytes = files.sumOf { it.size }
        _downloadState.value = DownloadState(
            fileName = if (files.isNotEmpty()) files.first().fileName else "",
            isDownloading = true,
            doneFiles = 0,
            totalFiles = files.size
        )

        val inputData = Data.Builder()
            .putString("ip", ip)
            .putInt("httpsPort", httpsPort)
            .putInt("port", tcpPort)
            .putString("files", Json.encodeToString(files))
            .putLong("totalBytes", totalBytes)
            .putString("destDirUri", destDirUri.toString())
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BatchDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        activeWorkId = workRequest.id
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelDownload(context: Context) {
        activeWorkId?.let {
            WorkManager.getInstance(context).cancelWorkById(it)
        }
        _downloadState.update { it.copy(isDownloading = false, error = "Cancelled by user") }
    }
}
