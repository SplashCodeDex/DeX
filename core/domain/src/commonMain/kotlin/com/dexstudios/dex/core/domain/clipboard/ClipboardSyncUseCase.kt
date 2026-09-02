package com.dexstudios.dex.core.domain.clipboard

/**
 * Payload the clipboard sync moves between peers. Exactly one lane is active:
 * text XOR image (the wire envelope carries `text` + optional `imageBase64`).
 */
sealed interface ClipboardPayload {
    data class Text(val value: String) : ClipboardPayload

    data class Image(val mimeType: String, val base64Png: String) : ClipboardPayload
}

/** Platform clipboard access (AWT on desktop, system service on Android, UIPasteboard on iOS). */
interface ClipboardAccess {
    /** The current clipboard content, or null when empty/unsupported. */
    suspend fun read(): ClipboardPayload?

    /** Writes [payload] to the system clipboard. */
    suspend fun write(payload: ClipboardPayload)
}

/** Delivery port: pushes an outbound sync payload to the connected peer(s). */
interface ClipboardSender {
    /**
     * Delivers the canonical `set-clipboard` envelope; returns false when no peer is
     * reachable (callers decide on fallback channels).
     */
    suspend fun send(payload: ClipboardPayload): Boolean
}

/**
 * The clipboard sync use case (plan 029): owns the echo guard, the enable policy, and
 * the local-change decision — the platform-neutral half of clipboard sync. Peers
 * (desktop, Android, iOS) wire their [ClipboardAccess]/[ClipboardSender] adapters and
 * reuse this logic verbatim.
 *
 * ECHO GUARD (the bug this class exists to prevent): when a remote peer's clipboard
 * arrives and we write it locally, the platform's clipboard-change event fires. Without
 * remembering what we received, we would broadcast it BACK — an infinite two-device
 * copy loop. [onLocalClipboardChanged] therefore suppresses sends whose content hash
 * matches the last RECEIVED content.
 *
 * PRIVACY LAW (plan 031, restated): clipboard CONTENT is real-time P2P only — it never
 * enters the sync backend. This class has no sync dependency by design.
 */
class ClipboardSyncUseCase(
    private val access: ClipboardAccess,
    private val sender: ClipboardSender,
    // Enable policy injected as a value source so the UI toggle drives behavior live.
    private val enabled: () -> Boolean,
    // Content hashing injected so tests are deterministic (and platforms may optimize).
    private val hash: (String) -> String,
) {
    @Volatile
    private var lastReceivedHash: String? = null

    /**
     * Pushes the CURRENT local clipboard to the peer. The desktop calls this from its
     * clipboard-change listener; [payload] lets callers pass the content they already
     * read (avoiding a second clipboard access) or null to read via [access].
     *
     * @return true when a send happened (enabled + non-echo + delivered-or-fallback).
     */
    suspend fun pushLocalClipboard(payload: ClipboardPayload? = null): Boolean {
        if (!enabled()) return false
        val content = payload ?: access.read() ?: return false
        val contentHash = hash(content.representation())

        // Echo guard: this content arrived FROM a peer moments ago — never bounce it back.
        if (contentHash == lastReceivedHash) return false

        return sender.send(content)
    }

    /**
     * Applies an inbound peer clipboard locally and marks its hash so the resulting
     * local-change event does not re-broadcast (the loop-killer).
     */
    suspend fun applyRemoteClipboard(payload: ClipboardPayload) {
        val contentHash = hash(payload.representation())
        lastReceivedHash = contentHash
        access.write(payload)
    }

    /** Called when the platform reports the clipboard changed — the send decision point. */
    suspend fun onLocalClipboardChanged(): Boolean = pushLocalClipboard()

    /** Test/diagnostic seam: clears the echo-guard memory. */
    fun resetGuard() {
        lastReceivedHash = null
    }

    private fun ClipboardPayload.representation(): String = when (this) {
        is ClipboardPayload.Text -> value
        is ClipboardPayload.Image -> base64Png
    }
}
