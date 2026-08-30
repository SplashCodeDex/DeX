package com.dexstudios.dex.network

import android.content.Context
import androidx.core.content.edit
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

    private var appContext: Context? = null

    /**
     * Loads the persisted "permanently denied" flags. Called once from
     * [com.dexstudios.dex.DeXApplication.onCreate] so the flags survive process restarts:
     * without this, a user who tapped "Don't ask again" would silently lose the
     * "Open Settings" guidance every time the process was killed.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _nearbyPermanentlyDenied.value = prefs.getBoolean(KEY_NEARBY_DENIED, false)
        _notificationsPermanentlyDenied.value = prefs.getBoolean(KEY_NOTIFICATIONS_DENIED, false)
    }

    fun triggerNearby() { _requestNearby.tryEmit(Unit) }
    fun triggerNotifications() { _requestNotifications.tryEmit(Unit) }
    fun triggerFolder() { _requestFolder.tryEmit(Unit) }

    fun setNearbyPermanentlyDenied(denied: Boolean) {
        _nearbyPermanentlyDenied.value = denied
        persist(KEY_NEARBY_DENIED, denied)
    }

    fun setNotificationsPermanentlyDenied(denied: Boolean) {
        _notificationsPermanentlyDenied.value = denied
        persist(KEY_NOTIFICATIONS_DENIED, denied)
    }

    private fun persist(key: String, denied: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit {
            putBoolean(key, denied)
        }
    }

    private const val PREFS = "dex_permission_prefs"
    private const val KEY_NEARBY_DENIED = "nearby_permanently_denied"
    private const val KEY_NOTIFICATIONS_DENIED = "notifications_permanently_denied"
}
