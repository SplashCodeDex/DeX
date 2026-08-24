package com.dexstudios.dex.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent

/** Combined profile snapshot — prevents triple-recomposition on Google sign-in. */
data class GoogleProfile(val name: String = "", val picture: String = "", val email: String = "")

class DeviceConfig(private val dataStore: DataStore<Preferences>, private val scope: CoroutineScope) : KoinComponent {

    // Tracked persistence writes so shutdown can await them instead of dropping the
    // user's latest change mid-flush (a hard exitProcess kills in-flight DataStore edits).
    private val pendingWrites = mutableListOf<Job>()
    private val writeLock = Any()

    private fun persist(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        synchronized(writeLock) { pendingWrites.add(job) }
        job.invokeOnCompletion { synchronized(writeLock) { pendingWrites.remove(job) } }
    }

    /**
     * Awaits (bounded) every in-flight persisted write. Invoked by the shutdown
     * coordinator before teardown so quitting can never lose a settings change.
     */
    suspend fun flushPersistedWrites(timeoutMillis: Long = 2_000L) {
        val jobs = synchronized(writeLock) { pendingWrites.toList() }
        if (jobs.isEmpty()) return
        withTimeoutOrNull(timeoutMillis) { jobs.forEach { it.join() } }
    }

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
        val DND_ENABLED_KEY = booleanPreferencesKey("dnd_enabled")
        val THEME_OVERRIDE_KEY = stringPreferencesKey("theme_override")
        val DOWNLOAD_DIR_KEY = stringPreferencesKey("download_dir")

