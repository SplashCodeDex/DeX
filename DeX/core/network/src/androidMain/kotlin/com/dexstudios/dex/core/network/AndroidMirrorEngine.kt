package com.dexstudios.dex.core.network

class AndroidMirrorEngine : IMirrorEngine {
    override var textSender: ((String) -> Unit)?
        get() = MirrorSession.textSender
        set(value) { MirrorSession.textSender = value }

    override var frameSender: ((ByteArray) -> Unit)?
        get() = MirrorSession.frameSender
        set(value) { MirrorSession.frameSender = value }

    override fun stop() {
        MirrorSession.stop()
    }
}
