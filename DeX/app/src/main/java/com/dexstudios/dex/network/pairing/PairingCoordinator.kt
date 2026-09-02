package com.dexstudios.dex.network.pairing

import android.content.Context
import android.os.SystemClock
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.DeviceManager
import com.dexstudios.dex.network.DexJson
import com.dexstudios.dex.network.HashUtils
import com.dexstudios.dex.network.NetConfig
import com.dexstudios.dex.network.NotificationHelper
import com.dexstudios.dex.network.PairRequestDto
import com.dexstudios.dex.network.PairRequestInfo
import com.dexstudios.dex.network.ProtocolKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coordinates pairing handshakes, race-condition resolution, PIN challenge lifecycle,
 * and zero-knowledge identity challenge proofs (Plan 024 Phase 3).
 */
class PairingCoordinator(
    private val deviceConfig: DeviceConfig,
    private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val sendMessage: (String) -> Unit
) {
    private val json = DexJson

    fun handlePairPrompt(dataElement: JsonElement) {
        val pairReq = json.decodeFromJsonElement<PairRequestDto>(dataElement)

        // Re-Pairing After Partial Forget (Auto-Accept)
        if (AuthState.pairedFingerprints.contains(pairReq.fingerprint)) {
            Timber.i("Device ${pairReq.fingerprint} is already paired locally. Auto-accepting pair prompt.")
            val responseMsg = ProtocolKeys.envelopeOf(ProtocolKeys.PAIR_RESPONSE) {
                put(ProtocolKeys.ACCEPTED, true)
            }
            sendMessage(responseMsg)
            return
        }

        if (AuthState.incomingPairRequest.value != null) {
            // Simultaneous Pairing Race Condition Tie-Breaker
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
            deadlineElapsedMs = SystemClock.elapsedRealtime() + NetConfig.PAIR_PROMPT_TIMEOUT_MS
        )
        AuthState.incomingPairRequest.value = info
        notificationHelper.showPairingRequestNotification(pairReq.alias)

        CoroutineScope(Dispatchers.Main).launch {
            val enteredPin = withTimeoutOrNull(NetConfig.PAIR_PROMPT_TIMEOUT_MS.milliseconds) { info.deferred.await() }
            AuthState.incomingPairRequest.value = null
            notificationHelper.cancelPairingNotification()

            val accepted = enteredPin != null && enteredPin == pairReq.pin
            if (accepted) {
                DeviceManager.savePairedFingerprint(pairReq.fingerprint)
                DeviceManager.savePairedAlias(pairReq.fingerprint, pairReq.alias)
                pairReq.token?.let { DeviceManager.savePairedToken(pairReq.fingerprint, it) }
                Timber.i("Pairing accepted with ${pairReq.alias}")
            } else {
                Timber.i("Pairing rejected or timed out with ${pairReq.alias}")
            }
            sendPairResponse(accepted, enteredPin)
        }
    }

    /**
     * The PC cancelled the pairing. Dismiss the PIN dialog immediately.
     */
    fun handlePairCancelled() {
        val pending = AuthState.incomingPairRequest.value ?: return
        AuthState.incomingPairRequest.value = null
        notificationHelper.cancelPairingNotification()
        pending.deferred.complete("")
    }

    /**
     * PIN pairing was granted by the PC. Persist both paired fingerprint and token.
     */
    fun handlePairAccepted(dataElement: JsonElement) {
        val obj = dataElement as? JsonObject ?: return
        val token = obj["token"]?.jsonPrimitive?.contentOrNull ?: return
        val pcFingerprint = obj["fingerprint"]?.jsonPrimitive?.contentOrNull
        if (!pcFingerprint.isNullOrBlank()) {
            DeviceManager.savePairedFingerprint(pcFingerprint)
            DeviceManager.savePairedToken(pcFingerprint, token)
            Timber.i("Pairing accepted by PC, stored pairing token for $pcFingerprint")
        }
    }

    /**
     * Same-account proof-of-possession: the PC challenges with a random nonce; we answer
     * HMAC(nonce, googleSub). Our googleSub never crosses the wire.
     */
    fun handleIdentityChallenge(dataElement: JsonElement) {
        val nonce = (dataElement as? JsonObject)?.get("nonce")?.jsonPrimitive?.contentOrNull ?: return
        val sub = deviceConfig.googleSub
        if (sub.isBlank() || nonce.isBlank()) return

        runCatching {
            val mac = HashUtils.hmacSha256Base64(sub, android.util.Base64.decode(nonce, android.util.Base64.NO_WRAP))
            sendMessage(
                ProtocolKeys.envelopeOf(ProtocolKeys.IDENTITY_PROOF) {
                    put(ProtocolKeys.MAC, mac)
                }
            )
        }.onFailure { Timber.e(it, "Failed to answer identity challenge") }
    }

    /**
     * Replies to the PC's pair-prompt.
     */
    fun sendPairResponse(accepted: Boolean, enteredPin: String? = null) {
        val payload = ProtocolKeys.envelopeOf(ProtocolKeys.PAIR_RESPONSE) {
            put(ProtocolKeys.ACCEPTED, accepted)
            if (!enteredPin.isNullOrEmpty()) {
                put(ProtocolKeys.PIN, enteredPin)
            }
        }
        sendMessage(payload)
    }

    /** Emits live keystroke telemetry so the desktop pairing UI can highlight matching digits in real time. */
    fun sendPinDigitEntered(digitCount: Int) {
        val payload = ProtocolKeys.envelopeOf(ProtocolKeys.PIN_DIGIT_ENTERED) {
            put(ProtocolKeys.DIGIT_COUNT, digitCount)
        }
        sendMessage(payload)
    }
}
