package com.dexstudios.dex.core.network.server.routes

import co.touchlab.kermit.Logger
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceManager
import com.dexstudios.dex.core.network.EndpointInfoDto
import com.dexstudios.dex.core.network.IMirrorEngine
import com.dexstudios.dex.core.network.PausedFeatures
import com.dexstudios.dex.core.network.RosterDeviceDto
import com.dexstudios.dex.core.network.RosterDto
import com.dexstudios.dex.core.network.server.DexRequestStore
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.network.services.PublicAddressService
import com.dexstudios.dex.core.network.services.RelayService
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
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
 *
 * The matching logic itself lives in [BearerTrust] so every bearer-consuming surface
 * (HTTP routes, clipboard push) shares one implementation.
 */
private fun resolveHandshakeTrust(fingerprint: String, token: String?): Pair<Boolean, String?> = com.dexstudios.dex.core.network.server.BearerTrust.resolveHandshakeTrust(fingerprint, token)

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

/**
 * Inbound `/ws` control-plane gate.
 *
 * Handshakes are accepted BEFORE trust is proven (that is what makes pairing possible), so
 * every inbound message that causes a LOCAL side effect must be checked against the session's
 * LIVE trust state — not the boolean captured at handshake time, which is false for a session
 * that proved trust later through the PIN or the same-account identity proof.
 *
 * Classification (keep this list honest when adding a message type):
 * - Allowed for untrusted sessions — the pairing/identity handshake itself: `pair-request`,
 *   `pair-response` and `pin-digit-entered` (both bound to the pending offer inside
 *   [com.dexstudios.dex.core.domain.pairing.PairingEngine]), `identity-proof`, `trust-check`
 *   (the peer only learns its own trust state) and `unpair` (a peer may only revoke ITSELF).
 * - Trusted sessions only — anything that mutates desktop state, settles requests we opened,
 *   or moves data toward a third device: `set-clipboard`, `pull-progress`, `telemetry`, the
 *   `*-reply` family, `device-roster`, `peer-endpoint`, `relay-transfer`
 *   (`resolve-endpoint` gates inside its own handler).
 * - Paused — screen mirroring messages and binary frames are dropped at the boundary; see
 *   [PausedFeatures.SCREEN_MIRROR].
 */
private fun isTrustedSession(fingerprint: String?): Boolean = fingerprint != null && WebSocketConnectionManager.isTrusted(fingerprint)

/** Single, greppable log line for a gated message so drops stay diagnosable, never silent. */
private fun logIgnored(type: String, fingerprint: String?) {
    Logger.i("Ignored $type from untrusted/unknown session FP: $fingerprint")
}

