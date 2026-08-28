package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JvmMirrorEngine : IMirrorEngine {
    override var textSender: ((String) -> Unit)? = null
    override var frameSender: ((ByteArray) -> Unit)? = null

    private val _latestFrame = MutableStateFlow<ByteArray?>(null)
    override val latestFrame: StateFlow<ByteArray?> = _latestFrame.asStateFlow()

    private val _config = MutableStateFlow<MirrorConfig?>(null)
    override val config: StateFlow<MirrorConfig?> = _config.asStateFlow()

    override fun updateConfig(width: Int, height: Int, fps: Int) {
        _config.value = MirrorConfig(width, height, fps)
    }

    override fun stop() {
        _latestFrame.value = null
        _config.value = null
    }

    override fun receiveFrame(frame: ByteArray) {
        _latestFrame.value = frame
    }
}
