package com.dexstudios.dex.core.network.server.routes

import co.touchlab.kermit.Logger
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceManager
import com.dexstudios.dex.core.network.EndpointInfoDto
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.core.network.RosterDeviceDto
import com.dexstudios.dex.core.network.RosterDto
import com.dexstudios.dex.core.network.server.DexRequestStore
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.network.services.PublicAddressService
import com.dexstudios.dex.core.network.services.RelayService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Handshake trust decision for a `/ws` connection.
 *
 * A connection is TRUSTED when its bearer token equals our googleSub / identityHash
 * (same-account auto-trust) or the fingerprint's stored pairing token. These values are
 * NEVER advertised (see DiscoveryEngine.localInfo) so only legitimate holders can present
 * them. Untrusted connections may run the pairing handshake, prove same-account identity
 * via identity-challenge/identity-proof, until the PIN is proven — they never receive
 * transfer prompts or hosted pull tokens.
 */
private fun resolveHandshakeTrust(fingerprint: String, token: String?): Pair<Boolean, String?> {
    if (token.isNullOrEmpty()) return false to null
    val koin = org.koin.core.context.GlobalContext.get()
    val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()

    // Length pre-check keeps MessageDigest.isEqual effective; isEqual alone short-circuits
    // nothing harmful, but equal lengths avoid trivially-timed rejections.
    fun matches(secret: String): Boolean = secret.isNotEmpty() && secret.length == token.length &&
        java.security.MessageDigest.isEqual(token.toByteArray(), secret.toByteArray())

    if (matches(deviceConfig.googleSub)) return true to deviceConfig.googleSub
    if (matches(deviceConfig.identityHash)) return true to deviceConfig.identityHash

    val pairedToken = AuthState.pairedTokens.value[fingerprint]
    if (!pairedToken.isNullOrEmpty() && pairedToken.length == token.length &&
        java.security.MessageDigest.isEqual(token.toByteArray(), pairedToken.toByteArray())
    ) {
        return true to null
    }
    return false to null
}

/** HMAC-SHA256 over [data] keyed by [secret] — proof-of-possession without disclosure. */
private fun hmacSha256(secret: String, data: ByteArray): ByteArray {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(data)
}

/**
 * Mints a per-device pairing token, persists the pairing, upgrades the live session to
 * trusted, and returns the token for delivery to the peer so IT can persist the pairing
 * too — without this, PIN-paired devices had no shared credential and could never
 * re-authenticate after reconnecting.
 */
private suspend fun grantPairing(fingerprint: String): String {
    val pairToken = java.util.UUID.randomUUID().toString()
    DeviceManager.savePairedFingerprint(fingerprint)
    DeviceManager.savePairedToken(fingerprint, pairToken)
    WebSocketConnectionManager.markTrusted(fingerprint)
    return pairToken
}

