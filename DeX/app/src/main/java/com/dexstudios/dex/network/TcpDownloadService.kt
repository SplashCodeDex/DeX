package com.dexstudios.dex.network

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    val etaSeconds: Long? = null,
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
            .putString(TransferWorkKeys.IP, ip)
            .putInt(TransferWorkKeys.HTTPS_PORT, httpsPort)
            .putInt(TransferWorkKeys.PORT, tcpPort)
            .putString(TransferWorkKeys.FILES, Json.encodeToString(files))
            .putLong(TransferWorkKeys.TOTAL_BYTES, totalBytes)
            .putString(TransferWorkKeys.DEST_DIR_URI, destDirUri?.toString())
            .putString(TransferWorkKeys.SOURCE_FINGERPRINT, fingerprint)
            .putString(TransferWorkKeys.SOURCE_ALIAS, sourceAlias)
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

    /**
     * Enqueues an end-to-end encrypted streaming download from the cloud relay (Plan 032 / Option 3).
     */
    fun downloadWanRelay(
        context: Context,
        sessionId: String,
        streamToken: String,
        relayUrl: String,
        pairedToken: String,
        fileName: String,
        totalBytes: Long,
        destDirUri: Uri?,
        fingerprint: String? = null,
        sourceAlias: String? = null
    ) {
        lastContext = context.applicationContext
        _downloadState.value = DownloadState(
            fileName = fileName,
            isDownloading = true,
            doneFiles = 0,
            totalFiles = 1,
            sourceFingerprint = fingerprint,
            peerName = sourceAlias,
            protocol = "relay-e2ee"
        )

        val inputData = Data.Builder()
            .putString(TransferWorkKeys.SESSION_ID, sessionId)
            .putString(TransferWorkKeys.STREAM_TOKEN, streamToken)
            .putString(TransferWorkKeys.RELAY_URL, relayUrl)
            .putString(TransferWorkKeys.PAIRED_TOKEN, pairedToken)
            .putString(TransferWorkKeys.FILE_NAME, fileName)
            .putLong(TransferWorkKeys.TOTAL_BYTES, totalBytes)
            .putString(TransferWorkKeys.DEST_DIR_URI, destDirUri?.toString())
            .putString(TransferWorkKeys.SOURCE_FINGERPRINT, fingerprint)
            .putString(TransferWorkKeys.SOURCE_ALIAS, sourceAlias)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<WanDownloadWorker>()
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
