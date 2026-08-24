package com.dexstudios.dex.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

/** Combined profile snapshot — prevents triple-recomposition on Google sign-in. */
data class GoogleProfile(val name: String = "", val picture: String = "", val email: String = "")

class DeviceConfig(private val dataStore: DataStore<Preferences>, private val scope: CoroutineScope) : KoinComponent {

    companion object {
        val EMAIL_KEY = stringPreferencesKey("email")
        val FINGERPRINT_KEY = stringPreferencesKey("fingerprint")
        val IDENTITY_HASH_KEY = stringPreferencesKey("identity_hash")
        val PUBLIC_ADDRESS_KEY = stringPreferencesKey("public_address")
        val GOOGLE_NAME_KEY = stringPreferencesKey("google_name")
        val GOOGLE_PICTURE_KEY = stringPreferencesKey("google_picture")
        val GOOGLE_SUB_KEY = stringPreferencesKey("google_sub")
        val ALIAS_KEY = stringPreferencesKey("alias")
        val CLIPBOARD_SYNC_ENABLED_KEY = booleanPreferencesKey("clipboard_sync_enabled")
        val WIGGLE_ENABLED_KEY = booleanPreferencesKey("wiggle_enabled")
        val UPNP_ENABLED_KEY = booleanPreferencesKey("upnp_enabled")
    }

    private val _emailFlow = MutableStateFlow("")
    val emailFlow: StateFlow<String> = _emailFlow.asStateFlow()

    private val _aliasFlow = MutableStateFlow("")
    val aliasFlow: StateFlow<String> = _aliasFlow.asStateFlow()

    private val _clipboardSyncEnabledFlow = MutableStateFlow(true)
    val clipboardSyncEnabledFlow: StateFlow<Boolean> = _clipboardSyncEnabledFlow.asStateFlow()

    private val _wiggleEnabledFlow = MutableStateFlow(true)
    val wiggleEnabledFlow: StateFlow<Boolean> = _wiggleEnabledFlow.asStateFlow()

    private val _upnpEnabledFlow = MutableStateFlow(false)
    val upnpEnabledFlow: StateFlow<Boolean> = _upnpEnabledFlow.asStateFlow()

    private val _profileNameFlow = MutableStateFlow("")
    private val _profilePictureFlow = MutableStateFlow("")

    private val _googleSubFlow = MutableStateFlow("")
    val googleSubFlow: StateFlow<String> = _googleSubFlow.asStateFlow()

    private val _fingerprintFlow = MutableStateFlow("")
    private val _identityHashFlow = MutableStateFlow("")
    private val _publicAddressFlow = MutableStateFlow("")

    val googleProfileFlow: StateFlow<GoogleProfile> = combine(
        _profileNameFlow,
        _profilePictureFlow,
        _emailFlow,
    ) { name, picture, email ->
        GoogleProfile(name, picture, email)
    }.stateIn(scope, SharingStarted.Eagerly, GoogleProfile())

    val publicAddress: String
        get() = _publicAddressFlow.value

    fun setPublicAddress(value: String) {
        _publicAddressFlow.value = value.trim()
        scope.launch {
            dataStore.edit { prefs ->
                prefs[PUBLIC_ADDRESS_KEY] = value.trim()
            }
        }
    }

    var email: String
        get() = _emailFlow.value
        set(value) {
            _emailFlow.value = value
            updateIdentityHash(value)
            scope.launch {
                println("Saving new email to DataStore: $value")
                dataStore.edit { prefs ->
                    prefs[EMAIL_KEY] = value
                }
            }
        }

