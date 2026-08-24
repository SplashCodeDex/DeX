package com.dexstudios.dex.core.network.engine

import com.dexstudios.dex.core.network.DesktopPullService
import com.dexstudios.dex.core.network.PullFileDto
import kotlinx.serialization.json.JsonObject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class DesktopPlatformEngine : IPlatformEngine {

    private fun showSystemNotification(title: String, message: String, type: java.awt.TrayIcon.MessageType) {
        if (!java.awt.SystemTray.isSupported()) return
        val tray = java.awt.SystemTray.getSystemTray()
        val trayIcons = tray.trayIcons
        if (trayIcons.isNotEmpty()) {
            trayIcons.first().displayMessage(title, message, type)
        } else {
            println("[$title] $message")
        }
    }

    override fun showPairingRequestNotification(alias: String) {
        println("[DesktopPlatformEngine] Pairing request from $alias")
        showSystemNotification("Pairing Request", "$alias wants to connect.", java.awt.TrayIcon.MessageType.INFO)
    }

    override fun cancelPairingNotification() {
        println("[DesktopPlatformEngine] Cancel pairing notification")
    }

    override fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
        println("[DesktopPlatformEngine] Incoming file transfer: $fileCount files (session $sessionId)")
        showSystemNotification("Incoming Files", "Receiving $fileCount files.", java.awt.TrayIcon.MessageType.INFO)
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

    override fun downloadBatch(senderIp: String, port: Int, tcpFallbackPort: Int, files: List<PullFileDto>, fingerprint: String, sourceAlias: String) {
        // Pull receiver for PC-to-PC transfers that arrive via the control channel
        // (cross-network relay pushes). Streams from the sender's hosted endpoints.
        val httpClient = runCatching {
            org.koin.core.context.GlobalContext.get().get<io.ktor.client.HttpClient>()
        }.getOrNull()
        if (httpClient == null) {
            println("[DesktopPlatformEngine] Cannot receive pulled files: HttpClient unavailable")
            return
        }
        DesktopPullService(httpClient).downloadBatch(senderIp, port, tcpFallbackPort, files, fingerprint, sourceAlias)
    }

    override fun handleFileExplorerRequest(type: String, data: JsonObject) {
        // Intentionally unsupported: this desktop is the EXPLORER CLIENT (it browses phones);
        // exposing local folders back to phone-initiated browse requests is not a product flow.
        println("[DesktopPlatformEngine] Ignoring explorer request '$type' (desktop does not expose folders)")
    }

    override fun handleMirrorStart() {
        println("[DesktopPlatformEngine] handleMirrorStart")
    }

    override fun handleMirrorStop() {
        println("[DesktopPlatformEngine] handleMirrorStop")
    }
}
