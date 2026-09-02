package com.dexstudios.dex.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dexstudios.dex.network.pairing.PairingCoordinator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class MessageHandler(
    private val deviceConfig: DeviceConfig,
    private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val fileShareManager: FileShareManager
) {
    private val json = DexJson

    var onSendMessage: ((String) -> Unit)? = null
        set(value) {
            field = value
            pairingCoordinator = createPairingCoordinator()
        }

    private var pairingCoordinator: PairingCoordinator = createPairingCoordinator()

    private fun createPairingCoordinator(): PairingCoordinator = PairingCoordinator(
        deviceConfig = deviceConfig,
        context = context,
        notificationHelper = notificationHelper,
        sendMessage = { onSendMessage?.invoke(it) }
    )

    fun handleMessage(text: String, senderIp: String, senderPort: Int) {
        try {
            Timber.d("Received message from $senderIp:$senderPort")
            val jsonObject = json.decodeFromString<JsonObject>(text)
            val type = jsonObject[ProtocolKeys.TYPE]?.jsonPrimitive?.content ?: return
            val dataElement = jsonObject[ProtocolKeys.DATA] ?: return

            when (type) {
                ProtocolKeys.PAIR_PROMPT -> pairingCoordinator.handlePairPrompt(dataElement)
                ProtocolKeys.PAIR_CANCELLED -> pairingCoordinator.handlePairCancelled()
                ProtocolKeys.PAIR_ACCEPTED -> pairingCoordinator.handlePairAccepted(dataElement)
                ProtocolKeys.IDENTITY_CHALLENGE -> pairingCoordinator.handleIdentityChallenge(dataElement)
                ProtocolKeys.PREPARE_UPLOAD -> handlePrepareUpload(dataElement, senderIp)
                ProtocolKeys.PUBLIC_ADDRESS -> handlePublicAddress(dataElement)
                ProtocolKeys.ENDPOINT_INFO -> handleEndpointInfo(dataElement)
                ProtocolKeys.PEER_ENDPOINT -> handlePeerEndpoint(dataElement)
                ProtocolKeys.DEVICE_ROSTER -> handleDeviceRoster(dataElement)
                ProtocolKeys.TRUST_CHECK -> handleTrustCheck(dataElement)
                ProtocolKeys.UNPAIR -> handleUnpair(dataElement)
                ProtocolKeys.RELAY_STARTED -> handleRelayReply(true)
                ProtocolKeys.RELAY_ERROR -> handleRelayReply(false)
                ProtocolKeys.RELAY_OFFER -> handleRelayOffer(dataElement)
                ProtocolKeys.SET_CLIPBOARD -> handleSetClipboard(dataElement)
                ProtocolKeys.WALLPAPER_UPDATED -> WallpaperState.notifyUpdated()
                ProtocolKeys.LIST_SHARED_FOLDERS, ProtocolKeys.BROWSE_FOLDER, ProtocolKeys.PULL_FILES, ProtocolKeys.PULL_CANCEL, ProtocolKeys.GRANT_SHARED_FOLDER ->
                    fileShareManager.handleRequest(type, dataElement as? JsonObject ?: JsonObject(emptyMap()))
                else -> {
                    Timber.w("Unknown message type received: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
        }
    }

    private fun handlePrepareUpload(dataElement: JsonElement, senderIp: String) {
        val uploadReq = json.decodeFromJsonElement<PrepareUploadRequestDto>(dataElement)
        Timber.i("Incoming prepare-upload via WebSocket from ${uploadReq.info.alias} for ${uploadReq.files.size} files")

        val dirUri = SafStorage.getDownloadsDexUri(context)

        // Pull mode: download the whole session immediately without prompt for paired/trusted peers
        val files = uploadReq.files.map { (fileId, file) -> PullFileDto(fileId, file.fileName, file.size, file.token) }
        TcpDownloadService.downloadBatch(
            context,
            senderIp,
            uploadReq.info.port,
            uploadReq.info.tcpFallbackPort,
            files,
            dirUri,
            fingerprint = uploadReq.info.fingerprint,
            sourceAlias = uploadReq.info.alias,
        )
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

    /** Incoming cloud relay E2EE streaming transfer offer (Plan 032 / Option 3). */
    private fun handleRelayOffer(dataElement: JsonElement) {
        val dataObj = dataElement as? JsonObject ?: return
        val sessionId = dataObj["sessionId"]?.jsonPrimitive?.contentOrNull ?: return
        val streamToken = dataObj["streamToken"]?.jsonPrimitive?.contentOrNull ?: return
        val relayUrl = dataObj["relayUrl"]?.jsonPrimitive?.contentOrNull ?: return
        val fileName = dataObj["fileName"]?.jsonPrimitive?.contentOrNull ?: "shared_file"
        val size = dataObj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val fingerprint = dataObj["fingerprint"]?.jsonPrimitive?.contentOrNull ?: return
        val alias = dataObj["alias"]?.jsonPrimitive?.contentOrNull ?: "Remote Device"

        val pairedToken = DeviceManager.getPairedToken(fingerprint)
        if (pairedToken.isNullOrBlank()) {
            Timber.w("Rejecting relay offer from unpaired device $fingerprint")
            return
        }

        val dirUri = SafStorage.getDownloadsDexUri(context)
        Timber.i("Incoming WAN relay offer from $alias ($fingerprint) for $fileName ($size bytes)")

        TcpDownloadService.downloadWanRelay(
            context = context,
            sessionId = sessionId,
            streamToken = streamToken,
            relayUrl = relayUrl,
            pairedToken = pairedToken,
            fileName = fileName,
            totalBytes = size,
            destDirUri = dirUri,
            fingerprint = fingerprint,
            sourceAlias = alias
        )
    }

    /**
     * Replies to the PC's pair-prompt. When [enteredPin] is non-empty it is echoed so the PC
     * can verify the PIN proof server-side before persisting trust; a null/empty pin (timeout,
     * cancel, or the already-paired auto-accept path) means the PC will require explicit
     * user consent on its pairing panel instead.
     */
    fun sendPairResponse(accepted: Boolean, enteredPin: String? = null) {
        pairingCoordinator.sendPairResponse(accepted, enteredPin)
    }

    /** Emits live keystroke telemetry so the desktop pairing UI can highlight matching digits in real time. */
    fun sendPinDigitEntered(digitCount: Int) {
        pairingCoordinator.sendPinDigitEntered(digitCount)
    }
}