        // Legal values for [THEME_OVERRIDE_KEY]; absent key means follow the OS setting.
        const val THEME_SYSTEM = "system"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }

    private val _emailFlow = MutableStateFlow("")
    val emailFlow: StateFlow<String> = _emailFlow.asStateFlow()

    private val _aliasFlow = MutableStateFlow("")
    val aliasFlow: StateFlow<String> = _aliasFlow.asStateFlow()

    private val _clipboardSyncEnabledFlow = MutableStateFlow(true)
    val clipboardSyncEnabledFlow: StateFlow<Boolean> = _clipboardSyncEnabledFlow.asStateFlow()

    private val _wiggleEnabledFlow = MutableStateFlow(true)
    val wiggleEnabledFlow: StateFlow<Boolean> = _wiggleEnabledFlow.asStateFlow()

    private val _dndEnabledFlow = MutableStateFlow(false)
    val dndEnabledFlow: StateFlow<Boolean> = _dndEnabledFlow.asStateFlow()

    private val _themeOverrideFlow = MutableStateFlow(THEME_SYSTEM)
    val themeOverrideFlow: StateFlow<String> = _themeOverrideFlow.asStateFlow()

    /** Signals completion of the init-block DataStore load; consumers gate on it to avoid racing defaults. */
    private val _initializedFlow = MutableStateFlow(false)
    val initializedFlow: StateFlow<Boolean> = _initializedFlow.asStateFlow()

    /**
     * Custom download directory override for inbound transfers. Empty string means "use the
     * legacy default" (`~/Downloads/DeX`, owned by [com.dexstudios.dex.core.network.server.ReceiveStorage]).
     */
    private val _downloadDirFlow = MutableStateFlow("")
    val downloadDirFlow: StateFlow<String> = _downloadDirFlow.asStateFlow()

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
        persist {
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
            persist {
                Logger.i("Saving new email to DataStore: $value")
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
            persist {
                dataStore.edit { prefs ->
                    prefs[ALIAS_KEY] = trimmed
                }
            }
        }

    var clipboardSyncEnabled: Boolean
        get() = _clipboardSyncEnabledFlow.value
        set(value) {
            _clipboardSyncEnabledFlow.value = value
            persist {
                dataStore.edit { prefs ->
                    prefs[CLIPBOARD_SYNC_ENABLED_KEY] = value
                }
            }
        }

    var wiggleEnabled: Boolean
        get() = _wiggleEnabledFlow.value
        set(value) {
            _wiggleEnabledFlow.value = value
            persist {
                dataStore.edit { prefs ->
                    prefs[WIGGLE_ENABLED_KEY] = value
                }
            }
        }

    /**
     * Do Not Disturb: mutes the ALERT layer only (tray notifications for incoming files
     * and pairing requests). Transfers still arrive and pairing still works — an end
     * user who wants total silence closes the app entirely.
     */
    var dndEnabled: Boolean
        get() = _dndEnabledFlow.value
        set(value) {
            _dndEnabledFlow.value = value
            persist {
                dataStore.edit { prefs ->
                    prefs[DND_ENABLED_KEY] = value
                }
            }
        }

    /**
     * Theme override: one of [THEME_SYSTEM] (follow the OS), [THEME_DARK] or [THEME_LIGHT].
     * Resolved into an actual dark flag by the app shell when composing DeXTheme.
     */
    var themeOverride: String
        get() = _themeOverrideFlow.value
        set(value) {
            val normalized = when (value) {
                THEME_DARK, THEME_LIGHT -> value
                else -> THEME_SYSTEM
            }
            _themeOverrideFlow.value = normalized
            persist {
                dataStore.edit { prefs ->
                    prefs[THEME_OVERRIDE_KEY] = normalized
                }
            }
        }

    val fingerprint: String
        get() = _fingerprintFlow.value

    /** Custom download directory for inbound transfers; empty string = platform default. */
    var downloadDir: String
        get() = _downloadDirFlow.value
        set(value) {
            val normalized = value.trim()
            _downloadDirFlow.value = normalized
            persist {
                dataStore.edit { prefs ->
                    prefs[DOWNLOAD_DIR_KEY] = normalized
                }
            }
        }

    val identityHash: String
        get() = _identityHashFlow.value

    val googleSub: String
        get() = _googleSubFlow.value

    fun setGoogleProfile(name: String, picture: String) {
        _profileNameFlow.value = name
        _profilePictureFlow.value = picture
        persist {
            dataStore.edit { prefs ->
                prefs[GOOGLE_NAME_KEY] = name
                prefs[GOOGLE_PICTURE_KEY] = picture
            }
        }
    }

    fun setGoogleSub(sub: String) {
        _googleSubFlow.value = sub
        persist {
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

    /**
     * Full "Reset Identity & Trust" semantics: sign out AND rotate the identity hash so a
     * previously leaked/known auto-trust credential dies with the reset. Pairing revocation
     * itself lives in [com.dexstudios.dex.core.network.DeviceManager] (network module).
     *
     * Deliberately bypasses the [email] setter: it re-derives/restores a stored hash, which
     * would race this rotation and resurrect the old credential.
     */
    fun resetIdentity(onRotation: suspend () -> Unit = {}) {
        scope.launch {
            onRotation()
            _emailFlow.value = ""
            setGoogleProfile("", "")
            setGoogleSub("")
            val freshHash = com.dexstudios.dex.core.network.HashUtils.generateUUID()
            _identityHashFlow.value = freshHash
            dataStore.edit { prefs ->
                prefs[EMAIL_KEY] = ""
                prefs[IDENTITY_HASH_KEY] = freshHash
            }
            Logger.i("Identity reset: rotated identity hash")
        }
    }

    init {
        scope.launch {
            Logger.i("Initializing DeviceConfig from DataStore...")
            val prefs = dataStore.data.first()

            val savedEmail = prefs[EMAIL_KEY] ?: ""
            _emailFlow.value = savedEmail

            var savedFingerprint = prefs[FINGERPRINT_KEY]
            if (savedFingerprint == null) {
                savedFingerprint = com.dexstudios.dex.core.network.HashUtils.generateUUID()
                dataStore.edit { it[FINGERPRINT_KEY] = savedFingerprint }
                Logger.i("Generated new device fingerprint: $savedFingerprint")
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
            _dndEnabledFlow.value = prefs[DND_ENABLED_KEY] ?: false
            _themeOverrideFlow.value = when (val savedTheme = prefs[THEME_OVERRIDE_KEY]) {
                THEME_DARK, THEME_LIGHT -> savedTheme
                else -> THEME_SYSTEM
            }
            _downloadDirFlow.value = prefs[DOWNLOAD_DIR_KEY] ?: ""
            Logger.i("DeviceConfig fully initialized.")
            _initializedFlow.value = true
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
        Logger.i("Identity hash updated: ${_identityHashFlow.value}")
    }

    private fun updateIdentityHash(emailStr: String) {
        persist {
            val prefs = dataStore.data.first()
            updateIdentityHashInternal(emailStr, prefs[IDENTITY_HASH_KEY])
        }
    }
}