fun Route.webSocketRoutes(pairingEngine: com.dexstudios.dex.auth.PairingEngine, mirrorEngine: IMirrorEngine, publicAddressService: PublicAddressService? = null) {
    webSocket("/ws") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        val token = call.request.queryParameters["token"]

        var registered = false
        // Per-connection nonce for the same-account proof-of-possession exchange. Handler
        // scoped: no shared map, nothing for another peer to read or replay.
        var identityNonce: ByteArray? = null
        if (!fingerprint.isNullOrBlank()) {
            val (trusted, identityToken) = resolveHandshakeTrust(fingerprint, token)
            // Hijack guard: an active session for this fingerprint is never silently replaced
            registered = WebSocketConnectionManager.register(fingerprint, this, trusted, identityToken)
            if (!registered) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "fingerprint already connected"))
                return@webSocket
            }

            Logger.i("WebSocket connection established: ${call.request.local.remoteHost} (FP: $fingerprint, trusted: $trusted)")

            // Same-account phones need our WAN address to reach us without port-forwarding know-how
            if (trusted && publicAddressService != null) {
                launch {
                    val address = runCatching { publicAddressService.publicAddress() }.getOrNull()
                    if (!address.isNullOrBlank()) {
                        WebSocketConnectionManager.sendRequest(
                            fingerprint,
                            buildJsonObject {
                                put("type", "public-address")
                                putJsonObject("data") { put("address", address) }
                            }.toString(),
                        )
                    }
                }
            }

            // Untrusted sessions get one chance to prove they share our Google account:
            // we challenge; they answer HMAC(nonce, googleSub). The sub itself never
            // travels — a third party learns only "same account or not" about the PAIR.
            if (!trusted) {
                val mySub = org.koin.core.context.GlobalContext.get()
                    .get<com.dexstudios.dex.core.network.DeviceConfig>()
                    .googleSub
                if (mySub.isNotEmpty()) {
                    val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                    identityNonce = nonce
                    WebSocketConnectionManager.sendRequest(
                        fingerprint,
                        buildJsonObject {
                            put("type", "identity-challenge")
                            putJsonObject("data") { put("nonce", java.util.Base64.getEncoder().encodeToString(nonce)) }
                        }.toString(),
                    )
                }
            }
        } else {
            Logger.i("WebSocket connection established: ${call.request.local.remoteHost} (no fingerprint)")
        }

        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val jsonElement = Json.parseToJsonElement(text)
                        val jsonObject = jsonElement.jsonObject
                        val type = jsonObject["type"]?.jsonPrimitive?.content
                        val dataObj = jsonObject["data"] as? JsonObject

                        when (type) {
                            "list-shared-folders-reply",
                            "browse-reply",
                            "grant-shared-folder-reply",
                            "grant-reply",
                            "pull-reply",
                            "reply",
                            -> {
                                val reqId = dataObj?.get("requestId")?.jsonPrimitive?.content
                                    ?: jsonObject["requestId"]?.jsonPrimitive?.content
                                if (reqId != null) {
                                    DexRequestStore.completeRequest(reqId, dataObj ?: jsonObject)
                                }
                            }

                            "pair-request" -> {
                                if (fingerprint != null) {
                                    val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
                                    if (deviceConfig.dndEnabled) {
                                        // Do Not Disturb: never surface the pairing prompt and never
                                        // mint a PIN. The requester's offer times out on its side.
                                        Logger.i("DND: ignored inbound pairing request from $fingerprint")
                                    } else {
                                        val ip = call.request.local.remoteHost
                                        val pin = pairingEngine.handleInboundPairingRequest(ip, fingerprint)
                                        val promptJson = buildJsonObject {
                                            put("type", "pair-prompt")
                                            putJsonObject("data") {
                                                put("pin", pin)
                                                put("alias", deviceConfig.alias.ifBlank { "DeX Desktop" })
                                                put("fingerprint", deviceConfig.fingerprint)
                                            }
                                        }.toString()
                                        WebSocketConnectionManager.sendRequest(fingerprint, promptJson)
                                    }
                                }
                            }

                            "pair-response" -> {
                                val accepted = dataObj?.get("accepted")?.jsonPrimitive?.content?.toBoolean() == true
                                val claimedPin = dataObj?.get("pin")?.jsonPrimitive?.contentOrNull
                                val verifiedByPin = accepted && fingerprint != null &&
                                    pairingEngine.verifyInboundPin(fingerprint, claimedPin.orEmpty())
                                when {
                                    // Peer proved knowledge of the displayed PIN: grant trust,
                                    // persist BOTH sides (we store a freshly minted pairing
                                    // token and hand it back so the peer can store it too),
                                    // and upgrade the session so prompts flow immediately.
                                    verifiedByPin -> {
                                        val pairToken = grantPairing(fingerprint)
                                        WebSocketConnectionManager.sendRequest(
                                            fingerprint,
                                            buildJsonObject {
                                                put("type", "pair-accepted")
                                                putJsonObject("data") {
                                                    put("token", pairToken)
                                                    put(
                                                        "fingerprint",
                                                        org.koin.core.context.GlobalContext.get()
                                                            .get<com.dexstudios.dex.core.network.DeviceConfig>().fingerprint,
                                                    )
                                                }
                                            }.toString(),
                                        )
                                        pairingEngine.handlePairResponse(true)
                                    }

                                    // Trust assertion without PIN proof is never persisted; the desktop
                                    // user can still grant access manually via the pairing panel.
                                    accepted -> {
                                        Logger.i("Rejected pair-response from $fingerprint: PIN not proven")
                                        pairingEngine.handlePairResponse(false)
                                    }

                                    else -> pairingEngine.handlePairResponse(false)
                                }
                            }

                            /*
                             * Answer to our identity-challenge: HMAC(nonce, googleSub) proves the
                             * peer holds our Google account ID without it ever crossing the wire.
                             * Session-scoped auto-trust; pairing persistence stays explicit.
                             */
                            "identity-proof" -> {
                                val mac = dataObj?.get("mac")?.jsonPrimitive?.contentOrNull
                                val nonce = identityNonce
                                if (fingerprint != null && mac != null && nonce != null &&
                                    !WebSocketConnectionManager.isTrusted(fingerprint)
                                ) {
                                    val deviceConfig = org.koin.core.context.GlobalContext.get()
                                        .get<com.dexstudios.dex.core.network.DeviceConfig>()
                                    val expected = hmacSha256(deviceConfig.googleSub, nonce)
                                    val presented = runCatching {
                                        java.util.Base64.getDecoder().decode(mac)
                                    }.getOrNull()
                                    if (presented != null && presented.size == expected.size &&
                                        java.security.MessageDigest.isEqual(presented, expected)
                                    ) {
                                        WebSocketConnectionManager.markTrusted(fingerprint, deviceConfig.googleSub)
                                        Logger.i("Same-account identity proven for FP: $fingerprint")
                                    }
                                }
                            }

                            /*
                             * Peer-initiated revocation: the connected device asks us to forget
                             * ITSELF. Only the owner of the live session may revoke its own
                             * entry — never an arbitrary third fingerprint.
                             */
                            "unpair" -> {
                                if (fingerprint != null) {
                                    DeviceManager.removePairedFingerprint(fingerprint)
                                    WebSocketConnectionManager.markUntrusted(fingerprint)
                                    pairingEngine.reset()
                                    Logger.i("Peer $fingerprint revoked its pairing; local trust removed")
                                }
                            }

                            "pin-digit-entered" -> {
                                val count = dataObj?.get("digitCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                pairingEngine.handlePinDigitEntered(count)
                            }

                            "pull-progress" -> {
                                val reqId = dataObj?.get("requestId")?.jsonPrimitive?.content
                                if (reqId != null) {
                                    val state = dataObj["state"]?.jsonPrimitive?.contentOrNull ?: "running"
                                    val doneFiles = dataObj["doneFiles"]?.jsonPrimitive?.intOrNull ?: 0
                                    val totalFiles = dataObj["totalFiles"]?.jsonPrimitive?.intOrNull ?: 0
                                    val sentBytes = dataObj["sentBytes"]?.jsonPrimitive?.longOrNull ?: 0L
                                    val totalBytes = dataObj["totalBytes"]?.jsonPrimitive?.longOrNull ?: 0L
                                    val currentFile = dataObj["currentFile"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    val explorer = org.koin.core.context.GlobalContext.get()
                                        .get<com.dexstudios.dex.core.network.services.FileExplorerService>()
                                    val current = explorer.pullProgress.value
                                    explorer.updatePullProgress(
                                        current.copy(
                                            requestId = reqId,
                                            activeFileName = currentFile.ifEmpty { current.activeFileName },
                                            completedFiles = doneFiles,
                                            totalFiles = totalFiles,
                                            bytesTransferred = sentBytes,
                                            totalBytes = totalBytes,
                                            progress = if (totalBytes > 0) sentBytes.toFloat() / totalBytes else current.progress,
                                            isPulling = state == "running",
                                            isDone = state == "done",
                                        ),
                                    )
                                }
                            }

                            // ---- Phone-role requests this PC must answer for phone-to-phone ----

                            /*
                             * The phone asks which same-email devices are online so it can offer
                             * direct NAT-punched transfers. Membership is derived from the identity
                             * each connected session PROVED at handshake — never from client claims.
                             */
                            "device-roster" -> handleDeviceRosterRequest(fingerprint)

                            /* Punch rendezvous: answer with the target's last registered endpoint. */
                            "resolve-endpoint" -> handleResolveEndpoint(fingerprint, dataObj)

                            /* Announce the sender's endpoint to the punch target. */
                            "peer-endpoint" -> handlePeerEndpointAnnounce(fingerprint, dataObj)

                            /*
                             * Trust desync check: the phone asserts what it believes; we answer with
                             * the authoritative view so it can downgrade stale trust.
                             */
                            "trust-check" -> handleTrustCheck(fingerprint, dataObj)

                            /*
                             * A->PC->B fallback: files already uploaded here under sessionId;
                             * verify arrival, host them, push a prompt to the target.
                             */
                            "relay-transfer" -> handleRelayTransfer(fingerprint, dataObj)

                            else -> {
                                Logger.i("Unhandled WS message type: $type from FP: $fingerprint")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.i("WebSocket handler error (${e::class.simpleName}): ${e.message}")
                    }
                } else if (frame is Frame.Binary) {
                    val bytes = frame.readBytes()
                    mirrorEngine.receiveFrame(bytes)
                }
            }
        } catch (e: Exception) {
            Logger.i("WebSocket error: ${e.message}")
        } finally {
            if (fingerprint != null && registered) {
                WebSocketConnectionManager.unregister(fingerprint)
            }
            Logger.i("WebSocket connection closed: ${call.request.local.remoteHost} (FP: $fingerprint)")
        }
    }
}

