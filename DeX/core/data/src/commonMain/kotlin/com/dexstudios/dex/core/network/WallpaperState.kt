package com.dexstudios.dex.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WallpaperState {
    private val _revision = MutableStateFlow(com.dexstudios.dex.core.network.HashUtils.currentTimeMillis())
    val revision = _revision.asStateFlow()

    fun notifyUpdated() {
        _revision.value = com.dexstudios.dex.core.network.HashUtils.currentTimeMillis()
    }
}



