package com.dexstudios.dex.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PermissionManager {
    private val _requestNearby = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNearby = _requestNearby.asSharedFlow()

    private val _requestNotifications = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNotifications = _requestNotifications.asSharedFlow()

    private val _requestFolder = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestFolder = _requestFolder.asSharedFlow()

    fun triggerNearby() { _requestNearby.tryEmit(Unit) }
    fun triggerNotifications() { _requestNotifications.tryEmit(Unit) }
    fun triggerFolder() { _requestFolder.tryEmit(Unit) }
}
