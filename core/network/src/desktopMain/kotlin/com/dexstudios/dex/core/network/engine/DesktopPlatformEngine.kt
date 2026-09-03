package com.dexstudios.dex.core.network.engine

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DesktopPullService
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.PullFileDto
import com.dexstudios.dex.core.network.sync.WanRelayClient
import kotlinx.coroutines.launch
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

    override fun downloadWanRelay(sessionId: String, streamToken: String, relayUrl: String, fileName: String, totalBytes: Long, fingerprint: String, sourceAlias: String) {
        val pairedToken = com.dexstudios.dex.auth.AuthState.pairedTokens.value[fingerprint]
        if (pairedToken.isNullOrBlank()) {
            Logger.i("[DesktopPlatformEngine] Rejecting WAN relay offer from unpaired device $fingerprint")
            return
        }
        val httpClient = runCatching {
            org.koin.core.context.GlobalContext.get().get<io.ktor.client.HttpClient>()
        }.getOrNull()
        if (httpClient == null) {
            Logger.i("[DesktopPlatformEngine] Cannot receive WAN relay files: HttpClient unavailable")
            return
        }
        val alias = sourceAlias.ifBlank { "Remote Device" }
        val downloadsFolder = com.dexstudios.dex.core.network.server.ReceiveStorage.downloadsDir()
        if (downloadsFolder.freeSpace < totalBytes) {
            Logger.i("[DesktopPlatformEngine] Insufficient disk space for WAN relay file from $alias")
            return
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            val monitor = com.dexstudios.dex.core.network.TransferStateMonitor
            val transferId = java.util.UUID.randomUUID().toString()
            monitor.updateIncomingProgress(transferId, alias, 1, 0)
            val dest = com.dexstudios.dex.core.network.server.ReceiveStorage.uniqueDest(downloadsFolder, fileName)
            val part = java.io.File(dest.parentFile, "${dest.name}.part")
            try {
                val relayClient = com.dexstudios.dex.core.network.sync.WanRelayClient(
                    client = httpClient,
                    baseUrlProvider = { relayUrl },
                    tokenProvider = { "" },
                )
                val session = WanRelayClient.RelaySession(sessionId, streamToken, fingerprint)
                part.outputStream().use { output ->
                    relayClient.download(session, pairedToken, output)
                }
                if (totalBytes > 0 && part.length() != totalBytes) {
                    throw IllegalStateException("Size mismatch: expected $totalBytes, got ${part.length()}")
                }
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                com.dexstudios.dex.core.network.TransferHistoryRecorder.recordCompleted(
                    name = dest.name,
                    size = dest.length(),
                    direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_RECEIVED,
                    uri = dest.absolutePath,
                    peerDevice = alias,
                )
                monitor.updateIncomingProgress(transferId, alias, 1, 1)
                Logger.i("[DesktopPlatformEngine] WAN relay download completed: ${dest.absolutePath}")
            } catch (e: Exception) {
                part.delete()
                com.dexstudios.dex.core.network.TransferHistoryRecorder.recordFailed(
                    name = fileName,
                    size = totalBytes,
                    direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_RECEIVED,
                    peerDevice = alias,
                )
                monitor.removeSession(transferId)
                Logger.i("[DesktopPlatformEngine] WAN relay download failed: ${e.message}")
            }
        }
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
