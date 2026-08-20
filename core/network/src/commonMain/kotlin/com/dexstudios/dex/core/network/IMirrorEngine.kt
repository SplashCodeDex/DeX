package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.StateFlow

interface IMirrorEngine {
    val latestFrame: StateFlow<ByteArray?>
    var textSender: ((String) -> Unit)?
    var frameSender: ((ByteArray) -> Unit)?
    fun stop()
    fun receiveFrame(frame: ByteArray)
}
