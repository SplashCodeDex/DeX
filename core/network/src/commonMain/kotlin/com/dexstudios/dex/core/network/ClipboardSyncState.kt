package com.dexstudios.dex.core.network
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
}
