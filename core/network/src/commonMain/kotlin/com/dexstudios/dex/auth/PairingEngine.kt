package com.dexstudios.dex.auth

import com.dexstudios.dex.core.network.DeviceManager
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.HashUtils
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

sealed interface PairingState {
    data object Idle : PairingState

    data class QrPhase(
        val ip: String,
        val fingerprint: String,
        // Absolute wall-clock deadline; the panel countdown and the expiry sweep both honor it.
        val expiresAtMillis: Long = 0L,
    ) : PairingState

    data class PinPhase(val ip: String, val fingerprint: String, val pinCode: String, val digitCount: Int, val isError: Boolean = false, val expiresAtMillis: Long = 0L) : PairingState

    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

class PairingEngine(
    // Injectable so tests can drive the accept/reject paths under virtual time.
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    // Injectable clock so PIN expiry logic is deterministically testable.
    private val nowMillis: () -> Long = ::getTimeMillis,
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    var outboundSender: suspend (fingerprint: String, json: String) -> Boolean = { _, _ -> false }

    /**
     * Supplies OUR fingerprint so pair-accepted payloads can tell the peer which local
     * identity the minted pairing token belongs to. Wired by the server assembly.
     */
    var deviceFingerprintProvider: (() -> String)? = null

    /**
     * Persists a pairing and returns the credential stored for it. Default mints a fresh
     * UUID token through [DeviceManager]; tests override to stay off real storage.
     */
    internal var persistentGrant: suspend (fingerprint: String) -> String = { fingerprint ->
        val pairToken = HashUtils.generateUUID()
        DeviceManager.savePairedFingerprint(fingerprint)
        DeviceManager.savePairedToken(fingerprint, pairToken)
        pairToken
    }

    private var expiryJob: Job? = null

    fun initiatePairing(device: DiscoveredDevice) {
        _state.value = PairingState.QrPhase(device.ip, device.info.fingerprint, nowMillis() + PIN_TTL_MS)
        armExpiry(device.info.fingerprint)
        // If the Android phone is already connected and discovering the PC, it will send pair-request
        // when the user scans the QR code or clicks Connect.
    }

    fun handlePinDigitEntered(digitCount: Int) {
        val current = _state.value
        if (current is PairingState.QrPhase) {
            // No PIN exists yet in this phase (the remote device is typing before its pair-request
            // reached us), so render the masked placeholder instead of fake digits.
            _state.value = PairingState.PinPhase(
                current.ip,
                current.fingerprint,
                "------",
                digitCount,
                expiresAtMillis = current.expiresAtMillis,
            )
            armExpiry(current.fingerprint)
        } else if (current is PairingState.PinPhase) {
            _state.value = current.copy(digitCount = digitCount)
        }
    }

    fun handleInboundPairingRequest(ip: String, fingerprint: String): String {
        // Concurrency model: one pairing offer at a time (last-wins). A superseded peer can
        // never gain trust from its stale offer — verifyInboundPin matches the exact
        // fingerprint AND honors the TTL — so overwriting is safe without a pending-map.
        val pinCode = (100000..999999).random().toString()
        _state.value = PairingState.PinPhase(
            ip,
            fingerprint,
            pinCode,
            digitCount = 0,
            expiresAtMillis = nowMillis() + PIN_TTL_MS,
        )
        armExpiry(fingerprint)
        return pinCode
    }

    fun acceptInboundPairing(isOneTime: Boolean) {
        val current = _state.value
        if (current is PairingState.PinPhase) {
            // Cancel the sweep synchronously so it cannot fire between the click and the reply.
            expiryJob?.cancel()
            scope.launch {
                if (!isOneTime) {
                    // Persisted pairing needs a shared credential: mint a per-device token,
                    // store it here, and hand it back in the reply so the peer can persist
                    // its side. Without this, PIN-paired devices could never re-authenticate.
                    val pairToken = persistentGrant(current.fingerprint)
                    val payload = buildJsonObject {
                        put("type", "pair-accepted")
                        putJsonObject("data") {
                            put("token", pairToken)
                            put("fingerprint", deviceFingerprintProvider?.invoke().orEmpty())
                        }
                    }
                    outboundSender(current.fingerprint, payload.toString())
                } else {
                    val payload = buildJsonObject {
                        put("type", "pair-response")
                        putJsonObject("data") {
                            put("accepted", true)
                        }
                    }
                    outboundSender(current.fingerprint, payload.toString())
                }
                _state.value = PairingState.Success
            }
        }
    }

    fun rejectInboundPairing() {
        expiryJob?.cancel()
        val current = _state.value
        if (current is PairingState.PinPhase) {
            scope.launch {
                val payload = buildJsonObject {
                    put("type", "pair-response")
                    putJsonObject("data") {
                        put("accepted", false)
                    }
                }
                outboundSender(current.fingerprint, payload.toString())
                reset()
            }
        } else {
            reset()
        }
    }

    /**
     * Server-side PIN proof for inbound pair-responses. Returns true only when [pin] matches
     * the PIN generated for [fingerprint] by the currently active, unexpired inbound pairing.
     * A connected peer that merely asserts accepted=true without proving knowledge of the
     * displayed PIN must never be persisted as trusted.
     */
    fun verifyInboundPin(fingerprint: String, pin: String): Boolean {
        val current = _state.value
        return current is PairingState.PinPhase &&
            current.fingerprint == fingerprint &&
            pin.isNotBlank() &&
            pin == current.pinCode &&
            current.expiresAtMillis > 0L &&
            nowMillis() <= current.expiresAtMillis
    }

    fun handlePairResponse(accepted: Boolean) {
        // Only a pairing still awaiting resolution may transition. Stray or duplicate
        // responses (e.g. arriving after the user already accepted locally) are ignored so
        // they can never flip Success back to Error.
        when (_state.value) {
            is PairingState.QrPhase, is PairingState.PinPhase -> {
                expiryJob?.cancel()
                _state.value = if (accepted) {
                    PairingState.Success
                } else {
                    PairingState.Error("Pairing rejected or timed out")
                }
            }

            else -> Unit
        }
    }

    fun reset() {
        expiryJob?.cancel()
        _state.value = PairingState.Idle
    }

    /**
     * Auto-expires an unresolved pairing offer once its TTL elapses. Covers BOTH phases:
     * QrPhase (nobody scanned/typed yet) and PinPhase (digits typed, offer unresolved);
     * a no-op if already resolved.
     */
    private fun armExpiry(fingerprint: String) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(PIN_TTL_MS)
            val current = _state.value
            val expiredPinOffer = current is PairingState.PinPhase &&
                !current.isError &&
                current.fingerprint == fingerprint
            val expiredQrOffer = current is PairingState.QrPhase && current.fingerprint == fingerprint
            if (expiredPinOffer || expiredQrOffer) {
                _state.value = PairingState.Error("Pairing timed out")
            }
        }
    }

    private companion object {
        /** Server-side pairing offers expire; the panel countdown mirrors this deadline. */
        const val PIN_TTL_MS = 60_000L
    }
}
