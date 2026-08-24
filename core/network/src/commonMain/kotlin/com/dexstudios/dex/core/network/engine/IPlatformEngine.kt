package com.dexstudios.dex.core.network.engine

import com.dexstudios.dex.core.network.PullFileDto
import kotlinx.serialization.json.JsonObject

interface IPlatformEngine {
    fun showPairingRequestNotification(alias: String)
    fun cancelPairingNotification()
    fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int)

    fun setClipboardText(text: String)

    fun downloadBatch(senderIp: String, port: Int, tcpFallbackPort: Int, files: List<PullFileDto>, fingerprint: String, sourceAlias: String)

    fun handleFileExplorerRequest(type: String, data: JsonObject)

    fun handleMirrorStart()
    fun handleMirrorStop()
}
