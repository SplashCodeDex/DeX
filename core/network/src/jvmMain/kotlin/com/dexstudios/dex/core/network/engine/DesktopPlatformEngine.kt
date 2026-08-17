package com.dexstudios.dex.core.network.engine

import com.dexstudios.dex.core.network.PullFileDto
import kotlinx.serialization.json.JsonObject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class DesktopPlatformEngine : IPlatformEngine {

    override fun showPairingRequestNotification(alias: String) {
        println("[DesktopPlatformEngine] Pairing request from $alias")
        // TODO: Show desktop notification (java.awt.SystemTray or local push)
    }

    override fun cancelPairingNotification() {
        println("[DesktopPlatformEngine] Cancel pairing notification")
    }

    override fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
        println("[DesktopPlatformEngine] Incoming file transfer: $fileCount files (session $sessionId)")
    }

    override fun setClipboardText(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            println("[DesktopPlatformEngine] Clipboard set")
        } catch (e: Exception) {
            println("[DesktopPlatformEngine] Failed to set clipboard: ${e.message}")
        }
    }

    override fun downloadBatch(
        senderIp: String,
        port: Int,
        tcpFallbackPort: Int,
        files: List<PullFileDto>,
        fingerprint: String,
        sourceAlias: String
    ) {
        println("[DesktopPlatformEngine] downloadBatch from $senderIp:$port")
        // Desktop implementation for fetching files will go here
    }

    override fun handleFileExplorerRequest(type: String, data: JsonObject) {
        println("[DesktopPlatformEngine] handleFileExplorerRequest: $type")
        // Desktop implementation for sharing local files with paired device
    }

    override fun handleMirrorStart() {
        println("[DesktopPlatformEngine] handleMirrorStart")
    }

    override fun handleMirrorStop() {
        println("[DesktopPlatformEngine] handleMirrorStop")
    }
}
