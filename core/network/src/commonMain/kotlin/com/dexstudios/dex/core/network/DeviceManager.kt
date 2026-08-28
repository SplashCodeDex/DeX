package com.dexstudios.dex.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dexstudios.dex.auth.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object DeviceManager {
    private val KEY_PAIRED_FINGERPRINTS = stringSetPreferencesKey("paired_fingerprints")
    private val KEY_PAIRED_TOKENS = stringPreferencesKey("paired_tokens")
    private val KEY_PAIRED_TIMES = stringPreferencesKey("paired_times")

    private lateinit var dataStore: DataStore<Preferences>

    suspend fun init(store: DataStore<Preferences>) {
        dataStore = store
        val prefs = dataStore.data.first()
        loadPairedFingerprints(prefs)
        loadPairedTokens(prefs)
        loadPairedTimes(prefs)
    }

    private fun loadPairedFingerprints(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_FINGERPRINTS] ?: emptySet()
        AuthState.updateFingerprints(saved)
    }

    private fun loadPairedTokens(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_TOKENS] ?: return
        try {
            val map = TokenCodec.decode(saved)
            AuthState.updateTokens(map)
        } catch (_: Exception) {}
    }

    private fun loadPairedTimes(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_TIMES] ?: return
        try {
            val json = Json.parseToJsonElement(saved).jsonObject
            val map = mutableMapOf<String, Long>()
            json.forEach { (key, element) ->
                val time = element.jsonPrimitive.longOrNull
                if (time != null) {
                    map[key] = time
                }
            }
            AuthState.updateTimes(map)
        } catch (_: Exception) {}
    }

    suspend fun savePairedFingerprint(fingerprint: String) {
        val newFingerprints = AuthState.pairedFingerprints.value.toMutableSet()
        newFingerprints.add(fingerprint)
        AuthState.updateFingerprints(newFingerprints)

        val newTimes = AuthState.pairedTimes.value.toMutableMap()
        if (!newTimes.containsKey(fingerprint)) {
            newTimes[fingerprint] = HashUtils.currentTimeMillis()
            AuthState.updateTimes(newTimes)
        }

        if (::dataStore.isInitialized) {
            dataStore.edit { prefs ->
                prefs[KEY_PAIRED_FINGERPRINTS] = newFingerprints
                prefs[KEY_PAIRED_TIMES] = Json.encodeToString(newTimes)
            }
        }
    }

    suspend fun savePairedToken(fingerprint: String, token: String) {
        val newTokens = AuthState.pairedTokens.value.toMutableMap()
        newTokens[fingerprint] = token
        AuthState.updateTokens(newTokens)
        if (::dataStore.isInitialized) {
            dataStore.edit { prefs ->
                prefs[KEY_PAIRED_TOKENS] = TokenCodec.encode(newTokens)
            }
        }
    }

    suspend fun removePairedFingerprint(fingerprint: String) {
        val newFingerprints = AuthState.pairedFingerprints.value.toMutableSet()
        newFingerprints.remove(fingerprint)
        AuthState.updateFingerprints(newFingerprints)

        val newTokens = AuthState.pairedTokens.value.toMutableMap()
        newTokens.remove(fingerprint)
        AuthState.updateTokens(newTokens)

        val newTimes = AuthState.pairedTimes.value.toMutableMap()
        newTimes.remove(fingerprint)
        AuthState.updateTimes(newTimes)

        if (::dataStore.isInitialized) {
            dataStore.edit { prefs ->
                prefs[KEY_PAIRED_FINGERPRINTS] = newFingerprints
                prefs[KEY_PAIRED_TOKENS] = TokenCodec.encode(newTokens)
                prefs[KEY_PAIRED_TIMES] = Json.encodeToString(newTimes)
            }
        }
    }
}