/** Replies `device-roster` with every OTHER trusted session that shares our account identity. */
private suspend fun handleDeviceRosterRequest(requesterFingerprint: String?) {
    if (requesterFingerprint == null) return
    val koin = org.koin.core.context.GlobalContext.get()
    val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()
    val discovery = koin.getOrNull<com.dexstudios.dex.core.network.DiscoveryEngine>()

    val mySub = deviceConfig.googleSub.takeIf { it.isNotBlank() }

    val devices = WebSocketConnectionManager.trustedFingerprints()
        .filterNot { it == requesterFingerprint }
        .mapNotNull { fp ->
            // Roster membership requires the peer to have PROVEN our googleSub at
            // handshake or via identity-proof — never a client claim.
            val identity = WebSocketConnectionManager.holderOf(fp)?.identityToken
            val sameAccount = mySub != null && identity == mySub
            if (!sameAccount) return@mapNotNull null
            val discovered = discovery?.devices?.value?.get(fp)?.info
            RosterDeviceDto(
                fingerprint = fp,
                alias = discovered?.alias ?: "Phone",
                deviceType = discovered?.deviceType?.takeIf { it.isNotBlank() } ?: "mobile",
            )
        }

    WebSocketConnectionManager.sendRequest(
        requesterFingerprint,
        buildJsonObject {
            put("type", "device-roster")
            put("data", Json.encodeToJsonElement(RosterDto.serializer(), RosterDto(devices)))
        }.toString(),
    )
}

