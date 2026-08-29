package com.dexstudios.dex.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.TransferSpeedCalculator
import com.dexstudios.dex.core.network.TransferStateMonitor
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.window.components.formatFileSize
import com.dexstudios.dex.window.components.getDeXDownloadDirectory
import com.dexstudios.dex.window.components.isImage
import com.dexstudios.dex.window.components.isVideo
import com.dexstudios.dex.window.components.openFileNative
import com.dexstudios.dex.window.components.openFolderAndSelectNative
import com.dexstudios.dex.window.components.truncateMiddle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bridges active file transfer state streams (Outbound Uploads & Inbound Pulls)
 * into the Unified Overlay Notification System:
 *
 * 1. Active Transfer Phase:
 *    - Real-time Dynamic Island Live Transfer Banner with live progress, speed, and cancel action.
 * 2. Completion Phase:
 *    - Single file -> AirDrop completion card with "Open" (default app) and "Folder" (Explorer highlight).
 *    - Multiple files -> AirDrop completion card with "Open Folder" and "Dismiss".
 * 3. Error / Interruption Phase:
 *    - In-place error banner or failure toast with clear feedback.
 */
class TransferOverlayBridge(
    private val overlayManager: OverlayManager,
    private val clientEngine: ClientEngine,
    private val fileExplorerService: FileExplorerService,
    private val scope: CoroutineScope,
) {
    private var uploadCollectorJob: Job? = null
    private var pullCollectorJob: Job? = null
    private var incomingCollectorJob: Job? = null

    private var activeUploadBannerId: String? = null
    private var activePullBannerId: String? = null
    private var activeIncomingBannerId: String? = null
    private val shownIncomingCompletionSessions = mutableSetOf<String>()

    fun start() {
        Logger.i("TransferOverlayBridge: Starting live transfer stream collectors")
        startUploadCollector()
        startPullCollector()
        startIncomingCollector()
    }

    fun stop() {
        uploadCollectorJob?.cancel()
        pullCollectorJob?.cancel()
        incomingCollectorJob?.cancel()
        uploadCollectorJob = null
        pullCollectorJob = null
        incomingCollectorJob = null
    }

    private fun startUploadCollector() {
        uploadCollectorJob?.cancel()
        uploadCollectorJob = scope.launch {
            clientEngine.uploadState.collectLatest { state ->
                if (state.isUploading) {
                    val fileName = state.fileName.ifBlank { "Files" }.truncateMiddle(32)
                    val speedStr = TransferSpeedCalculator.formatSpeed(state.speedBps)
                    val etaStr = TransferSpeedCalculator.formatEta(state.etaSeconds)
                    val metricsStr = listOfNotNull(
                        speedStr.takeIf { it.isNotBlank() },
                        etaStr.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")

                    val speedText = if (metricsStr.isNotBlank()) {
                        if (state.totalFiles > 1) {
                            "${state.currentFileIndex}/${state.totalFiles} files • $metricsStr"
                        } else {
                            metricsStr
                        }
                    } else {
                        if (state.totalFiles > 1) "${state.currentFileIndex}/${state.totalFiles} files" else null
                    }
                    val badge = state.peerName?.ifBlank { null } ?: "Sending"

                    val currentId = activeUploadBannerId
                    if (currentId == null) {
                        activeUploadBannerId = overlayManager.showBanner(
                            title = fileName,
                            subtitle = speedText,
                            badgeText = badge,
                            iconResource = DeXIcons.ArrowUploadArrow,
                            progress = state.progress,
                            autoDismissTimeoutMs = null,
                            onActionClick = {
                                clientEngine.cancelUpload()
                            },
                        )
                    } else {
                        overlayManager.updateBanner(
                            id = currentId,
                            title = fileName,
                            subtitle = speedText,
                            badgeText = badge,
                            progress = state.progress,
                        )
                    }
                } else {
                    activeUploadBannerId?.let { id ->
                        overlayManager.dismiss(id)
                        activeUploadBannerId = null
                    }

                    if (state.isSuccess) {
                        val fileName = state.fileName.ifBlank { "Files" }.truncateMiddle(30)
                        overlayManager.showToast(
                            message = "Sent '$fileName' successfully",
                            variant = ToastVariant.Success,
                            iconResource = DeXIcons.FileUpload,
                            autoDismissTimeoutMs = 4_000L,
                            playSound = true,
                        )
                    } else {
                        val errorStr = state.error
                        if (errorStr != null) {
                            val isCancelled = errorStr.lowercase().let { it.contains("cancel") || it.contains("interrupt") }
                            if (isCancelled) {
                                val fileName = state.fileName.ifBlank { "Files" }.truncateMiddle(30)
                                overlayManager.showToast(
                                    message = "Transfer of '$fileName' was cancelled",
                                    variant = ToastVariant.Error,
                                    autoDismissTimeoutMs = 5_000L,
                                    playSound = true,
                                )
                            } else {
                                overlayManager.showToast(
                                    message = "Upload failed: $errorStr",
                                    variant = ToastVariant.Error,
                                    autoDismissTimeoutMs = 6_000L,
                                    playSound = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startPullCollector() {
        pullCollectorJob?.cancel()
        pullCollectorJob = scope.launch {
            fileExplorerService.pullProgress.collectLatest { state ->
                if (state.isPulling) {
                    val fileName = state.activeFileName.ifBlank { "File" }.truncateMiddle(32)
                    val speedStr = TransferSpeedCalculator.formatSpeed(state.speedBps)
                    val etaStr = TransferSpeedCalculator.formatEta(state.etaSeconds)
                    val metricsStr = listOfNotNull(
                        speedStr.takeIf { it.isNotBlank() },
                        etaStr.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")

                    val subtitleText = if (metricsStr.isNotBlank()) {
                        if (state.totalFiles > 1) {
                            "${state.completedFiles}/${state.totalFiles} files • $metricsStr"
                        } else {
                            metricsStr
                        }
                    } else if (state.totalBytes > 0) {
                        if (state.totalFiles > 1) {
                            "${state.completedFiles}/${state.totalFiles} files • ${formatFileSize(state.bytesTransferred)} / ${formatFileSize(state.totalBytes)}"
                        } else {
                            "${formatFileSize(state.bytesTransferred)} / ${formatFileSize(state.totalBytes)}"
                        }
                    } else if (state.totalFiles > 1) {
                        "${state.completedFiles}/${state.totalFiles} files"
                    } else {
                        null
                    }

                    val currentId = activePullBannerId
                    if (currentId == null) {
                        activePullBannerId = overlayManager.showBanner(
                            title = fileName,
                            subtitle = subtitleText,
                            badgeText = "Receiving",
                            iconResource = DeXIcons.ArrowDownloadArrow,
                            progress = state.progress,
                            autoDismissTimeoutMs = null,
                            onActionClick = {
                                scope.launch {
                                    fileExplorerService.cancelPull("", state.requestId)
                                }
                            },
                        )
                    } else {
                        overlayManager.updateBanner(
                            id = currentId,
                            title = fileName,
                            subtitle = subtitleText,
                            badgeText = "Receiving",
                            progress = state.progress,
                        )
                    }
                } else {
                    activePullBannerId?.let { id ->
                        overlayManager.dismiss(id)
                        activePullBannerId = null
                    }

                    if (state.isDone && (state.totalFiles > 0 || state.activeFileName.isNotBlank())) {
                        val downloadDir = getDeXDownloadDirectory()
                        val fileName = state.activeFileName.ifBlank { "file" }.truncateMiddle(30)
                        val targetFile = File(downloadDir, state.activeFileName.ifBlank { "file" })

                        if (state.totalFiles <= 1) {
                            val icon = if (targetFile.isVideo()) DeXIcons.VideoCamera else DeXIcons.FileDownload
                            overlayManager.showAlert(
                                title = "Received '$fileName'",
                                message = "Saved to Downloads/DeX",
                                iconResource = icon,
                                previewContent = if (targetFile.isImage()) {
                                    {
                                        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                                        LaunchedEffect(targetFile.absolutePath) {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    if (targetFile.exists()) {
                                                        targetFile.inputStream().buffered().use {
                                                            bitmap = loadImageBitmap(it)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Logger.e("Failed to load image thumbnail for ${targetFile.name}", e)
                                                }
                                            }
                                        }

                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap!!,
                                                contentDescription = "Thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.2f)))
                                        }
                                    }
                                } else {
                                    null
                                },
                                positiveButtonText = "Open",
                                negativeButtonText = "Folder",
                                onPositiveAction = {
                                    if (targetFile.exists()) {
                                        openFileNative(targetFile.absolutePath)
                                    } else {
                                        overlayManager.showToast("File not found or was moved", ToastVariant.Error)
                                    }
                                },
                                onNegativeAction = {
                                    if (targetFile.exists()) {
                                        openFolderAndSelectNative(targetFile.absolutePath)
                                    } else {
                                        overlayManager.showToast("File not found or was moved", ToastVariant.Error)
                                    }
                                },
                                playSound = true,
                            )
                        } else {
                            overlayManager.showAlert(
                                title = "Received ${state.totalFiles} Files",
                                message = "Saved to Downloads/DeX",
                                iconResource = DeXIcons.FileDownload,
                                positiveButtonText = "Open Folder",
                                negativeButtonText = "Dismiss",
                                onPositiveAction = {
                                    if (File(downloadDir).exists()) {
                                        openFolderAndSelectNative(downloadDir)
                                    } else {
                                        overlayManager.showToast("Downloads folder not found", ToastVariant.Error)
                                    }
                                },
                                onNegativeAction = {},
                                playSound = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startIncomingCollector() {
        incomingCollectorJob?.cancel()
        incomingCollectorJob = scope.launch {
            TransferStateMonitor.activeTransfers.collectLatest { transfers ->
                val activeList = transfers.values.toList()
                val runningSession = activeList.firstOrNull { !it.isComplete }
                val completedSessions = activeList.filter { it.isComplete && it.sessionId !in shownIncomingCompletionSessions }

                if (runningSession != null) {
                    val alias = runningSession.senderAlias.ifBlank { "Device" }
                    val progressPercent = if (runningSession.totalBytes > 0 && runningSession.bytesReceived > 0) {
                        (runningSession.bytesReceived.toFloat() / runningSession.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else if (runningSession.totalFiles > 0) {
                        (runningSession.filesReceived.toFloat() / runningSession.totalFiles.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val titleText = if (runningSession.currentFileName.isNotBlank()) {
                        runningSession.currentFileName.truncateMiddle(32)
                    } else {
                        "Receiving from $alias"
                    }

                    val speedStr = TransferSpeedCalculator.formatSpeed(runningSession.speedBps)
                    val etaStr = TransferSpeedCalculator.formatEta(runningSession.etaSeconds)
                    val metricsStr = listOfNotNull(
                        speedStr.takeIf { it.isNotBlank() },
                        etaStr.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")

                    val subtitle = if (metricsStr.isNotBlank()) {
                        if (runningSession.totalFiles > 1) {
                            "${runningSession.filesReceived}/${runningSession.totalFiles} files • $metricsStr"
                        } else {
                            metricsStr
                        }
                    } else if (runningSession.totalFiles > 1) {
                        "${runningSession.filesReceived}/${runningSession.totalFiles} files"
                    } else {
                        null
                    }

                    val currentId = activeIncomingBannerId
                    if (currentId == null) {
                        activeIncomingBannerId = overlayManager.showBanner(
                            title = titleText,
                            subtitle = subtitle,
                            badgeText = "Receiving",
                            iconResource = DeXIcons.ArrowDownloadArrow,
                            progress = progressPercent,
                            autoDismissTimeoutMs = null,
                        )
                    } else {
                        overlayManager.updateBanner(
                            id = currentId,
                            title = titleText,
                            subtitle = subtitle,
                            badgeText = "Receiving",
                            progress = progressPercent,
                        )
                    }
                } else {
                    activeIncomingBannerId?.let { id ->
                        overlayManager.dismiss(id)
                        activeIncomingBannerId = null
                    }
                }

                for (completed in completedSessions) {
                    shownIncomingCompletionSessions.add(completed.sessionId)
                    val downloadDir = getDeXDownloadDirectory()
                    val count = completed.totalFiles
                    val alias = completed.senderAlias.ifBlank { "Device" }

                    overlayManager.showAlert(
                        title = if (count <= 1) "Received File from $alias" else "Received $count Files from $alias",
                        message = "Saved to Downloads/DeX",
                        iconResource = DeXIcons.FileDownload,
                        positiveButtonText = "Open Folder",
                        negativeButtonText = "Dismiss",
                        onPositiveAction = {
                            if (File(downloadDir).exists()) {
                                openFolderAndSelectNative(downloadDir)
                            } else {
                                overlayManager.showToast("Downloads folder not found", ToastVariant.Error)
                            }
                        },
                        onNegativeAction = {},
                        playSound = true,
                    )
                }
            }
        }
    }
}