    var alias: String
        get() = _aliasFlow.value
        set(value) {
            val trimmed = value.trim().take(32)
            _aliasFlow.value = trimmed
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[ALIAS_KEY] = trimmed
                }
            }
        }

    var clipboardSyncEnabled: Boolean
        get() = _clipboardSyncEnabledFlow.value
        set(value) {
            _clipboardSyncEnabledFlow.value = value
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[CLIPBOARD_SYNC_ENABLED_KEY] = value
                }
            }
        }

    var wiggleEnabled: Boolean
        get() = _wiggleEnabledFlow.value
        set(value) {
            _wiggleEnabledFlow.value = value
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[WIGGLE_ENABLED_KEY] = value
                }
            }
        }

    var upnpEnabled: Boolean
        get() = _upnpEnabledFlow.value
        set(value) {
            _upnpEnabledFlow.value = value
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[UPNP_ENABLED_KEY] = value
                }
            }
        }

    val fingerprint: String
        get() = _fingerprintFlow.value

    val identityHash: String
        get() = _identityHashFlow.value

    val googleSub: String
        get() = _googleSubFlow.value

    fun setGoogleProfile(name: String, picture: String) {
        _profileNameFlow.value = name
        _profilePictureFlow.value = picture
        scope.launch {
            dataStore.edit { prefs ->
                prefs[GOOGLE_NAME_KEY] = name
                prefs[GOOGLE_PICTURE_KEY] = picture
            }
        }
    }

    fun setGoogleSub(sub: String) {
        _googleSubFlow.value = sub
        scope.launch {
            dataStore.edit { prefs ->
                prefs[GOOGLE_SUB_KEY] = sub
            }
        }
    }

    fun signOut() {
        email = ""
        setGoogleProfile("", "")
        setGoogleSub("")
    }

    init {
        scope.launch {
            println("Initializing DeviceConfig from DataStore...")
            val prefs = dataStore.data.first()

            val savedEmail = prefs[EMAIL_KEY] ?: ""
            _emailFlow.value = savedEmail

            var savedFingerprint = prefs[FINGERPRINT_KEY]
            if (savedFingerprint == null) {
                savedFingerprint = com.dexstudios.dex.core.network.HashUtils.generateUUID()
                dataStore.edit { it[FINGERPRINT_KEY] = savedFingerprint }
                println("Generated new device fingerprint: $savedFingerprint")
            }
            _fingerprintFlow.value = savedFingerprint

            updateIdentityHashInternal(savedEmail, prefs[IDENTITY_HASH_KEY])
            _publicAddressFlow.value = prefs[PUBLIC_ADDRESS_KEY] ?: ""
            _profileNameFlow.value = prefs[GOOGLE_NAME_KEY] ?: ""
            _profilePictureFlow.value = prefs[GOOGLE_PICTURE_KEY] ?: ""
            _googleSubFlow.value = prefs[GOOGLE_SUB_KEY] ?: ""
            _aliasFlow.value = prefs[ALIAS_KEY] ?: ""
            _clipboardSyncEnabledFlow.value = prefs[CLIPBOARD_SYNC_ENABLED_KEY] ?: true
            _wiggleEnabledFlow.value = prefs[WIGGLE_ENABLED_KEY] ?: true
            _upnpEnabledFlow.value = prefs[UPNP_ENABLED_KEY] ?: false
            println("DeviceConfig fully initialized.")
        }
    }

    private suspend fun updateIdentityHashInternal(emailStr: String, savedHash: String?) {
        if (emailStr.isNotBlank()) {
            val newHash = com.dexstudios.dex.core.network.HashUtils.sha256(emailStr.trim().lowercase())
            _identityHashFlow.value = newHash
            dataStore.edit { it[IDENTITY_HASH_KEY] = newHash }
        } else {
            val newHash = savedHash ?: run {
                val generated = com.dexstudios.dex.core.network.HashUtils.generateUUID()
                dataStore.edit { it[IDENTITY_HASH_KEY] = generated }
                generated
            }
            _identityHashFlow.value = newHash
        }
        println("Identity hash updated: ${_identityHashFlow.value}")
    }

    private fun updateIdentityHash(emailStr: String) {
        scope.launch {
            val prefs = dataStore.data.first()
            updateIdentityHashInternal(emailStr, prefs[IDENTITY_HASH_KEY])
        }
    }
}