/** Answers `resolve-endpoint` with `endpoint-info` carrying the target's registered punch endpoint. */
private suspend fun handleResolveEndpoint(callerFingerprint: String?, dataObj: JsonObject?) {
    if (callerFingerprint == null) return
    // Punch rendezvous data (other devices' ip:port) is only for proven sessions.
    if (!WebSocketConnectionManager.isTrusted(callerFingerprint)) return
    val targetFingerprint = dataObj?.get("targetFingerprint")?.jsonPrimitive?.contentOrNull ?: return

    val entry = getPunchEndpoint(targetFingerprint)
    WebSocketConnectionManager.sendRequest(
        callerFingerprint,
        buildJsonObject {
            put("type", "endpoint-info")
            put(
                "data",
                Json.encodeToJsonElement(
                    EndpointInfoDto.serializer(),
                    EndpointInfoDto(
                        targetFingerprint = targetFingerprint,
                        ip = entry?.ip.orEmpty(),
                        port = entry?.port ?: 0,
                    ),
                ),
            )
        }.toString(),
    )
}

/** Forwards a sender's punch endpoint to the intended target so both sides race simultaneously. */
private suspend fun handlePeerEndpointAnnounce(senderFingerprint: String?, dataObj: JsonObject?) {
    if (senderFingerprint == null || dataObj == null) return
    val peerFingerprint = dataObj["peerFingerprint"]?.jsonPrimitive?.contentOrNull ?: return

    WebSocketConnectionManager.sendToTrusted(
        peerFingerprint,
        buildJsonObject {
            put("type", "peer-endpoint")
            put("data", dataObj)
        }.toString(),
    )
}

