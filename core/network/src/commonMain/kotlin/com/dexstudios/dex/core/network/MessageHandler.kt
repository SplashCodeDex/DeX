package com.dexstudios.dex.core.network

import co.touchlab.kermit.Logger
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.engine.IPlatformEngine
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import io.ktor.util.date.getTimeMillis
import io.ktor.util.generateNonceBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.milliseconds

class MessageHandler(
    private val deviceConfig: DeviceConfig,
    private val engine: IPlatformEngine,
    // Handler-owned supervised scope: fire-and-forget work launched here is cancellable
    // as a unit (tests supervise/await it instead of leaking untracked IO coroutines that
    // outlive teardown and crash the NEXT test with exceptions from deleted temp dirs).
    private val handlerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Cancels in-flight handler work (test teardown / session shutdown). */
    fun shutdown() {
        handlerScope.coroutineContext.cancelChildren()
    }

    var onSendMessage: ((String) -> Unit)? = null

    /**
     * Supplies the fingerprint of the PC the socket is currently connected to, so inbound
     * pairing grants (pair-accepted) can be persisted against the right peer entry.
     * Wired by WebSocketEngine when it opens a session.
     */
    var peerFingerprintProvider: (() -> String?)? = null

    fun handleMessage(text: String, senderIp: String, senderPort: Int) {
        try {
            Logger.i("Received message from $senderIp:$senderPort")
            val type = ProtocolEnvelope.decodeType(text) ?: return
            val dataElement = ProtocolEnvelope.decodeData(text) ?: return

            when (type) {
                MessageTypes.PAIR_PROMPT -> handlePairPrompt(dataElement)

                MessageTypes.PAIR_CANCELLED -> handlePairCancelled()

                MessageTypes.PAIR_ACCEPTED -> handlePairAccepted(dataElement)

                MessageTypes.IDENTITY_CHALLENGE -> handleIdentityChallenge(dataElement)

                MessageTypes.PREPARE_UPLOAD -> handlePrepareUpload(dataElement, senderIp)

                MessageTypes.PUBLIC_ADDRESS -> handlePublicAddress(dataElement)

                MessageTypes.ENDPOINT_INFO -> handleEndpointInfo(dataElement)

                MessageTypes.PEER_ENDPOINT -> handlePeerEndpoint(dataElement)

                MessageTypes.DEVICE_ROSTER -> handleDeviceRoster(dataElement)

                MessageTypes.TRUST_CHECK -> handleTrustCheck(dataElement)

                MessageTypes.UNPAIR -> handleUnpair(dataElement)

                MessageTypes.RELAY_STARTED -> handleRelayReply(true)

                MessageTypes.RELAY_ERROR -> handleRelayReply(false)

                MessageTypes.RELAY_OFFER -> handleRelayOffer(dataElement)

                MessageTypes.SET_CLIPBOARD -> handleSetClipboard(dataElement)

                MessageTypes.WALLPAPER_UPDATED -> WallpaperState.notifyUpdated()

                MessageTypes.MIRROR_START -> engine.handleMirrorStart()

                MessageTypes.MIRROR_STOP -> engine.handleMirrorStop()

                MessageTypes.LIST_SHARED_FOLDERS, MessageTypes.BROWSE_FOLDER, MessageTypes.PULL_FILES, MessageTypes.GRANT_SHARED_FOLDER ->
                    engine.handleFileExplorerRequest(type, dataElement as? JsonObject ?: JsonObject(emptyMap()))

                else -> {
                    Logger.i("Unknown message type received: $type")
                }
            }
        } catch (e: Exception) {
            Logger.i("ERROR: `Failed to parse WebSocket message")
        }
    }

    private fun handlePairPrompt(dataElement: JsonElement) {
        val pairReq = json.decodeFromJsonElement<PairRequestDto>(dataElement)

        // Task 5: Re-Pairing After Partial Forget (Auto-Accept)
        if (AuthState.pairedFingerprints.value.contains(pairReq.fingerprint)) {
            Logger.i("Device ${pairReq.fingerprint} is already paired locally. Auto-accepting pair prompt.")
            val responseMsg = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_RESPONSE) {
                put(FieldNames.ACCEPTED, true)
            }
            onSendMessage?.invoke(responseMsg)
            return
        }

        if (AuthState.incomingPairRequest.value != null) {
            // Task 2: Simultaneous Pairing Race Condition Tie-Breaker
            val localFp = deviceConfig.fingerprint
            if (pairReq.fingerprint > localFp) {
                Logger.i("Race condition: ignoring inbound pair-prompt from ${pairReq.fingerprint} (Android is initiator)")
                return
            } else {
                Logger.i("Race condition: yielding to inbound pair-prompt from ${pairReq.fingerprint} (PC is initiator)")
                handlePairCancelled()
            }
        }

        Logger.i("Incoming pair-prompt via WebSocket from ${pairReq.alias}")
        val info = PairRequestInfo(
            alias = pairReq.alias,
            fingerprint = pairReq.fingerprint,
            pin = pairReq.pin,
            deferred = CompletableDeferred(),
            deadlineElapsedMs = getTimeMillis() + PAIR_PROMPT_TIMEOUT_MS,
        )
        AuthState.updateIncomingPairRequest(info)
        engine.showPairingRequestNotification(pairReq.alias)

        CoroutineScope(Dispatchers.Main).launch {
            val enteredPin = withTimeoutOrNull(PAIR_PROMPT_TIMEOUT_MS.milliseconds) { info.deferred.await() }
            AuthState.updateIncomingPairRequest(null)
            // The dialog resolved by any path (PIN entered, ✕, or countdown timeout) — clear
            // the pairing notification so it never lingers in the shade.
            engine.cancelPairingNotification()

            val accepted = enteredPin != null && enteredPin == pairReq.pin
            if (accepted) {
                DeviceManager.savePairedFingerprint(pairReq.fingerprint)
                pairReq.token?.let { DeviceManager.savePairedToken(pairReq.fingerprint, it) }
                Logger.i("Pairing accepted with ${pairReq.alias}")
            } else {
                Logger.i("Pairing rejected or timed out with ${pairReq.alias}")
            }
            // The PIN is echoed so the PC can verify the proof server-side before persisting
            // trust (parity with the Android MessageHandler contract).
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
        AuthState.updateIncomingPairRequest(null)
        engine.cancelPairingNotification()
        pending.deferred.complete("")
    }

    /**
     * PIN pairing was granted: the PC proved our PIN knowledge and minted a per-device
     * pairing token. Persist BOTH sides of the pairing so reconnects authenticate with
     * the token instead of restarting the whole pairing dance.
     */
    private fun handlePairAccepted(dataElement: JsonElement) {
        val obj = dataElement as? JsonObject ?: return
        val token = obj[FieldNames.TOKEN]?.jsonPrimitive?.contentOrNull ?: return
        val pcFingerprint = peerFingerprintProvider?.invoke()
            ?: obj[FieldNames.FINGERPRINT]?.jsonPrimitive?.contentOrNull
        if (pcFingerprint.isNullOrBlank()) return

        handlerScope.launch {
            DeviceManager.savePairedFingerprint(pcFingerprint)
            DeviceManager.savePairedToken(pcFingerprint, token)
            Logger.i("Pairing token stored for PC $pcFingerprint")
        }
    }

    /**
     * Same-account proof-of-possession: the PC challenges with a random nonce; we answer
     * HMAC(nonce, googleSub). Our googleSub never crosses the wire — a third party can
     * only learn "same account or not" about this exact session.
     */
    private fun handleIdentityChallenge(dataElement: JsonElement) {
        val nonce = (dataElement as? JsonObject)?.get(FieldNames.NONCE)?.jsonPrimitive?.contentOrNull ?: return
        val sub = deviceConfig.googleSub
        if (sub.isBlank() || nonce.isBlank()) return

        runCatching {
            val mac = HashUtils.hmacSha256Base64(sub, java.util.Base64.getDecoder().decode(nonce))
            onSendMessage?.invoke(
                ProtocolEnvelope.envelopeOf(MessageTypes.IDENTITY_PROOF) {
                    put(FieldNames.MAC, mac)
                },
            )
        }.onFailure { Logger.i("Failed to answer identity challenge: ${it.message}") }
    }

    private fun handlePrepareUpload(dataElement: JsonElement, senderIp: String) {
        val uploadReq = json.decodeFromJsonElement<PrepareUploadRequestDto>(dataElement)
        Logger.i("Incoming prepare-upload via WebSocket from ${uploadReq.info.alias} for ${uploadReq.files.size} files")

        val files = uploadReq.files.map { (fileId, file) -> com.dexstudios.dex.core.network.PullFileDto(fileId, file.fileName, file.size, file.token) }
        engine.downloadBatch(
            senderIp,
            uploadReq.info.port,
            uploadReq.info.tcpFallbackPort,
            files,
            uploadReq.info.fingerprint,
            uploadReq.info.alias,
        )
    }

    /** The PC tells us its public IP so WAN transfers work without manual configuration.
     *  Only auto-fills when the field is blank — a manually entered address (e.g. a
     *  DDNS hostname) always wins, otherwise a dynamic IP change would silently break it. */
    private fun handlePublicAddress(dataElement: JsonElement) {
        val address = json.decodeFromJsonElement<PublicAddressDto>(dataElement).address.trim()
        if (address.isNotBlank() && deviceConfig.publicAddress.isBlank()) {
            deviceConfig.setPublicAddress(address)
            Logger.i("Auto-configured public address from PC: $address")
        } else {
            Logger.i("Ignoring auto public address (manual configuration exists or address empty)")
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
            Logger.i("Peer endpoint announced: ${peer.peerFingerprint} at ${peer.ip}:${peer.port}")
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
                    identityHash = null,
                ),
                viaRoster = true,
            )
        }
        Logger.i("Roster updated: ${roster.devices.size} same-email devices")
    }

    /** The PC revoked its trust in us. We must forget it locally so we don't incorrectly show it as "Trusted". */
    private fun handleUnpair(dataElement: JsonElement) {
        val fingerprint = (dataElement as? JsonObject)?.get(FieldNames.FINGERPRINT)?.jsonPrimitive?.contentOrNull
        if (!fingerprint.isNullOrBlank()) {
            handlerScope.launch { DeviceManager.removePairedFingerprint(fingerprint) }
            Logger.i("PC $fingerprint requested unpair; removed from local trusted list")
        }
    }

    private fun handleTrustCheck(dataElement: JsonElement) {
        val isTrustedByPC = (dataElement as? JsonObject)?.get(FieldNames.IS_TRUSTED)?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        val fingerprint = (dataElement as? JsonObject)?.get(FieldNames.FINGERPRINT)?.jsonPrimitive?.contentOrNull
        if (!isTrustedByPC && !fingerprint.isNullOrBlank()) {
            if (AuthState.pairedFingerprints.value.contains(fingerprint)) {
                Logger.i("PC $fingerprint reported we are not trusted. Downgrading local trust.")
                handlerScope.launch {
                    DeviceManager.removePairedFingerprint(fingerprint)
                }
            }
        }
    }

    /** The PC pushed clipboard text over the WebSocket - write it to the phone's clipboard. */
    private fun handleSetClipboard(dataElement: JsonElement) {
        val text = (dataElement as? JsonObject)?.get(FieldNames.TEXT)?.jsonPrimitive?.contentOrNull
        if (text.isNullOrBlank()) {
            Logger.i("set-clipboard with empty text, ignoring")
            return
        }
        engine.setClipboardText(text)
        ClipboardSyncState.emitReceived(text)
        Logger.i("Clipboard synced from Phone")
    }

    /** The PC acknowledged (or failed) the relay-transfer fallback. */
    private fun handleRelayReply(success: Boolean) {
        PunchState.pendingRelay.value?.complete(success)
        PunchState.pendingRelay.value = null
    }

    /** Incoming cloud relay E2EE streaming transfer offer (Plan 032). */
    private fun handleRelayOffer(dataElement: JsonElement) {
        val dataObj = dataElement as? JsonObject ?: return
        val sessionId = dataObj[FieldNames.SESSION_ID]?.jsonPrimitive?.contentOrNull ?: return
        val streamToken = dataObj[FieldNames.STREAM_TOKEN]?.jsonPrimitive?.contentOrNull ?: return
        val relayUrl = dataObj[FieldNames.RELAY_URL]?.jsonPrimitive?.contentOrNull ?: return
        val fileName = dataObj[FieldNames.FILE_NAME]?.jsonPrimitive?.contentOrNull ?: "shared_file"
        val size = dataObj[FieldNames.SIZE]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val fingerprint = dataObj[FieldNames.FINGERPRINT]?.jsonPrimitive?.contentOrNull ?: return
        val alias = dataObj[FieldNames.ALIAS]?.jsonPrimitive?.contentOrNull ?: "Remote Device"

        Logger.i("Incoming WAN relay offer from $alias ($fingerprint) for $fileName ($size bytes)")
        engine.downloadWanRelay(
            sessionId = sessionId,
            streamToken = streamToken,
            relayUrl = relayUrl,
            fileName = fileName,
            totalBytes = size,
            fingerprint = fingerprint,
            sourceAlias = alias,
        )
    }

    /**
     * Replies to the PC's pair-prompt. When [enteredPin] is non-empty it is echoed so the peer
     * can verify the PIN proof before persisting trust; a null/empty pin (timeout, cancel, or
     * the already-paired auto-accept path) means the peer will require explicit user consent.
     */
    private fun sendPairResponse(accepted: Boolean, enteredPin: String? = null) {
        val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_RESPONSE) {
            put(FieldNames.ACCEPTED, accepted)
            if (!enteredPin.isNullOrEmpty()) {
                put(FieldNames.PIN, enteredPin)
            }
        }
        onSendMessage?.invoke(payload)
    }

    /** Emits live keystroke telemetry so the desktop pairing UI can highlight matching digits in real time. */
    fun sendPinDigitEntered(digitCount: Int) {
        val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PIN_DIGIT_ENTERED) {
            put(FieldNames.DIGIT_COUNT, digitCount)
        }
        onSendMessage?.invoke(payload)
    }

    private companion object {
        const val PAIR_PROMPT_TIMEOUT_MS = 60_000L
        const val PROMPT_TIMEOUT_MS = 60_000L
    }
}
