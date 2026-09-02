package com.dexstudios.dex.core.network
import com.dexstudios.dex.core.domain.clipboard.ClipboardPayload
import com.dexstudios.dex.core.domain.clipboard.ClipboardSyncUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class ClipboardEvent {
    data class Received(val text: String) : ClipboardEvent()
    data class Sent(val text: String) : ClipboardEvent()
}

object ClipboardSyncState {
    private val _events = MutableSharedFlow<ClipboardEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun emitReceived(text: String) {
        _events.tryEmit(ClipboardEvent.Received(text))
    }

    fun emitSent(text: String) {
        _events.tryEmit(ClipboardEvent.Sent(text))
    }

    /**
     * The ONE process-wide clipboard-sync use case (plan 029): owns the echo guard and
     * the send decision. Wired by the desktop assembly (NetworkModule.jvm — AWT access +
     * WS broadcast sender); the server receive path and the AWT change listener BOTH
     * consult this instance so inbound text never bounces back.
     */
    @Volatile
    var useCase: ClipboardSyncUseCase? = null

    /** Server receive path: apply inbound text through the shared use case. */
    suspend fun applyRemoteText(text: String) {
        useCase?.applyRemoteClipboard(ClipboardPayload.Text(text))
    }
}
