package com.dexstudios.dex.core.network

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

object TransferState {
    val pendingPrompts = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
}

data class PairRequestInfo(
    val alias: String,
    val fingerprint: String,
    val pin: String,
    val deferred: CompletableDeferred<String>,
    // Monotonic (SystemClock.elapsedRealtime) deadline of the pairing prompt, set when the
    // PC pushes it. The PIN dialog counts down to this so the user sees the true remaining
    // time even when the dialog opens late from a notification.
    val deadlineElapsedMs: Long = 0L,
)
