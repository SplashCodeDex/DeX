package com.dexstudios.dex.core.network.engine

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DesktopPullService
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.PullFileDto
import kotlinx.serialization.json.JsonObject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class DesktopPlatformEngine(private val deviceConfig: DeviceConfig? = null) : IPlatformEngine {

    private fun showSystemNotification(title: String, message: String, type: java.awt.TrayIcon.MessageType) {
        if (deviceConfig?.dndEnabled == true) {
            // Do Not Disturb mutes the alerting layer only — transfers/pairing proceed.
            Logger.i("[DND] Suppressed notification: $title — $message")
            return
        }
        if (!java.awt.SystemTray.isSupported()) return
        val tray = java.awt.SystemTray.getSystemTray()
        val trayIcons = tray.trayIcons
        if (trayIcons.isNotEmpty()) {
            trayIcons.first().displayMessage(title, message, type)
        } else {
            Logger.i("[$title] $message")
        }
    }

    override fun showPairingRequestNotification(alias: String) {
        Logger.i("[DesktopPlatformEngine] Pairing request from $alias")
        showSystemNotification("Pairing Request", "$alias wants to connect.", java.awt.TrayIcon.MessageType.INFO)
    }

    override fun showPairingPinNotification(pin: String, alias: String) {
        Logger.i("[DesktopPlatformEngine] Pairing PIN issued for $alias")
        // Exact legacy toast copy (Show-PinPanel path B, WPF tray balloon).
        showSystemNotification("DeX Pairing PIN", "Enter PIN $pin on $alias", java.awt.TrayIcon.MessageType.INFO)
    }

    override fun cancelPairingNotification() {
        Logger.i("[DesktopPlatformEngine] Cancel pairing notification")
    }

    override fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
        Logger.i("[DesktopPlatformEngine] Incoming file transfer: $fileCount files (session $sessionId)")
        showSystemNotification("Incoming Files", "Receiving $fileCount files.", java.awt.TrayIcon.MessageType.INFO)
    }

    override fun setClipboardText(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            Logger.i("[DesktopPlatformEngine] Clipboard set")
        } catch (e: Exception) {
            Logger.i("[DesktopPlatformEngine] Failed to set clipboard: ${e.message}")
        }
    }

    override fun downloadBatch(senderIp: String, port: Int, tcpFallbackPort: Int, files: List<PullFileDto>, fingerprint: String, sourceAlias: String) {
        // Pull receiver for PC-to-PC transfers that arrive via the control channel
        // (cross-network relay pushes). Streams from the sender's hosted endpoints.
        val httpClient = runCatching {
            org.koin.core.context.GlobalContext.get().get<io.ktor.client.HttpClient>()
        }.getOrNull()
        if (httpClient == null) {
            Logger.i("[DesktopPlatformEngine] Cannot receive pulled files: HttpClient unavailable")
            return
        }
        DesktopPullService(httpClient).downloadBatch(senderIp, port, tcpFallbackPort, files, fingerprint, sourceAlias)
    }

    override fun handleFileExplorerRequest(type: String, data: JsonObject) {
        // Intentionally unsupported: this desktop is the EXPLORER CLIENT (it browses phones);
        // exposing local folders back to phone-initiated browse requests is not a product flow.
        Logger.i("[DesktopPlatformEngine] Ignoring explorer request '$type' (desktop does not expose folders)")
    }

    override fun handleMirrorStart() {
        Logger.i("[DesktopPlatformEngine] handleMirrorStart")
    }

    override fun handleMirrorStop() {
        Logger.i("[DesktopPlatformEngine] handleMirrorStop")
    }
}
