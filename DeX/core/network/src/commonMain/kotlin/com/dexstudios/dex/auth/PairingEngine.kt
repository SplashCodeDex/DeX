package com.dexstudios.dex.auth

import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.WebSocketEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface PairingState {
    data object Idle : PairingState

    data class QrPhase(
        val ip: String,
        val fingerprint: String
    ) : PairingState

    data class PinPhase(
        val ip: String,
        val fingerprint: String,
        val digitCount: Int,
        val isError: Boolean = false
    ) : PairingState

    data object Success : PairingState
    data class Error(val message: String) : PairingState
}

class PairingEngine(
    private val webSocketEngine: WebSocketEngine
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun initiatePairing(device: DiscoveredDevice) {
        _state.value = PairingState.QrPhase(device.ip, device.info.fingerprint)
        webSocketEngine.requestPairingWith(device) { accepted ->
            if (accepted) {
                _state.value = PairingState.Success
            } else {
                _state.value = PairingState.Error("Pairing rejected or timed out")
            }
        }
    }

    fun handlePinDigitEntered(digitCount: Int) {
        val current = _state.value
        if (current is PairingState.QrPhase) {
            _state.value = PairingState.PinPhase(current.ip, current.fingerprint, digitCount)
        } else if (current is PairingState.PinPhase) {
            _state.value = current.copy(digitCount = digitCount)
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
