package com.dexstudios.dex.core.network

object ClipboardHook {
    var onRemoteTextReceived: ((String) -> Unit)? = null
}
