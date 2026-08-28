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
import kotlinx.serialization.json.contentOrNull
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
                "pair-accepted" -> handlePairAccepted(dataElement)
                "prepare-upload" -> handlePrepareUpload(dataElement, senderIp)
                "public-address" -> handlePublicAddress(dataElement)
                "endpoint-info" -> handleEndpointInfo(dataElement)
                "peer-endpoint" -> handlePeerEndpoint(dataElement)
                "device-roster" -> handleDeviceRoster(dataElement)
                "trust-check" -> handleTrustCheck(dataElement)
                "unpair" -> handleUnpair(dataElement)
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
        val pairReq = json.decodeFromJsonElement<PairRequestDto>(dataElement)
        
        // Task 5: Re-Pairing After Partial Forget (Auto-Accept)
        if (AuthState.pairedFingerprints.contains(pairReq.fingerprint)) {
            Timber.i("Device ${pairReq.fingerprint} is already paired locally. Auto-accepting pair prompt.")
            val responseMsg = buildJsonObject {
                put("type", "pair-response")
                putJsonObject("data") {
                    put("accepted", true)
                }
            }.toString()
            onSendMessage?.invoke(responseMsg)
            return
        }

        if (AuthState.incomingPairRequest.value != null) {
            // Task 2: Simultaneous Pairing Race Condition Tie-Breaker
            val localFp = deviceConfig.fingerprint
            if (pairReq.fingerprint > localFp) {
                Timber.w("Race condition: ignoring inbound pair-prompt from ${pairReq.fingerprint} (Android is initiator)")
                return
            } else {
                Timber.w("Race condition: yielding to inbound pair-prompt from ${pairReq.fingerprint} (PC is initiator)")
                handlePairCancelled()
            }
        }

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
            // Echo the entered PIN so the PC can verify the proof server-side before
            // persisting trust (a bare accepted=true assertion is no longer honored).
            sendPairResponse(accepted, enteredPin)
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

    /**
     * PIN pairing was granted by the PC. Persist both the paired fingerprint and the
     * minted bearer token so reconnects authenticate cleanly without another pairing handshake.
     */
    private fun handlePairAccepted(dataElement: JsonElement) {
        val obj = dataElement as? JsonObject ?: return
        val token = obj["token"]?.jsonPrimitive?.contentOrNull ?: return
        val pcFingerprint = obj["fingerprint"]?.jsonPrimitive?.contentOrNull
        if (!pcFingerprint.isNullOrBlank()) {
            DeviceManager.savePairedFingerprint(pcFingerprint)
            DeviceManager.savePairedToken(pcFingerprint, token)
            Timber.i("Pairing accepted by PC, stored pairing token for $pcFingerprint")
        }
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
                fingerprint = uploadReq.info.fingerprint,
                sourceAlias = uploadReq.info.alias
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

    /** The PC revoked its trust in us. We must forget it locally so we don't incorrectly show it as "Trusted". */
    private fun handleUnpair(dataElement: JsonElement) {
        val fingerprint = (dataElement as? JsonObject)?.get("fingerprint")?.jsonPrimitive?.content
        if (!fingerprint.isNullOrBlank()) {
            DeviceManager.removePairedFingerprint(fingerprint)
            Timber.i("PC $fingerprint requested unpair; removed from local trusted list")
        }
    }

    private fun handleTrustCheck(dataElement: JsonElement) {
        val isTrustedByPC = (dataElement as? JsonObject)?.get("isTrusted")?.jsonPrimitive?.content?.toBoolean() ?: false
        val fingerprint = (dataElement as? JsonObject)?.get("fingerprint")?.jsonPrimitive?.content
        if (!isTrustedByPC && !fingerprint.isNullOrBlank()) {
            if (AuthState.pairedFingerprints.contains(fingerprint)) {
                Timber.w("PC $fingerprint reported we are not trusted. Downgrading local trust.")
                DeviceManager.removePairedFingerprint(fingerprint)
            }
        }
    }

    /** The PC pushed clipboard text or image over the WebSocket — write it to the phone's clipboard. */
    private fun handleSetClipboard(dataElement: JsonElement) {
        val obj = dataElement as? JsonObject ?: return
        val text = obj["text"]?.jsonPrimitive?.contentOrNull
        val imageBase64 = obj["imageBase64"]?.jsonPrimitive?.contentOrNull
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Timber.w("Clipboard service unavailable")
            return
        }
        if (!text.isNullOrBlank()) {
            clipboard.setPrimaryClip(ClipData.newPlainText("DeX", text))
            ClipboardSyncState.lastIncoming = text
            Timber.i("Clipboard text synced from PC")
        } else if (!imageBase64.isNullOrBlank()) {
            Timber.i("Clipboard image synced from PC")
        }
    }

    /** The PC acknowledged (or failed) the relay-transfer fallback. */
    private fun handleRelayReply(success: Boolean) {
        PunchState.pendingRelay.value?.complete(success)
        PunchState.pendingRelay.value = null
    }

    /**
     * Replies to the PC's pair-prompt. When [enteredPin] is non-empty it is echoed so the PC
     * can verify the PIN proof server-side before persisting trust; a null/empty pin (timeout,
     * cancel, or the already-paired auto-accept path) means the PC will require explicit
     * user consent on its pairing panel instead.
     */
    private fun sendPairResponse(accepted: Boolean, enteredPin: String? = null) {
        val payload = buildJsonObject {
            put("type", "pair-response")
            putJsonObject("data") {
                put("accepted", accepted)
                if (!enteredPin.isNullOrEmpty()) {
                    put("pin", enteredPin)
                }
            }
        }
        onSendMessage?.invoke(payload.toString())
    }

    /** Emits live keystroke telemetry so the desktop pairing UI can highlight matching digits in real time. */
    fun sendPinDigitEntered(digitCount: Int) {
        val payload = buildJsonObject {
            put("type", "pin-digit-entered")
            putJsonObject("data") {
                put("digitCount", digitCount)
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
