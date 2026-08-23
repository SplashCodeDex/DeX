package com.dexstudios.dex.auth

import com.dexstudios.dex.core.network.PairRequestInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AuthState {
    private val _pairedFingerprints = MutableStateFlow<Set<String>>(emptySet())
    val pairedFingerprints: StateFlow<Set<String>> = _pairedFingerprints.asStateFlow()

    private val _pairedTokens = MutableStateFlow<Map<String, String>>(emptyMap())
    val pairedTokens: StateFlow<Map<String, String>> = _pairedTokens.asStateFlow()

    private val _pairedTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val pairedTimes: StateFlow<Map<String, Long>> = _pairedTimes.asStateFlow()

    private val _incomingPairRequest = MutableStateFlow<PairRequestInfo?>(null)
    val incomingPairRequest: StateFlow<PairRequestInfo?> = _incomingPairRequest.asStateFlow()

    fun updateFingerprints(fingerprints: Set<String>) {
        _pairedFingerprints.value = fingerprints
    }

    fun updateTokens(tokens: Map<String, String>) {
        _pairedTokens.value = tokens
    }

    fun updateTimes(times: Map<String, Long>) {
        _pairedTimes.value = times
    }

    fun updateIncomingPairRequest(request: PairRequestInfo?) {
        _incomingPairRequest.value = request
    }
}
