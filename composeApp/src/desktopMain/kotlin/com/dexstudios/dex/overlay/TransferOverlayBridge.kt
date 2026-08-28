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

    private var activeUploadBannerId: String? = null
    private var activePullBannerId: String? = null

    fun start() {
        Logger.i("TransferOverlayBridge: Starting live transfer stream collectors")
        startUploadCollector()
        startPullCollector()
    }

    fun stop() {
        uploadCollectorJob?.cancel()
        pullCollectorJob?.cancel()
        uploadCollectorJob = null
        pullCollectorJob = null
    }

    private fun startUploadCollector() {
        uploadCollectorJob?.cancel()
        uploadCollectorJob = scope.launch {
            clientEngine.uploadState.collectLatest { state ->
                if (state.isUploading) {
                    val fileName = state.fileName.ifBlank { "Files" }.truncateMiddle(32)
                    val speedText = if (state.speedBps > 0) {
                        if (state.totalFiles > 1) {
                            "${state.currentFileIndex}/${state.totalFiles} files • ${formatFileSize(state.speedBps)}/s"
                        } else {
                            "${formatFileSize(state.speedBps)}/s"
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
                    val subtitleText = if (state.totalBytes > 0) {
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
}
