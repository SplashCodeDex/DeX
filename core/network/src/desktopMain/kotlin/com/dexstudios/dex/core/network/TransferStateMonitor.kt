package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TransferSessionInfo(
    val sessionId: String,
    val senderAlias: String,
    val totalFiles: Int,
    val filesReceived: Int,
    val isComplete: Boolean = false,
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long? = null,
    val currentFileName: String = "",
)

object TransferStateMonitor {
    private val _activeTransfers = MutableStateFlow<Map<String, TransferSessionInfo>>(emptyMap())
    val activeTransfers = _activeTransfers.asStateFlow()

    fun updateIncomingProgress(
        sessionId: String,
        alias: String,
        totalFiles: Int,
        filesReceived: Int,
        isComplete: Boolean = false,
        bytesReceived: Long = 0L,
        totalBytes: Long = 0L,
        speedBps: Long = 0L,
        etaSeconds: Long? = null,
        currentFileName: String = "",
    ) {
        _activeTransfers.update { current ->
            val updated = current.toMutableMap()
            updated[sessionId] = TransferSessionInfo(
                sessionId = sessionId,
                senderAlias = alias,
                totalFiles = totalFiles,
                filesReceived = filesReceived,
                isComplete = isComplete,
                bytesReceived = bytesReceived,
                totalBytes = totalBytes,
                speedBps = speedBps,
                etaSeconds = etaSeconds,
                currentFileName = currentFileName,
            )
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
