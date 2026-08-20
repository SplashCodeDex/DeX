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

class PairingEngine {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    var outboundSender: suspend (fingerprint: String, json: String) -> Boolean = { _, _ -> false }
    private val scope = CoroutineScope(Dispatchers.Default)

    fun initiatePairing(device: DiscoveredDevice) {
        _state.value = PairingState.QrPhase(device.ip, device.info.fingerprint)
        // If the Android phone is already connected and discovering the PC, it will send pair-request
        // when the user scans the QR code or clicks Connect.
    }

    fun handlePinDigitEntered(digitCount: Int) {
        val current = _state.value
        if (current is PairingState.QrPhase) {
            _state.value = PairingState.PinPhase(current.ip, current.fingerprint, "000000", digitCount)
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

    fun handlePairResponse(accepted: Boolean) {
        if (accepted) {
            _state.value = PairingState.Success
        } else {
            _state.value = PairingState.Error("Pairing rejected or timed out")
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
