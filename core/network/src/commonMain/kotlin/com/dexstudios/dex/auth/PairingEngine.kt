package com.dexstudios.dex.auth

import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

sealed interface PairingState {
    data object Idle : PairingState

    data class QrPhase(
        val ip: String,
        val fingerprint: String
    ) : PairingState

    data class PinPhase(
        val ip: String,
        val fingerprint: String,
        val pinCode: String,
        val digitCount: Int,
        val isError: Boolean = false
    ) : PairingState

    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

class PairingEngine(
    // Injectable so tests can drive the accept/reject paths under virtual time.
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    var outboundSender: suspend (fingerprint: String, json: String) -> Boolean = { _, _ -> false }

    fun initiatePairing(device: DiscoveredDevice) {
        _state.value = PairingState.QrPhase(device.ip, device.info.fingerprint)
        // If the Android phone is already connected and discovering the PC, it will send pair-request
        // when the user scans the QR code or clicks Connect.
    }

    fun handlePinDigitEntered(digitCount: Int) {
        val current = _state.value
        if (current is PairingState.QrPhase) {
            // No PIN exists yet in this phase (the remote device is typing before its pair-request
            // reached us), so render the masked placeholder instead of fake digits.
            _state.value = PairingState.PinPhase(current.ip, current.fingerprint, "------", digitCount)
        } else if (current is PairingState.PinPhase) {
            _state.value = current.copy(digitCount = digitCount)
        }
    }

    fun handleInboundPairingRequest(ip: String, fingerprint: String): String {
        val pinCode = (100000..999999).random().toString()
        _state.value = PairingState.PinPhase(ip, fingerprint, pinCode, digitCount = 0)
        return pinCode
    }

    fun acceptInboundPairing(isOneTime: Boolean) {
        val current = _state.value
        if (current is PairingState.PinPhase) {
            scope.launch {
                if (!isOneTime) {
                    DeviceManager.savePairedFingerprint(current.fingerprint)
                }
                val payload = buildJsonObject {
                    put("type", "pair-response")
                    putJsonObject("data") {
                        put("accepted", true)
                    }
                }
                outboundSender(current.fingerprint, payload.toString())
                _state.value = PairingState.Success
            }
        }
    }

    fun rejectInboundPairing() {
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
     * the PIN generated for [fingerprint] by the currently active inbound pairing. A connected
     * peer that merely asserts accepted=true without proving knowledge of the displayed PIN
     * must never be persisted as trusted.
     */
    fun verifyInboundPin(fingerprint: String, pin: String): Boolean {
        val current = _state.value
        return current is PairingState.PinPhase &&
                current.fingerprint == fingerprint &&
                pin.isNotBlank() &&
                pin == current.pinCode
    }

    fun handlePairResponse(accepted: Boolean) {
        // Only a pairing still awaiting resolution may transition. Stray or duplicate
        // responses (e.g. arriving after the user already accepted locally) are ignored so
        // they can never flip Success back to Error.
        when (_state.value) {
            is PairingState.QrPhase, is PairingState.PinPhase ->
                _state.value = if (accepted) {
                    PairingState.Success
                } else {
                    PairingState.Error("Pairing rejected or timed out")
                }
            else -> Unit
        }
    }

    fun markPairingError() {
        val current = _state.value
        if (current is PairingState.PinPhase) {
            _state.value = current.copy(isError = true)
        }
    }

    fun reset() {
        _state.value = PairingState.Idle
    }
}
