package com.dexstudios.dex.core.network

interface IMirrorEngine {
    var textSender: ((String) -> Unit)?
    var frameSender: ((ByteArray) -> Unit)?
    fun stop()
}
