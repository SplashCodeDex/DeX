package com.dexstudios.dex.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object PermissionManager {
    private val _requestNearby = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNearby = _requestNearby.asSharedFlow()

    private val _requestNotifications = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestNotifications = _requestNotifications.asSharedFlow()

    private val _requestFolder = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestFolder = _requestFolder.asSharedFlow()

    private val _nearbyPermanentlyDenied = MutableStateFlow(false)
    val nearbyPermanentlyDenied = _nearbyPermanentlyDenied.asStateFlow()

    private val _notificationsPermanentlyDenied = MutableStateFlow(false)
    val notificationsPermanentlyDenied = _notificationsPermanentlyDenied.asStateFlow()

    fun triggerNearby() { _requestNearby.tryEmit(Unit) }
    fun triggerNotifications() { _requestNotifications.tryEmit(Unit) }
    fun triggerFolder() { _requestFolder.tryEmit(Unit) }

    fun setNearbyPermanentlyDenied(denied: Boolean) {
        _nearbyPermanentlyDenied.value = denied
    }

    fun setNotificationsPermanentlyDenied(denied: Boolean) {
        _notificationsPermanentlyDenied.value = denied
    }
}
