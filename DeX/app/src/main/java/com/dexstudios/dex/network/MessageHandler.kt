package com.dexstudios.dex.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.util.UUID

import kotlin.time.Duration.Companion.milliseconds

class MessageHandler(
    private val deviceConfig: DeviceConfig,
    private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val fileShareManager: FileShareManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    var onSendMessage: ((String) -> Unit)? = null

    fun handleMessage(text: String, senderIp: String, senderPort: Int) {
        try {
            Timber.d("Received message from $senderIp:$senderPort")
            val jsonObject = json.decodeFromString<JsonObject>(text)
            val type = jsonObject["type"]?.jsonPrimitive?.content ?: return
            val dataElement = jsonObject["data"] ?: return

            when (type) {
                "pair-prompt" -> handlePairPrompt(dataElement)
                "pair-cancelled" -> handlePairCancelled()
                "prepare-upload" -> handlePrepareUpload(dataElement, senderIp)
                "public-address" -> handlePublicAddress(dataElement)
                "endpoint-info" -> handleEndpointInfo(dataElement)
                "peer-endpoint" -> handlePeerEndpoint(dataElement)
                "device-roster" -> handleDeviceRoster(dataElement)
                "relay-started" -> handleRelayReply(true)
                "relay-error" -> handleRelayReply(false)
                "set-clipboard" -> handleSetClipboard(dataElement)
                "wallpaper-updated" -> WallpaperState.notifyUpdated()
                "mirror-start" -> MirrorSession.requestStart()
                "mirror-stop" -> MirrorSession.stop()
                "list-shared-folders", "browse-folder", "pull-files", "grant-shared-folder" ->
                    fileShareManager.handleRequest(type, dataElement as? JsonObject ?: JsonObject(emptyMap()))
                else -> {
                    Timber.w("Unknown message type received: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
        }
    }

    private fun handlePairPrompt(dataElement: JsonElement) {
        if (AuthState.incomingPairRequest.value != null) {
            Timber.w("Pairing request already pending, ignoring duplicate")
            return
        }

        val pairReq = json.decodeFromJsonElement<PairRequestDto>(dataElement)
        Timber.i("Incoming pair-prompt via WebSocket from ${pairReq.alias}")
        val info = PairRequestInfo(
            alias = pairReq.alias,
            fingerprint = pairReq.fingerprint,
            pin = pairReq.pin,
            deferred = CompletableDeferred(),
            deadlineElapsedMs = SystemClock.elapsedRealtime() + PAIR_PROMPT_TIMEOUT_MS
        )
        AuthState.incomingPairRequest.value = info
        notificationHelper.showPairingRequestNotification(pairReq.alias)

        CoroutineScope(Dispatchers.Main).launch {
            val enteredPin = withTimeoutOrNull(PAIR_PROMPT_TIMEOUT_MS.milliseconds) { info.deferred.await() }
            AuthState.incomingPairRequest.value = null
            // The dialog resolved by any path (PIN entered, ✕, or countdown timeout) — clear
            // the pairing notification so it never lingers in the shade.
            notificationHelper.cancelPairingNotification()

            val accepted = enteredPin != null && enteredPin == pairReq.pin
            if (accepted) {
                DeviceManager.savePairedFingerprint(pairReq.fingerprint)
                pairReq.token?.let { DeviceManager.savePairedToken(pairReq.fingerprint, it) }
                Timber.i("Pairing accepted with ${pairReq.alias}")
            } else {
                Timber.i("Pairing rejected or timed out with ${pairReq.alias}")
            }
            sendPairResponse(accepted)
        }
    }

    /**
     * The PC cancelled the pairing (user clicked Cancel / timed out on the PC panel).
     * Dismiss the PIN dialog immediately instead of letting it count down its own 60s
     * and only then rejecting — same effect as the user tapping ✕.
     */
    private fun handlePairCancelled() {
        val pending = AuthState.incomingPairRequest.value ?: return
        AuthState.incomingPairRequest.value = null
        notificationHelper.cancelPairingNotification()
        pending.deferred.complete("")
    }

    private fun handlePrepareUpload(dataElement: JsonElement, senderIp: String) {
        val uploadReq = json.decodeFromJsonElement<PrepareUploadRequestDto>(dataElement)
        Timber.i("Incoming prepare-upload via WebSocket from ${uploadReq.info.alias} for ${uploadReq.files.size} files")

        val sessionId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        TransferState.pendingPrompts[sessionId] = deferred
        val notificationId = sessionId.hashCode()
        notificationHelper.showIncomingFileNotification(sessionId, notificationId, uploadReq.files.size)

        CoroutineScope(Dispatchers.IO).launch {
            val accepted = withTimeoutOrNull(PROMPT_TIMEOUT_MS.milliseconds) { deferred.await() } == true
            TransferState.pendingPrompts.remove(sessionId)
            if (!accepted) {
                Timber.i("Incoming transfer rejected or timed out")
                return@launch
            }

            val dirUri = SafStorage.getDownloadsDexUri(context)

            // Pull mode: download the whole session in one work item (QUIC streams, aggregate progress).
            // The HTTPS port comes from the PC's advertised info; the TCP port is the legacy fallback.
            val files = uploadReq.files.map { (fileId, file) -> PullFileDto(fileId, file.fileName, file.size, file.token) }
            TcpDownloadService.downloadBatch(
                context,
                senderIp,
                uploadReq.info.port,
                uploadReq.info.tcpFallbackPort,
                files,
                dirUri,
                fingerprint = uploadReq.info.fingerprint
            )
        }
    }

    /** The PC tells us its public IP so WAN transfers work without manual configuration.
     *  Only auto-fills when the field is blank — a manually entered address (e.g. a
     *  DDNS hostname) always wins, otherwise a dynamic IP change would silently break it. */
    private fun handlePublicAddress(dataElement: JsonElement) {
        val address = json.decodeFromJsonElement<PublicAddressDto>(dataElement).address.trim()
        if (address.isNotBlank() && deviceConfig.publicAddress.isBlank()) {
            deviceConfig.setPublicAddress(address)
            Timber.i("Auto-configured public address from PC: $address")
        } else {
            Timber.i("Ignoring auto public address (manual configuration exists or address empty)")
        }
    }

    /** The PC resolved a punch target's public endpoint — completes the sender's pending request. */
    private fun handleEndpointInfo(dataElement: JsonElement) {
        val info = json.decodeFromJsonElement<EndpointInfoDto>(dataElement)
        PunchState.pendingEndpointInfo.value?.complete(info)
        PunchState.pendingEndpointInfo.value = null
    }

    /** A peer announced it wants to punch us — remember its endpoint for the simultaneous-open. */
    private fun handlePeerEndpoint(dataElement: JsonElement) {
        val peer = json.decodeFromJsonElement<PeerEndpointDto>(dataElement)
        if (peer.ip.isNotBlank() && peer.port > 0) {
            PunchState.incomingPeerEndpoints.value += (peer.peerFingerprint to PunchEndpoint(peer.ip, peer.port))
            Timber.i("Peer endpoint announced: ${peer.peerFingerprint} at ${peer.ip}:${peer.port}")
        }
    }

    /** The PC listed our same-email devices — refresh the roster shown on the main screen. */
    private fun handleDeviceRoster(dataElement: JsonElement) {
        val roster = json.decodeFromJsonElement<RosterDto>(dataElement)
        PunchState.devices.value = roster.devices.map { d ->
            DiscoveredDevice(
                ip = "",
                info = RegisterDto(
                    alias = d.alias,
                    version = "2.0",
                    deviceModel = if (d.deviceType == "desktop") "PC" else "Phone",
                    deviceType = d.deviceType,
                    fingerprint = d.fingerprint,
                    port = 0,
                    protocol = "punch",
                    download = false,
                    identityHash = null
                ),
                viaRoster = true
            )
        }
        Timber.i("Roster updated: ${roster.devices.size} same-email devices")
    }

    /** The PC pushed clipboard text over the WebSocket — write it to the phone's clipboard. */
    private fun handleSetClipboard(dataElement: JsonElement) {
        val text = (dataElement as? JsonObject)?.get("text")?.jsonPrimitive?.content
        if (text.isNullOrBlank()) {
            Timber.w("set-clipboard with empty text, ignoring")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Timber.w("Clipboard service unavailable")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("DeX", text))
        // Remember it so the auto-sync listener does not push it back to the PC
        ClipboardSyncState.lastIncoming = text
        Timber.i("Clipboard synced from PC")
    }

    /** The PC acknowledged (or failed) the relay-transfer fallback. */
    private fun handleRelayReply(success: Boolean) {
        PunchState.pendingRelay.value?.complete(success)
        PunchState.pendingRelay.value = null
    }

    private fun sendPairResponse(accepted: Boolean) {
        val payload = buildJsonObject {
            put("type", "pair-response")
            putJsonObject("data") {
                put("accepted", accepted)
            }
        }
        onSendMessage?.invoke(payload.toString())
    }

    private companion object {
        const val PAIR_PROMPT_TIMEOUT_MS = 60_000L
        const val PROMPT_TIMEOUT_MS = 60_000L
        const val GRANT_WAIT_MS = 180_000L
    }
}
