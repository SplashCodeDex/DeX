package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.domain.transfer.TransferProgress
import com.dexstudios.dex.core.domain.transfer.TransferSession
import com.dexstudios.dex.core.domain.transfer.TransferUseCase
import kotlinx.coroutines.flow.StateFlow

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

/**
 * Desktop facade over the domain TransferUseCase (plan 027). The legacy public API is
 * preserved verbatim — every call site (routes, pull service, overlays) compiles and
 * behaves identically — while the session registry itself now lives in `core/domain`
 * where every ecosystem peer reuses it.
 *
 * A single process-wide instance backs this object; the shared domain use case is
 * created lazily on the IO scope and is also exposed for future direct consumers.
 */
object TransferStateMonitor {
    private val sharedScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    @Volatile
    private var fallback: TransferUseCase? = null

    /**
     * ONE process-wide registry: Koin's when the DI graph has it (the instance
     * NetworkModule registers — also wired into future peers), else a self-managed
     * fallback for tests/early startup/partial DI graphs. getOrNull — a partial Koin
     * (e.g. route tests registering only DeviceConfig) must never crash callers.
     */
    fun useCase(): TransferUseCase {
        org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<TransferUseCase>()?.let { return it }
        return fallback ?: synchronized(this) {
            fallback ?: TransferUseCase(lingerScope = sharedScope).also { fallback = it }
        }
    }

    /** Live sessions mapped back to the legacy session-info shape. */
    val activeTransfers: StateFlow<Map<String, TransferSessionInfo>> =
        object : kotlinx.coroutines.flow.StateFlow<Map<String, TransferSessionInfo>> {
            private val backing: StateFlow<Map<String, TransferSession>> get() = useCase().sessions

            override val value: Map<String, TransferSessionInfo>
                get() = backing.value.mapValues { it.value.toInfo() }

            override val replayCache: List<Map<String, TransferSessionInfo>>
                get() = listOf(value)

            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Map<String, TransferSessionInfo>>): Nothing {
                backing.collect { map -> collector.emit(map.mapValues { it.value.toInfo() }) }
            }
        }

    private fun TransferSession.toInfo() = TransferSessionInfo(
        sessionId = sessionId,
        senderAlias = senderAlias,
        totalFiles = totalFiles,
        filesReceived = filesReceived,
        isComplete = isComplete,
        bytesReceived = bytesReceived,
        totalBytes = totalBytes,
        speedBps = speedBps,
        etaSeconds = etaSeconds,
        currentFileName = currentFileName,
    )

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
        val uc = useCase()
        // Legacy semantics: every call creates-or-replaces the entry verbatim — including
        // the completion call, which wipes byte fields exactly like the original monitor
        // did (callers re-supply only what they have). NO auto-removal on complete:
        // removal is owned by the caller (ShareRoutes/DesktopPullService delay+remove,
        // overlay tests drive it explicitly).
        uc.registerSession(sessionId, alias, totalFiles, totalBytes)
        if (isComplete) {
            uc.reportProgress(
                sessionId,
                TransferProgress(
                    filesDone = filesReceived,
                    totalFiles = totalFiles,
                    bytesTransferred = bytesReceived,
                    totalBytes = totalBytes,
                    speedBps = speedBps,
                    etaSeconds = etaSeconds,
                    currentFileName = currentFileName,
                ),
            )
            uc.markComplete(sessionId, filesReceived, totalFiles)
        } else {
            uc.reportProgress(
                sessionId,
                TransferProgress(
                    filesDone = filesReceived,
                    totalFiles = totalFiles,
                    bytesTransferred = bytesReceived,
                    totalBytes = totalBytes,
                    speedBps = speedBps,
                    etaSeconds = etaSeconds,
                    currentFileName = currentFileName,
                ),
            )
        }
    }

    fun removeSession(sessionId: String) {
        useCase().removeSession(sessionId)
    }
}
