package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.StateFlow

data class MirrorConfig(val width: Int, val height: Int, val fps: Int)

interface IMirrorEngine {
    val latestFrame: StateFlow<ByteArray?>
    val config: StateFlow<MirrorConfig?>
    var textSender: ((String) -> Unit)?
    var frameSender: ((ByteArray) -> Unit)?
    fun updateConfig(width: Int, height: Int, fps: Int)
    fun stop()
    fun receiveFrame(frame: ByteArray)
}
