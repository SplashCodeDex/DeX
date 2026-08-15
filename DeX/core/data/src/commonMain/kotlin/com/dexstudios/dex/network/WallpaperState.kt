package com.dexstudios.dex.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WallpaperState {
    private val _revision = MutableStateFlow(com.dexstudios.dex.network.protocol.HashUtils.currentTimeMillis())
    val revision = _revision.asStateFlow()

    fun notifyUpdated() {
        _revision.value = com.dexstudios.dex.network.protocol.HashUtils.currentTimeMillis()
    }
}
