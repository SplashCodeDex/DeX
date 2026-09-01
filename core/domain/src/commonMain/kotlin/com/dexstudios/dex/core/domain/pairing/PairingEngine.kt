package com.dexstudios.dex.core.domain.pairing

import com.dexstudios.dex.core.network.HashUtils
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

sealed interface PairingState {
    data object Idle : PairingState

    data class QrPhase(
        val ip: String,
        val fingerprint: String,
        // Absolute wall-clock deadline; the panel countdown and the expiry sweep both honor it.
        val expiresAtMillis: Long = 0L,
        // Peer display name captured at click time so the panel can title itself
        // "Pairing with {alias}" without re-resolving discovery.
        val alias: String = "",
    ) : PairingState

    data class PinPhase(val ip: String, val fingerprint: String, val pinCode: String, val digitCount: Int, val isError: Boolean = false, val expiresAtMillis: Long = 0L, val alias: String = "") :
        PairingState

    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

/** Everything the domain engine needs to know about the device being paired. */
data class PairingTarget(val ip: String, val fingerprint: String, val alias: String)

class PairingEngine(
    // Injectable so tests can drive the accept/reject paths under virtual time.
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    // Injectable clock so PIN expiry logic is deterministically testable.
    private val nowMillis: () -> Long = HashUtils::currentTimeMillis,
    // Infrastructure port: mints + persists the per-device credential on a proven grant.
    private val grantStore: PairingGrantStore? = null,
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
     * Supplies OUR display alias for pair-prompt payloads (legacy WPF server put its own
     * alias in every prompt it pushed). Wired by the server assembly.
     */
    var deviceAliasProvider: (() -> String)? = null

    /**
     * Fired after a desktop-initiated PIN offer was delivered, mirroring the legacy Windows
     * toast "Enter PIN {pin} on {alias}". Wired by the server assembly to the platform
     * notification layer; DND policy lives there.
     */
    var pinOfferNotifier: suspend (pin: String, alias: String) -> Unit = { _, _ -> }

    /**
     * Persists a pairing and returns the credential stored for it. Defaults to the
     * [PairingGrantStore] port; tests override to stay off real storage.
     */
    internal var persistentGrant: suspend (fingerprint: String) -> String = { fingerprint ->
        requireNotNull(grantStore) { "grantStore must be wired for persistent grants" }.grant(fingerprint)
    }

    private var expiryJob: Job? = null

    fun initiatePairing(target: PairingTarget) {
        _state.value = PairingState.QrPhase(
            ip = target.ip,
            fingerprint = target.fingerprint,
            expiresAtMillis = nowMillis() + PIN_TTL_MS,
            alias = target.alias,
        )
        armExpiry(target.fingerprint)
        // If the Android phone is already connected and discovering the PC, it will send pair-request
        // when the user scans the QR code or clicks Connect.
    }

    /**
     * Desktop-initiated "PIN CODE" request — the port of the legacy WPF flow where tapping
     * the toggle POSTed /local/pair-initiate: the SERVER mints a fresh 5-digit PIN, pushes
     * `pair-prompt{pin, alias, fingerprint}` to the phone's live WebSocket session so its
     * entry dialog opens, and only then does this side flip to the digit view with a fresh
     * 60s TTL. Returns false (state untouched) when there is no QR offer to upgrade or the
     * prompt could not be delivered — an undeliverable PIN must never be displayed.
     */
    suspend fun requestPinForActiveDevice(): Boolean {
        val current = _state.value
        if (current !is PairingState.QrPhase) return false

        val pinCode = mintPin()
        val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_PROMPT) {
            put(FieldNames.PIN, pinCode)
            put(FieldNames.ALIAS, deviceAliasProvider?.invoke().orEmpty().ifBlank { "DeX Desktop" })
            put(FieldNames.FINGERPRINT, deviceFingerprintProvider?.invoke().orEmpty())
        }
        // Deliver FIRST, transition SECOND: a failed push leaves the panel in the reachable
        // QR view instead of advertising a PIN the phone can never type.
        if (!outboundSender(current.fingerprint, payload)) return false

        _state.value = PairingState.PinPhase(
            ip = current.ip,
            fingerprint = current.fingerprint,
            pinCode = pinCode,
            digitCount = 0,
            expiresAtMillis = nowMillis() + PIN_TTL_MS,
            alias = current.alias,
        )
        armExpiry(current.fingerprint)
        pinOfferNotifier(pinCode, current.alias)
        return true
    }

    /**
     * Back-switch to the QR view (legacy "QR CODE" toggle): cancels the pending PIN offer
     * LOCALLY — never unpairing existing trust — keeps the device context so "PIN CODE"
     * works again, and re-arms the idle-QR expiry exactly like Start-QrPhaseTimer did.
     */
    fun revertToQrPhase() {
        val current = _state.value
        if (current is PairingState.PinPhase) {
            scope.launch {
                val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_CANCELLED)
                outboundSender(current.fingerprint, payload)
            }
            _state.value = PairingState.QrPhase(
                ip = current.ip,
                fingerprint = current.fingerprint,
                expiresAtMillis = nowMillis() + PIN_TTL_MS,
                alias = current.alias,
            )
            armExpiry(current.fingerprint)
        }
    }

    fun handlePinDigitEntered(digitCount: Int) {
        val current = _state.value
        if (current is PairingState.PinPhase) {
            _state.value = current.copy(digitCount = digitCount.coerceIn(0, PIN_LENGTH))
        }
    }

    fun handleInboundPairingRequest(ip: String, fingerprint: String, alias: String = ""): String {
        // Concurrency model: one pairing offer at a time (last-wins). A superseded peer can
        // never gain trust from its stale offer — verifyInboundPin matches the exact
        // fingerprint AND honors the TTL — so overwriting is safe without a pending-map.
        val pinCode = mintPin()
        _state.value = PairingState.PinPhase(
            ip = ip,
            fingerprint = fingerprint,
            pinCode = pinCode,
            digitCount = 0,
            expiresAtMillis = nowMillis() + PIN_TTL_MS,
            alias = alias,
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
                    val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_ACCEPTED) {
                        put(FieldNames.TOKEN, pairToken)
                        put(FieldNames.FINGERPRINT, deviceFingerprintProvider?.invoke().orEmpty())
                    }
                    outboundSender(current.fingerprint, payload)
                } else {
                    val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_RESPONSE) {
                        put(FieldNames.ACCEPTED, true)
                    }
                    outboundSender(current.fingerprint, payload)
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
                val payload = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_CANCELLED)
                outboundSender(current.fingerprint, payload)
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

    companion object {
        /** Server-side pairing offers expire; the panel countdown mirrors this deadline. */
        const val PIN_TTL_SECONDS = 60

        private val PIN_TTL_MS = PIN_TTL_SECONDS * 1000L

        /**
         * Canonical PIN length. The legacy WPF server minted Random().Next(10000, 99999) —
         * five digits — and the phone's entry dialog enforces exactly five slots, so every
         * producer/consumer (engine, panels, dialogs, tests) shares this constant.
         */
        const val PIN_LENGTH = 5

        /** 5-digit range mirrors the legacy WPF server (Random().Next(10000, 99999)). */
        private fun mintPin(): String = (10000..99999).random().toString()
    }
}