/**
 * The phone reports its belief about trust for [its own] fingerprint; the PC answers with the
 * authoritative state. Auto-trusted identities are always trusted while signed in.
 */
private suspend fun handleTrustCheck(callerFingerprint: String?, dataObj: JsonObject?) {
    if (callerFingerprint == null) return
    val subjectFingerprint = dataObj?.get("fingerprint")?.jsonPrimitive?.contentOrNull ?: callerFingerprint

    val koin = org.koin.core.context.GlobalContext.get()
    val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()
    val identity = WebSocketConnectionManager.holderOf(callerFingerprint)?.identityToken
    val autoTrusted = identity != null && identity == deviceConfig.googleSub
    val actuallyTrusted = autoTrusted || AuthState.pairedFingerprints.value.contains(subjectFingerprint)

    WebSocketConnectionManager.sendRequest(
        callerFingerprint,
        buildJsonObject {
            put("type", "trust-check")
            putJsonObject("data") {
                put("isTrusted", actuallyTrusted)
                put("fingerprint", subjectFingerprint)
            }
        }.toString(),
    )
}

/**
 * Completes the phone-to-phone relay fallback: waits until the staged upload fully arrived,
 * then hosts those files and pushes a prepare-upload prompt at the final target. Replies
 * `relay-started` / `relay-error` to the requesting phone.
 */
private suspend fun handleRelayTransfer(requesterFingerprint: String?, dataObj: JsonObject?) {
    if (requesterFingerprint == null || dataObj == null) return
    val targetFingerprint = dataObj["targetFingerprint"]?.jsonPrimitive?.contentOrNull
    val sessionId = dataObj["sessionId"]?.jsonPrimitive?.contentOrNull

    if (targetFingerprint.isNullOrBlank() || sessionId.isNullOrBlank()) {
        WebSocketConnectionManager.sendRequest(
            requesterFingerprint,
            relayReplyJson(success = false, sessionId = sessionId, targetFingerprint = targetFingerprint),
        )
        return
    }

    // Long-running orchestration must not block the WS reader loop
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val delivered = RelayService.relayUploadedSession(sessionId, targetFingerprint)
        WebSocketConnectionManager.sendRequest(
            requesterFingerprint,
            relayReplyJson(success = delivered, sessionId = sessionId, targetFingerprint = targetFingerprint),
        )
    }
}

private fun relayReplyJson(success: Boolean, sessionId: String?, targetFingerprint: String?): String = buildJsonObject {
    put("type", if (success) "relay-started" else "relay-error")
    putJsonObject("data") {
        put("sessionId", sessionId ?: "")
        put("targetFingerprint", targetFingerprint ?: "")
    }
}.toString()
