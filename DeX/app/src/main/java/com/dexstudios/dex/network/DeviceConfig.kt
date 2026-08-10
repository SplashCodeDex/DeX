package com.dexstudios.dex.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dex_datastore",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "dex_prefs"))
    }
)

class DeviceConfig(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val EMAIL_KEY = stringPreferencesKey("email")
        val FINGERPRINT_KEY = stringPreferencesKey("fingerprint")
        val IDENTITY_HASH_KEY = stringPreferencesKey("identity_hash")
        val PUBLIC_ADDRESS_KEY = stringPreferencesKey("public_address")
        val GOOGLE_NAME_KEY = stringPreferencesKey("google_name")
        val GOOGLE_PICTURE_KEY = stringPreferencesKey("google_picture")
        val GOOGLE_SUB_KEY = stringPreferencesKey("google_sub")
    }

    private val _emailFlow = MutableStateFlow("")
    val emailFlow: StateFlow<String> = _emailFlow.asStateFlow()

    private val _profileNameFlow = MutableStateFlow("")
    val profileNameFlow: StateFlow<String> = _profileNameFlow.asStateFlow()

    private val _profilePictureFlow = MutableStateFlow("")
    val profilePictureFlow: StateFlow<String> = _profilePictureFlow.asStateFlow()

    private val _googleSubFlow = MutableStateFlow("")
    val googleSubFlow: StateFlow<String> = _googleSubFlow.asStateFlow()

    private val _fingerprintFlow = MutableStateFlow("")
    val fingerprintFlow: StateFlow<String> = _fingerprintFlow.asStateFlow()

    private val _identityHashFlow = MutableStateFlow("")

    private val _publicAddressFlow = MutableStateFlow("")

    /** Single combined flow — replacing three individual collectAsState() reads in the UI. */
    val googleProfileFlow: StateFlow<GoogleProfile> = combine(
        _profileNameFlow, _profilePictureFlow, _emailFlow
    ) { name, picture, email ->
        GoogleProfile(name, picture, email)
    }.stateIn(scope, SharingStarted.Eagerly, GoogleProfile())

    val publicAddress: String
        get() = _publicAddressFlow.value

    fun setPublicAddress(value: String) {
        _publicAddressFlow.value = value.trim()
        scope.launch {
            context.dataStore.edit { prefs ->
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
                Timber.d("Saving new email to DataStore: %s", value)
                context.dataStore.edit { prefs ->
                    prefs[EMAIL_KEY] = value
                }
            }
        }

    /** Fingerprint, computed once. First access during cold start blocks until DataStore loads. */
    val fingerprint: String
        get() {
            if (_fingerprintFlow.value.isEmpty()) {
                _fingerprintFlow.value = runBlocking(Dispatchers.IO) {
                    val prefs = context.dataStore.data.first()
                    prefs[FINGERPRINT_KEY] ?: UUID.randomUUID().toString().also { fp ->
                        context.dataStore.edit { it[FINGERPRINT_KEY] = fp }
                    }
                }
            }
            return _fingerprintFlow.value
        }

    val identityHash: String
        get() = _identityHashFlow.value

    val profileName: String
        get() = _profileNameFlow.value

    val profilePicture: String
        get() = _profilePictureFlow.value

    /** Google account ID (sub) — the unguessable same-email trust key when signed in with Google. */
    val googleSub: String
        get() = _googleSubFlow.value

    /** Stores the signed-in Google profile (name + avatar) alongside the verified email. */
    fun setGoogleProfile(name: String, picture: String) {
        _profileNameFlow.value = name
        _profilePictureFlow.value = picture
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[GOOGLE_NAME_KEY] = name
                prefs[GOOGLE_PICTURE_KEY] = picture
            }
        }
    }

    fun setGoogleSub(sub: String) {
        _googleSubFlow.value = sub
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[GOOGLE_SUB_KEY] = sub
            }
        }
    }

    /** Signs out of the Google identity: email clears (identity hash resets), profile and sub detach. */
    fun signOut() {
        email = ""
        setGoogleProfile("", "")
        setGoogleSub("")
    }

    init {
        // DataStore is loaded asynchronously — non-critical UI fields start with
        // empty defaults and populate in under one frame. Fingerprint reads are
        // guarded on first access (see the getter below) so the discovery engine
        // never sees a blank id, even during cold start.
        scope.launch {
            Timber.i("Initializing DeviceConfig from DataStore...")
            val prefs = context.dataStore.data.first()

            val savedEmail = prefs[EMAIL_KEY] ?: ""
            _emailFlow.value = savedEmail

            var savedFingerprint = prefs[FINGERPRINT_KEY]
            if (savedFingerprint == null) {
                savedFingerprint = UUID.randomUUID().toString()
                context.dataStore.edit { it[FINGERPRINT_KEY] = savedFingerprint }
                Timber.d("Generated new device fingerprint: %s", savedFingerprint)
            }
            _fingerprintFlow.value = savedFingerprint

            updateIdentityHashInternal(savedEmail, prefs[IDENTITY_HASH_KEY])
            _publicAddressFlow.value = prefs[PUBLIC_ADDRESS_KEY] ?: ""
            _profileNameFlow.value = prefs[GOOGLE_NAME_KEY] ?: ""
            _profilePictureFlow.value = prefs[GOOGLE_PICTURE_KEY] ?: ""
            _googleSubFlow.value = prefs[GOOGLE_SUB_KEY] ?: ""
            Timber.i("DeviceConfig fully initialized.")
        }
    }

    private suspend fun updateIdentityHashInternal(emailStr: String, savedHash: String?) {
        if (emailStr.isNotBlank()) {
            val bytes = MessageDigest.getInstance("SHA-256").digest(emailStr.trim().lowercase().toByteArray())
            val newHash = bytes.joinToString("") { "%02x".format(it) }
            _identityHashFlow.value = newHash
            context.dataStore.edit { it[IDENTITY_HASH_KEY] = newHash }
        } else {
            val newHash = if (savedHash != null) {
                savedHash
            } else {
                val generated = UUID.randomUUID().toString()
                context.dataStore.edit { it[IDENTITY_HASH_KEY] = generated }
                generated
            }
            _identityHashFlow.value = newHash
        }
        Timber.d("Identity hash updated: %s", _identityHashFlow.value)
    }

    private fun updateIdentityHash(emailStr: String) {
        scope.launch {
            val prefs = context.dataStore.data.first()
            updateIdentityHashInternal(emailStr, prefs[IDENTITY_HASH_KEY])
        }
    }
}

/** Combined profile snapshot — prevents triple-recomposition on Google sign-in. */
data class GoogleProfile(
    val name: String = "",
    val picture: String = "",
    val email: String = ""
)
