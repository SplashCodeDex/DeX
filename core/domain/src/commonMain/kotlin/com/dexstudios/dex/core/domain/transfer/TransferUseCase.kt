package com.dexstudios.dex.core.domain.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The transfer session registry use case (plan 027): one live dashboard entry per
 * transfer session, created at offer/prepare time, updated on progress, completed on
 * settlement, and removed after the legacy 6-second linger.
 *
 * Semantics preserved verbatim from the legacy TransferStateMonitor (core/network):
 * entries keyed by sessionId; progress reports REPLACE (never merge) fields; failure
 * removes immediately; completion lingers [SESSION_LINGER_MS] then disappears.
 *
 * Byte delivery (QUIC/HTTPS/HTTP) and history persistence live behind ports; the
 * network adapters keep their streaming loops and drive this registry.
 */
class TransferUseCase(
    // Owns the linger delays; desktop wires an IO scope, tests a virtual-time scope.
    private val lingerScope: CoroutineScope? = null,
) {
    private val _sessions = MutableStateFlow<Map<String, TransferSession>>(emptyMap())
    val sessions: StateFlow<Map<String, TransferSession>> = _sessions

    companion object {
        /** Dashboard keeps a settled transfer visible for 6s — legacy behavior. */
        const val SESSION_LINGER_MS = 6_000L

        /** History direction values (TransferHistory contract). */
        const val DIRECTION_RECEIVED = "received"
        const val DIRECTION_SENT = "sent"

        /** History status values (TransferHistory contract). */
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
    }

    /** Registers a session at offer/prepare time with zero progress. */
    fun registerSession(sessionId: String, senderAlias: String, totalFiles: Int, totalBytes: Long = 0L) {
        if (sessionId.isBlank()) return
        _sessions.value = _sessions.value + (
            sessionId to TransferSession(
                sessionId = sessionId,
                senderAlias = senderAlias,
                totalFiles = totalFiles,
                totalBytes = totalBytes,
            )
            )
    }

    /** Live progress report — replaces the entry's progress fields verbatim. */
    fun reportProgress(sessionId: String, progress: TransferProgress) {
        val current = _sessions.value[sessionId] ?: return
        _sessions.value = _sessions.value + (
            sessionId to current.copy(
                filesReceived = progress.filesDone,
                totalFiles = progress.totalFiles,
                bytesReceived = progress.bytesTransferred,
                totalBytes = progress.totalBytes,
                speedBps = progress.speedBps,
                etaSeconds = progress.etaSeconds,
                currentFileName = progress.currentFileName,
                isComplete = false,
            )
            )
    }

    /**
     * Marks a session complete WITHOUT scheduling removal — the caller owns the entry's
     * lifetime (desktop callers linger 6s then [removeSession] explicitly, preserving
     * the legacy monitor contract where completion never auto-removed).
     */
    fun markComplete(sessionId: String, filesReceived: Int, totalFiles: Int) {
        val current = _sessions.value[sessionId] ?: return
        _sessions.value = _sessions.value + (
            sessionId to current.copy(
                filesReceived = filesReceived,
                totalFiles = totalFiles,
                isComplete = true,
            )
            )
    }

    /** Marks a session complete and schedules the linger removal (future-peer default). */
    fun completeSession(sessionId: String, filesReceived: Int, totalFiles: Int) {
        markComplete(sessionId, filesReceived, totalFiles)
        lingerScope?.launch {
            delay(SESSION_LINGER_MS)
            removeSession(sessionId)
        }
    }

    /**
     * Removes a session immediately — failure paths (a failed upload must not leave a
     * phantom transfer on the dashboard) and explicit cancellation.
     */
    fun removeSession(sessionId: String) {
        _sessions.value = _sessions.value - sessionId
    }

    fun session(sessionId: String): TransferSession? = _sessions.value[sessionId]
}
