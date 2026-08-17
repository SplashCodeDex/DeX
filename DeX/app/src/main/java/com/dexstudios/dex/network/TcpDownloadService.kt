package com.dexstudios.dex.network

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class DownloadState(
    val fileName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val doneFiles: Int = 0,
    val totalFiles: Int = 1,
    val protocol: String = "",
    val speedBps: Long = 0L,
    val sourceFingerprint: String? = null,
    val peerName: String? = null,
    val peerPicture: String? = null
)

object TcpDownloadService {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var resetJob: Job? = null

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState = _downloadState.asStateFlow()

    fun resetDownloadState() {
        resetJob?.cancel()
        _downloadState.value = DownloadState()
    }

    var activeWorkId: UUID? = null
    private var lastContext: Context? = null

    fun updateState(state: DownloadState) {
        _downloadState.value = state
        if (state.isSuccess) {
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(6.seconds)
                resetDownloadState()
            }
        }
    }

    fun cancelIfFingerprint(fingerprint: String) {
        if (_downloadState.value.sourceFingerprint == fingerprint && _downloadState.value.isDownloading) {
            lastContext?.let { cancelDownload(it) }
        }
    }

    fun triggerDemo() {
        resetJob?.cancel()
        scope.launch {
            _downloadState.value = DownloadState(
                fileName = "Summer_Vacation_Video.mp4",
                progress = 0f,
                isDownloading = true,
                speedBps = 15728640L, // 15 MB/s
                protocol = "QUIC",
                peerName = "Danny Lopez",
                peerPicture = "https://lh3.googleusercontent.com/a/ACg8ocL_6F3B1u8w8Z3h9Z3h9Z3h9Z3h9Z3h9Z3h=s96-c",
                totalFiles = 23
            )

            // Fast progress simulation
            for (i in 1..100) {
                delay(50)
                _downloadState.update { it.copy(progress = i / 100f) }
            }

            _downloadState.update { it.copy(isDownloading = false, isSuccess = true) }
            delay(6.seconds)
            resetDownloadState()
        }
    }

    /**
     * Enqueues one work item for the whole transfer session. The worker downloads all
     * [files] concurrently (QUIC streams) and reports aggregate progress, so a cancel
     * stops the entire session instead of just the last file.
     *
     * @param httpsPort the PC's advertised HTTPS port (serves /download over HTTP/1.1 and, via Alt-Svc, HTTP/3)
     * @param tcpPort the legacy raw-TCP pull server port used as fallback
     * @param fingerprint the fingerprint of the source device (for UI sorting/tracking)
     * @param sourceAlias the display name of the source device
     */
    fun downloadBatch(
        context: Context,
        ip: String,
        httpsPort: Int,
        tcpPort: Int,
        files: List<PullFileDto>,
        destDirUri: Uri?,
        fingerprint: String? = null,
        sourceAlias: String? = null
    ) {
        lastContext = context.applicationContext
        val totalBytes = files.sumOf { it.size }
        _downloadState.value = DownloadState(
            fileName = if (files.isNotEmpty()) files.first().fileName else "",
            isDownloading = true,
            doneFiles = 0,
            totalFiles = files.size,
            sourceFingerprint = fingerprint
        )

        val inputData = Data.Builder()
            .putString("ip", ip)
            .putInt("httpsPort", httpsPort)
            .putInt("port", tcpPort)
            .putString("files", Json.encodeToString(files))
            .putLong("totalBytes", totalBytes)
            .putString("destDirUri", destDirUri?.toString())
            .putString("sourceFingerprint", fingerprint)
            .putString("sourceAlias", sourceAlias)
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
