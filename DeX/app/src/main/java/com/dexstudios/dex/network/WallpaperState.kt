package com.dexstudios.dex.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WallpaperState {
    private val _revision = MutableStateFlow(System.currentTimeMillis())
    val revision = _revision.asStateFlow()

    fun notifyUpdated() {
        _revision.value = System.currentTimeMillis()
    }
}
