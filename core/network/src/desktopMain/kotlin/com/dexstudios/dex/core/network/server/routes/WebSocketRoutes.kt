package com.dexstudios.dex.core.network.server.routes

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
 * (same-account auto-trust) or the fingerprint's stored pairing token. Untrusted
 * connections may only run the pairing handshake until the PIN is proven — they never
 * receive transfer prompts or hosted pull tokens.
 */
private fun resolveHandshakeTrust(fingerprint: String, token: String?): Pair<Boolean, String?> {
    if (token.isNullOrEmpty()) return false to null
    val koin = org.koin.core.context.GlobalContext.get()
    val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()

    if (deviceConfig.googleSub.isNotEmpty() && token == deviceConfig.googleSub) return true to deviceConfig.googleSub
    if (deviceConfig.identityHash.isNotEmpty() && token == deviceConfig.identityHash) return true to deviceConfig.identityHash

    val pairedToken = AuthState.pairedTokens.value[fingerprint]
    if (!pairedToken.isNullOrEmpty() && pairedToken.length == token.length &&
        java.security.MessageDigest.isEqual(token.toByteArray(), pairedToken.toByteArray())
    ) {
        return true to null
    }
    return false to null
}

fun Route.webSocketRoutes(pairingEngine: com.dexstudios.dex.auth.PairingEngine, mirrorEngine: IMirrorEngine, publicAddressService: PublicAddressService? = null) {
    webSocket("/ws") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        val token = call.request.queryParameters["token"]

        var registered = false
        if (!fingerprint.isNullOrBlank()) {
            val (trusted, identityToken) = resolveHandshakeTrust(fingerprint, token)
            // Hijack guard: an active session for this fingerprint is never silently replaced
            registered = WebSocketConnectionManager.register(fingerprint, this, trusted, identityToken)
            if (!registered) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "fingerprint already connected"))
                return@webSocket
            }

            println("WebSocket connection established: ${call.request.local.remoteHost} (FP: $fingerprint, trusted: $trusted)")

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
        } else {
            println("WebSocket connection established: ${call.request.local.remoteHost} (no fingerprint)")
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
                                    val ip = call.request.local.remoteHost
                                    val pin = pairingEngine.handleInboundPairingRequest(ip, fingerprint)
                                    val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
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

                            "pair-response" -> {
                                val accepted = dataObj?.get("accepted")?.jsonPrimitive?.content?.toBoolean() == true
                                val claimedPin = dataObj?.get("pin")?.jsonPrimitive?.contentOrNull
                                val verifiedByPin = accepted && fingerprint != null &&
                                    pairingEngine.verifyInboundPin(fingerprint, claimedPin.orEmpty())
                                when {
                                    // Peer proved knowledge of the displayed PIN: grant, persist trust,
                                    // and upgrade its session so prompts can flow immediately.
                                    verifiedByPin -> {
                                        DeviceManager.savePairedFingerprint(fingerprint!!)
                                        WebSocketConnectionManager.markTrusted(fingerprint)
                                        pairingEngine.handlePairResponse(true)
                                    }

                                    // Trust assertion without PIN proof is never persisted; the desktop
                                    // user can still grant access manually via the pairing panel.
                                    accepted -> {
                                        println("Rejected pair-response from $fingerprint: PIN not proven")
                                        pairingEngine.handlePairResponse(false)
                                    }

                                    else -> pairingEngine.handlePairResponse(false)
                                }
                            }

                            "pin-digit-entered" -> {
                                val count = dataObj?.get("digitCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                pairingEngine.handlePinDigitEntered(count)
                            }

                            "pull-progress" -> {
                                val reqId = dataObj?.get("requestId")?.jsonPrimitive?.content
                                if (reqId != null && dataObj != null) {
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
                                println("Unhandled WS message type: $type from FP: $fingerprint")
                            }
                        }
                    } catch (e: Exception) {
                        println("Failed to parse WebSocket message: ${e.message}")
                    }
                } else if (frame is Frame.Binary) {
                    val bytes = frame.readBytes()
                    mirrorEngine.receiveFrame(bytes)
                }
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message}")
        } finally {
            if (fingerprint != null && registered) {
                WebSocketConnectionManager.unregister(fingerprint)
            }
            println("WebSocket connection closed: ${call.request.local.remoteHost} (FP: $fingerprint)")
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
    val myIdentity = deviceConfig.identityHash.takeIf { it.isNotBlank() }

    val devices = WebSocketConnectionManager.trustedFingerprints()
        .filterNot { it == requesterFingerprint }
        .mapNotNull { fp ->
            val identity = WebSocketConnectionManager.holderOf(fp)?.identityToken
            val sameAccount = (mySub != null && identity == mySub) || (myIdentity != null && identity == myIdentity)
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
    val autoTrusted = (identity != null && (identity == deviceConfig.googleSub || identity == deviceConfig.identityHash))
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
