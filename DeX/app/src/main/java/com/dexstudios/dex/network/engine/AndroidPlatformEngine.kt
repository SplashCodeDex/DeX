package com.dexstudios.dex.network.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dexstudios.dex.core.network.PullFileDto
import com.dexstudios.dex.core.network.engine.IPlatformEngine
import com.dexstudios.dex.network.ClipboardSyncState
import com.dexstudios.dex.network.FileShareManager
import com.dexstudios.dex.network.NotificationHelper
import com.dexstudios.dex.network.SafStorage
import com.dexstudios.dex.network.TcpDownloadService
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

class AndroidPlatformEngine(
    private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val fileShareManager: FileShareManager? = null,
) : IPlatformEngine {

    override fun showPairingRequestNotification(alias: String) {
        notificationHelper.showPairingRequestNotification(alias)
    }

    override fun cancelPairingNotification() {
        notificationHelper.cancelPairingNotification()
    }

    override fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
        notificationHelper.showIncomingFileNotification(sessionId, notificationId, fileCount)
    }

    override fun showPairingPinNotification(pin: String, alias: String) {
        // No-op on phone: phone receives the pairing prompt requiring user input
    }

    override fun setClipboardText(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("DeX", text))
                ClipboardSyncState.lastIncoming = text
                Timber.i("Clipboard text synced from PC via AndroidPlatformEngine")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to set clipboard on Android")
        }
    }

    override fun downloadBatch(
        senderIp: String,
        port: Int,
        tcpFallbackPort: Int,
        files: List<PullFileDto>,
        fingerprint: String,
        sourceAlias: String,
    ) {
        val dirUri = SafStorage.getDownloadsDexUri(context)
        val appFiles = files.map {
            com.dexstudios.dex.network.PullFileDto(
                fileId = it.fileId,
                fileName = it.fileName,
                size = it.size,
                token = it.token,
                relativePath = it.relativePath,
            )
        }
        TcpDownloadService.downloadBatch(
            context = context,
            ip = senderIp,
            httpsPort = port,
            tcpPort = tcpFallbackPort,
            files = appFiles,
            destDirUri = dirUri,
            fingerprint = fingerprint,
            sourceAlias = sourceAlias,
        )
    }

    override fun handleFileExplorerRequest(type: String, data: JsonObject) {
        fileShareManager?.handleRequest(type, data)
    }

    override fun handleMirrorStart() {
        Timber.i("Mirror start requested on Android")
    }

    override fun handleMirrorStop() {
        Timber.i("Mirror stop requested on Android")
    }
}
