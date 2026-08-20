package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JvmMirrorEngine : IMirrorEngine {
    override var textSender: ((String) -> Unit)? = null
    override var frameSender: ((ByteArray) -> Unit)? = null

    private val _latestFrame = MutableStateFlow<ByteArray?>(null)
    override val latestFrame: StateFlow<ByteArray?> = _latestFrame.asStateFlow()

    override fun stop() {
        _latestFrame.value = null
    }

    override fun receiveFrame(frame: ByteArray) {
        _latestFrame.value = frame
    }
}
