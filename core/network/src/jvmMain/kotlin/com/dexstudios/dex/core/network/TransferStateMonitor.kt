package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TransferSessionInfo(val sessionId: String, val senderAlias: String, val totalFiles: Int, val filesReceived: Int, val isComplete: Boolean = false)

object TransferStateMonitor {
    private val _activeTransfers = MutableStateFlow<Map<String, TransferSessionInfo>>(emptyMap())
    val activeTransfers = _activeTransfers.asStateFlow()

    fun updateIncomingProgress(sessionId: String, alias: String, totalFiles: Int, filesReceived: Int, isComplete: Boolean = false) {
        _activeTransfers.update { current ->
            val updated = current.toMutableMap()
            updated[sessionId] = TransferSessionInfo(sessionId, alias, totalFiles, filesReceived, isComplete)
            updated
        }
    }

    fun removeSession(sessionId: String) {
        _activeTransfers.update { current ->
            val updated = current.toMutableMap()
            updated.remove(sessionId)
            updated
        }
    }
}