fun Route.webSocketRoutes(pairingEngine: com.dexstudios.dex.core.domain.pairing.PairingEngine, mirrorEngine: IMirrorEngine, publicAddressService: PublicAddressService? = null) {
    webSocket("/ws") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        val token = call.request.queryParameters["token"]
        val alias = call.request.queryParameters["alias"]
        val deviceType = call.request.queryParameters["deviceType"] ?: "mobile"
        val deviceModel = call.request.queryParameters["deviceModel"] ?: "Phone"

        var registered = false
        val pullSpeedCalc = com.dexstudios.dex.core.network.TransferSpeedCalculator()
        // Per-connection nonce for the same-account proof-of-possession exchange. Handler
        // scoped: no shared map, nothing for another peer to read or replay.
        var identityNonce: ByteArray? = null
        // One-time latch so paused mirror traffic cannot flood the log while it is dropped.
        var pausedTrafficLogged = false
        if (!fingerprint.isNullOrBlank()) {
            val (trusted, identityToken) = resolveHandshakeTrust(fingerprint, token)
            // Hijack guard: an active session for this fingerprint is never silently replaced
            registered = WebSocketConnectionManager.register(fingerprint, this, trusted, identityToken)
            if (!registered) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "fingerprint already connected"))
                return@webSocket
            }

            val discovery = org.koin.core.context.GlobalContext.get().getOrNull<com.dexstudios.dex.core.network.DiscoveryEngine>()
            if (discovery != null) {
                val existing = discovery.devices.value[fingerprint]
                val remoteIp = call.request.local.remoteHost
                if (existing == null) {
                    val newDevice = com.dexstudios.dex.core.network.DiscoveredDevice(
                        ip = remoteIp,
                        info = com.dexstudios.dex.core.network.RegisterDto(
                            alias = alias?.ifBlank { "Phone" } ?: "Phone",
                            version = "2.0",
                            deviceModel = deviceModel,
                            deviceType = deviceType,
                            fingerprint = fingerprint,
                            port = com.dexstudios.dex.core.network.DeXPorts.HTTPS,
                            protocol = "https",
                            download = false,
                        ),
                        viaWan = false,
                    )
                    discovery.addDevice(newDevice)
                } else if (!alias.isNullOrBlank() && existing.info.alias.isBlank()) {
                    discovery.addDevice(existing.copy(info = existing.info.copy(alias = alias)))
                }
            }

            Logger.i("WebSocket connection established: ${call.request.local.remoteHost} (FP: $fingerprint, trusted: $trusted)")

            // Same-account phones need our WAN address to reach us without port-forwarding know-how
            if (trusted && publicAddressService != null) {
                launch {
                    val address = runCatching { publicAddressService.publicAddress() }.getOrNull()
                    if (!address.isNullOrBlank()) {
                        WebSocketConnectionManager.sendRequest(
                            fingerprint,
                            ProtocolEnvelope.envelopeOf(MessageTypes.PUBLIC_ADDRESS) {
                                put(FieldNames.ADDRESS, address)
                            },
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
                        ProtocolEnvelope.envelopeOf(MessageTypes.IDENTITY_CHALLENGE) {
                            put(FieldNames.NONCE, java.util.Base64.getEncoder().encodeToString(nonce))
                        },
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
                        val type = ProtocolEnvelope.decodeType(text)
                        val dataObj = ProtocolEnvelope.decodeData(text)

                        when (type) {
                            MessageTypes.LIST_SHARED_FOLDERS_REPLY,
                            MessageTypes.BROWSE_REPLY,
                            MessageTypes.GRANT_SHARED_FOLDER_REPLY,
                            MessageTypes.GRANT_REPLY,
                            MessageTypes.PULL_REPLY,
                            MessageTypes.REPLY,
                            -> {
                                // Replies settle requests WE opened against a peer we already
                                // trust; an untrusted session must never be able to resolve them
                                // (that would let any LAN peer answer another device's request,
                                // e.g. forge a browse-reply or a pull-reply).
                                if (!isTrustedSession(fingerprint)) {
                                    logIgnored(type, fingerprint)
                                } else {
                                    val reqId = dataObj?.get(FieldNames.REQUEST_ID)?.jsonPrimitive?.content
                                    if (reqId != null) {
                                        DexRequestStore.completeRequest(reqId, dataObj)
                                    }
                                }
                            }

                            MessageTypes.PAIR_REQUEST -> {
                                if (fingerprint != null) {
                                    // Pairing is interactive consent, not a passive alert: it must
                                    // work regardless of Do Not Disturb. DND mutes the tray
                                    // notification (DesktopPlatformEngine), never the flow itself.
                                    val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
                                    val ip = call.request.local.remoteHost
                                    // Resolve the peer's advertised alias so the pairing panel can
                                    // title itself "Pairing with {alias}" without a second lookup.
                                    val peerAlias = org.koin.core.context.GlobalContext.get()
                                        .getOrNull<com.dexstudios.dex.core.network.DiscoveryEngine>()
                                        ?.devices?.value?.get(fingerprint)?.info?.alias.orEmpty()
                                    val pin = pairingEngine.handleInboundPairingRequest(ip, fingerprint, peerAlias)
                                    val promptJson = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_PROMPT) {
                                        put(FieldNames.PIN, pin)
                                        put(FieldNames.ALIAS, deviceConfig.alias.ifBlank { "DeX Desktop" })
                                        put(FieldNames.FINGERPRINT, deviceConfig.fingerprint)
                                    }
                                    WebSocketConnectionManager.sendRequest(fingerprint, promptJson)
                                }
                            }

                            MessageTypes.PAIR_RESPONSE -> {
                                val accepted = dataObj?.get(FieldNames.ACCEPTED)?.jsonPrimitive?.content?.toBoolean() == true
                                val claimedPin = dataObj?.get(FieldNames.PIN)?.jsonPrimitive?.contentOrNull
                                val verifiedByPin = accepted && fingerprint != null &&
                                    !claimedPin.isNullOrBlank() &&
                                    pairingEngine.verifyInboundPin(fingerprint, claimedPin)
                                when {
                                    // Peer proved knowledge of the displayed PIN: grant trust,
                                    // persist BOTH sides (we store a freshly minted pairing
                                    // token and hand it back so the peer can store it too),
                                    // and upgrade the session so prompts flow immediately.
                                    verifiedByPin -> {
                                        val pairToken = grantPairing(fingerprint)
                                        WebSocketConnectionManager.sendRequest(
                                            fingerprint,
                                            ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_ACCEPTED) {
                                                put(FieldNames.TOKEN, pairToken)
                                                put(
                                                    FieldNames.FINGERPRINT,
                                                    org.koin.core.context.GlobalContext.get()
                                                        .get<com.dexstudios.dex.core.network.DeviceConfig>().fingerprint,
                                                )
                                            },
                                        )
                                        pairingEngine.handlePairResponse(fingerprint, true)
                                    }

                                    // Peer explicitly rejected the pairing offer
                                    !accepted -> {
                                        pairingEngine.handlePairResponse(fingerprint, false)
                                    }

                                    // Trust assertion without PIN proof or with unverified PIN is never persisted;
                                    // the desktop user can still grant access manually via the pairing panel,
                                    // unless maximum failed attempts locked out the offer.
                                    else -> {
                                        Logger.i("Rejected automatic pair-response from $fingerprint: PIN not proven")
                                    }
                                }
                            }

                            /*
                             * Answer to our identity-challenge: HMAC(nonce, googleSub) proves the
                             * peer holds our Google account ID without it ever crossing the wire.
                             * Session-scoped auto-trust; pairing persistence stays explicit.
                             */
                            MessageTypes.IDENTITY_PROOF -> {
                                val mac = dataObj?.get(FieldNames.MAC)?.jsonPrimitive?.contentOrNull
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
                            MessageTypes.UNPAIR -> {
                                if (fingerprint != null) {
                                    DeviceManager.removePairedFingerprint(fingerprint)
                                    WebSocketConnectionManager.markUntrusted(fingerprint)
                                    pairingEngine.reset()
                                    Logger.i("Peer $fingerprint revoked its pairing; local trust removed")
                                }
                            }

                            MessageTypes.PIN_DIGIT_ENTERED -> {
                                // The binding is enforced INSIDE the engine: only the peer that
                                // owns the pending offer may drive the PIN panel, so an
                                // unrelated session cannot spoof or blank the digit counter.
                                val count = dataObj?.get(FieldNames.DIGIT_COUNT)?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                pairingEngine.handlePinDigitEntered(fingerprint, count)
                            }

                            MessageTypes.PULL_PROGRESS -> {
                                // Pull progress is dashboard state for the peer we are pulling
                                // from; an untrusted session must not be able to rewrite it.
                                if (!isTrustedSession(fingerprint)) {
                                    logIgnored(type, fingerprint)
                                    return@consumeEach
                                }
                                val reqId = dataObj?.get(FieldNames.REQUEST_ID)?.jsonPrimitive?.content
                                if (reqId != null) {
                                    val state = dataObj[FieldNames.STATE]?.jsonPrimitive?.contentOrNull ?: FieldNames.STATE_RUNNING
                                    val doneFiles = dataObj[FieldNames.DONE_FILES]?.jsonPrimitive?.intOrNull ?: 0
                                    val totalFiles = dataObj[FieldNames.TOTAL_FILES]?.jsonPrimitive?.intOrNull ?: 0
                                    val sentBytes = dataObj[FieldNames.SENT_BYTES]?.jsonPrimitive?.longOrNull ?: 0L
                                    val totalBytes = dataObj[FieldNames.TOTAL_BYTES]?.jsonPrimitive?.longOrNull ?: 0L
                                    val currentFile = dataObj[FieldNames.CURRENT_FILE]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    val speedSample = pullSpeedCalc.sample(sentBytes, totalBytes)
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
                                            speedBps = speedSample.speedBps,
                                            etaSeconds = speedSample.etaSeconds,
                                            isPulling = state == FieldNames.STATE_RUNNING,
                                            isDone = state == FieldNames.STATE_DONE,
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
                            MessageTypes.DEVICE_ROSTER -> if (isTrustedSession(fingerprint)) {
                                handleDeviceRosterRequest(fingerprint)
                            } else {
                                logIgnored(type, fingerprint)
                            }

                            /* Punch rendezvous: answer with the target's last registered endpoint. */
                            MessageTypes.RESOLVE_ENDPOINT -> handleResolveEndpoint(fingerprint, dataObj)

                            /* Announce the sender's endpoint to the punch target. */
                            MessageTypes.PEER_ENDPOINT -> if (isTrustedSession(fingerprint)) {
                                handlePeerEndpointAnnounce(fingerprint, dataObj)
                            } else {
                                // An untrusted peer must not be able to inject a bogus punch
                                // endpoint into a trusted target's rendezvous.
                                logIgnored(type, fingerprint)
                            }

                            /*
                             * Trust desync check: the phone asserts what it believes; we answer with
                             * the authoritative view so it can downgrade stale trust.
                             */
                            MessageTypes.TRUST_CHECK -> handleTrustCheck(fingerprint, dataObj)

                            /*
                             * A->PC->B fallback: files already uploaded here under sessionId;
                             * verify arrival, host them, push a prompt to the target.
                             */
                            MessageTypes.RELAY_TRANSFER -> if (isTrustedSession(fingerprint)) {
                                handleRelayTransfer(fingerprint, dataObj)
                            } else {
                                // Relay orchestration moves files toward a THIRD device, so the
                                // requester must be a proven session — otherwise any LAN peer
                                // could have the PC push staged files at a trusted target.
                                logIgnored(type, fingerprint)
                            }

                            // Screen mirroring is PAUSED (see [PausedFeatures.SCREEN_MIRROR]):
                            // paused traffic is dropped at the boundary instead of being parsed
                            // into engine calls, so nothing can drive or stop the stream while
                            // the pause holds. The trust gate below is what the pause checklist
                            // requires before the feature is ever re-enabled.
                            MessageTypes.MIRROR_CONFIG, MessageTypes.MIRROR_STOP -> {
                                if (PausedFeatures.isPaused(PausedFeatures.SCREEN_MIRROR)) {
                                    if (!pausedTrafficLogged) {
                                        pausedTrafficLogged = true
                                        Logger.i("Dropping mirror traffic — ${PausedFeatures.pauseNotice(PausedFeatures.SCREEN_MIRROR)}")
                                    }
                                } else if (!isTrustedSession(fingerprint)) {
                                    logIgnored(type, fingerprint)
                                } else if (type == MessageTypes.MIRROR_CONFIG) {
                                    val width = dataObj?.get(FieldNames.WIDTH)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 720
                                    val height = dataObj?.get(FieldNames.HEIGHT)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1280
                                    val fps = dataObj?.get(FieldNames.FPS)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 15
                                    mirrorEngine.updateConfig(width, height, fps)
                                    Logger.i("Mirror stream config updated: ${width}x$height @ ${fps}fps")
                                } else {
                                    mirrorEngine.stop()
                                    Logger.i("Mirror stream stopped by peer $fingerprint")
                                }
                            }

                            MessageTypes.TELEMETRY -> {
                                // Battery/Wi-Fi telemetry is discovery state attributed to a
                                // fingerprint, so it is only accepted from a proven session.
                                if (!isTrustedSession(fingerprint)) {
                                    logIgnored(type, fingerprint)
                                    return@consumeEach
                                }
                                val battery = dataObj?.get(FieldNames.BATTERY)?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val isCharging = dataObj?.get(FieldNames.IS_CHARGING)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                                val wifiSsid = dataObj?.get(FieldNames.WIFI_SSID)?.jsonPrimitive?.contentOrNull
                                if (fingerprint != null) {
                                    val discovery = org.koin.core.context.GlobalContext.get().getOrNull<com.dexstudios.dex.core.network.DiscoveryEngine>()
                                    discovery?.updateTelemetry(fingerprint, battery, isCharging, wifiSsid)
                                }
                            }

                            MessageTypes.SET_CLIPBOARD -> {
                                // The desktop system clipboard is a LOCAL side effect, so only a
                                // session that proved trust (paired token, same-account proof or
                                // PIN) may write to it. Untrusted sockets exist solely to run the
                                // pairing handshake — without this gate any LAN peer could
                                // replace the user's clipboard at will.
                                if (!isTrustedSession(fingerprint)) {
                                    logIgnored(type, fingerprint)
                                    return@consumeEach
                                }
                                val text = dataObj?.get(FieldNames.TEXT)?.jsonPrimitive?.contentOrNull
                                if (!text.isNullOrBlank()) {
                                    try {
                                        // Route through the shared domain use case (plan 029): the
                                        // write AND the echo-guard marking must both happen there,
                                        // or the AWT change listener bounces this text right back.
                                        com.dexstudios.dex.core.network.ClipboardSyncState.applyRemoteText(text)
                                        com.dexstudios.dex.core.network.ClipboardSyncState.emitReceived(text)
                                        Logger.i("Clipboard text synced via WebSocket from $fingerprint")
                                    } catch (e: Exception) {
                                        Logger.i("Failed to set desktop clipboard text from $fingerprint: ${e.message}")
                                    }
                                }
                            }

                            else -> {
                                Logger.i("Unhandled WS message type: $type from FP: $fingerprint")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.i("WebSocket handler error (${e::class.simpleName}): ${e.message}")
                    }
                } else if (frame is Frame.Binary) {
                    // Binary frames are the mirror stream's data plane, so they are paused with
                    // the rest of the feature: dropped BEFORE decoding (a paused feature must
                    // not cost CPU per frame, and the engine must never see unauthenticated
                    // input).
                    if (PausedFeatures.isPaused(PausedFeatures.SCREEN_MIRROR)) {
                        if (!pausedTrafficLogged) {
                            pausedTrafficLogged = true
                            Logger.i("Dropping mirror frames — ${PausedFeatures.pauseNotice(PausedFeatures.SCREEN_MIRROR)}")
                        }
                    } else if (!isTrustedSession(fingerprint)) {
                        logIgnored("binary-frame", fingerprint)
                    } else {
                        val bytes = frame.readBytes()
                        mirrorEngine.receiveFrame(bytes)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.i("WebSocket error: ${e.message}")
        } finally {
            if (fingerprint != null && registered) {
                // Ownership-checked: a delayed cleanup from a superseded connection must not
                // evict the newer connection that took this fingerprint's slot.
                WebSocketConnectionManager.unregister(fingerprint, this)
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

    val rosterEnvelope = buildJsonObject {
        put(FieldNames.TYPE, MessageTypes.DEVICE_ROSTER)
        put(FieldNames.DATA, Json.encodeToJsonElement(RosterDto.serializer(), RosterDto(devices)))
    }.toString()
    WebSocketConnectionManager.sendRequest(requesterFingerprint, rosterEnvelope)
}

/** Answers `resolve-endpoint` with `endpoint-info` carrying the target's registered punch endpoint. */
private suspend fun handleResolveEndpoint(callerFingerprint: String?, dataObj: JsonObject?) {
    if (callerFingerprint == null) return
    // Punch rendezvous data (other devices' ip:port) is only for proven sessions.
    if (!WebSocketConnectionManager.isTrusted(callerFingerprint)) return
    val targetFingerprint = dataObj?.get(FieldNames.TARGET_FINGERPRINT)?.jsonPrimitive?.contentOrNull ?: return

    val entry = getPunchEndpoint(targetFingerprint)
    val endpointEnvelope = buildJsonObject {
        put(FieldNames.TYPE, MessageTypes.ENDPOINT_INFO)
        put(
            FieldNames.DATA,
            Json.encodeToJsonElement(
                EndpointInfoDto.serializer(),
                EndpointInfoDto(
                    targetFingerprint = targetFingerprint,
                    ip = entry?.ip.orEmpty(),
                    port = entry?.port ?: 0,
                ),
            ),
        )
    }.toString()
    WebSocketConnectionManager.sendRequest(callerFingerprint, endpointEnvelope)
}

/** Forwards a sender's punch endpoint to the intended target so both sides race simultaneously. */
private suspend fun handlePeerEndpointAnnounce(senderFingerprint: String?, dataObj: JsonObject?) {
    if (senderFingerprint == null || dataObj == null) return
    val peerFingerprint = dataObj[FieldNames.PEER_FINGERPRINT]?.jsonPrimitive?.contentOrNull ?: return

    // The sender's original data object is forwarded verbatim inside a fresh envelope.
    val forwardEnvelope = buildJsonObject {
        put(FieldNames.TYPE, MessageTypes.PEER_ENDPOINT)
        put(FieldNames.DATA, dataObj)
    }.toString()
    WebSocketConnectionManager.sendToTrusted(peerFingerprint, forwardEnvelope)
}

/**
 * The phone reports its belief about trust for [its own] fingerprint; the PC answers with the
 * authoritative state. Auto-trusted identities are always trusted while signed in.
 */
private suspend fun handleTrustCheck(callerFingerprint: String?, dataObj: JsonObject?) {
    if (callerFingerprint == null) return
    val subjectFingerprint = dataObj?.get(FieldNames.FINGERPRINT)?.jsonPrimitive?.contentOrNull ?: callerFingerprint

    val koin = org.koin.core.context.GlobalContext.get()
    val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()
    val identity = WebSocketConnectionManager.holderOf(callerFingerprint)?.identityToken
    val autoTrusted = identity != null && identity == deviceConfig.googleSub
    val actuallyTrusted = autoTrusted || AuthState.pairedFingerprints.value.contains(subjectFingerprint)

    WebSocketConnectionManager.sendRequest(
        callerFingerprint,
        ProtocolEnvelope.envelopeOf(MessageTypes.TRUST_CHECK) {
            put(FieldNames.IS_TRUSTED, actuallyTrusted)
            put(FieldNames.FINGERPRINT, deviceConfig.fingerprint)
        },
    )
}

/**
 * Completes the phone-to-phone relay fallback: waits until the staged upload fully arrived,
 * then hosts those files and pushes a prepare-upload prompt at the final target. Replies
 * `relay-started` / `relay-error` to the requesting phone.
 */
private suspend fun handleRelayTransfer(requesterFingerprint: String?, dataObj: JsonObject?) {
    if (requesterFingerprint == null || dataObj == null) return
    val targetFingerprint = dataObj[FieldNames.TARGET_FINGERPRINT]?.jsonPrimitive?.contentOrNull
    val sessionId = dataObj[FieldNames.SESSION_ID]?.jsonPrimitive?.contentOrNull

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

private fun relayReplyJson(success: Boolean, sessionId: String?, targetFingerprint: String?): String =
    ProtocolEnvelope.envelopeOf(if (success) MessageTypes.RELAY_STARTED else MessageTypes.RELAY_ERROR) {
        put(FieldNames.SESSION_ID, sessionId ?: "")
        put(FieldNames.TARGET_FINGERPRINT, targetFingerprint ?: "")
    }
