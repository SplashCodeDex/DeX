package com.dexstudios.dex.core.network

class JvmMirrorEngine : IMirrorEngine {
    override var textSender: ((String) -> Unit)? = null
    override var frameSender: ((ByteArray) -> Unit)? = null

    override fun stop() {
        // Desktop is viewer-only in Phase 1; no active capture session to stop.
    }
}
