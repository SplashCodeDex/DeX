package com.dexstudios.dex.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    fun init(store: DataStore<Preferences>) {
        dataStore = store
        runBlocking {
            val prefs = dataStore.data.first()
            loadPairedFingerprints(prefs)
            loadPairedTokens(prefs)
            loadPairedTimes(prefs)
        }
    }

    private fun loadPairedFingerprints(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_FINGERPRINTS] ?: emptySet()
        com.dexstudios.dex.auth.AuthState.pairedFingerprints.clear()
        com.dexstudios.dex.auth.AuthState.pairedFingerprints.addAll(saved)
    }

    private fun loadPairedTokens(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_TOKENS] ?: return
        try {
            val map = TokenCodec.decode(saved)
            com.dexstudios.dex.auth.AuthState.pairedTokens.clear()
            com.dexstudios.dex.auth.AuthState.pairedTokens.putAll(map)
        } catch (_: Exception) {}
    }

    private fun loadPairedTimes(prefs: Preferences) {
        val saved = prefs[KEY_PAIRED_TIMES] ?: return
        try {
            val json = Json.parseToJsonElement(saved).jsonObject
            com.dexstudios.dex.auth.AuthState.pairedTimes.clear()
            json.forEach { (key, element) ->
                val time = element.jsonPrimitive.longOrNull
                if (time != null) {
                    com.dexstudios.dex.auth.AuthState.pairedTimes[key] = time
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun savePairedFingerprint(fingerprint: String) {
        com.dexstudios.dex.auth.AuthState.pairedFingerprints.add(fingerprint)
        if (!com.dexstudios.dex.auth.AuthState.pairedTimes.containsKey(fingerprint)) {
            com.dexstudios.dex.auth.AuthState.pairedTimes[fingerprint] = com.dexstudios.dex.network.protocol.HashUtils.currentTimeMillis()
        }
        
        val timesMap = com.dexstudios.dex.auth.AuthState.pairedTimes.toMap()
        
        dataStore.edit { prefs ->
            prefs[KEY_PAIRED_FINGERPRINTS] = com.dexstudios.dex.auth.AuthState.pairedFingerprints.toSet()
            prefs[KEY_PAIRED_TIMES] = Json.encodeToString(timesMap)
        }
    }

    suspend fun savePairedToken(fingerprint: String, token: String) {
        com.dexstudios.dex.auth.AuthState.pairedTokens[fingerprint] = token
        dataStore.edit { prefs -> 
            prefs[KEY_PAIRED_TOKENS] = TokenCodec.encode(com.dexstudios.dex.auth.AuthState.pairedTokens.toMap()) 
        }
    }

    suspend fun removePairedFingerprint(fingerprint: String) {
        // TCP Download cancel logic should be decoupled or moved to an event/callback
        // TcpDownloadService.cancelIfFingerprint(fingerprint)
        
        com.dexstudios.dex.auth.AuthState.pairedFingerprints.remove(fingerprint)
        com.dexstudios.dex.auth.AuthState.pairedTokens.remove(fingerprint)
        com.dexstudios.dex.auth.AuthState.pairedTimes.remove(fingerprint)
        
        val timesMap = com.dexstudios.dex.auth.AuthState.pairedTimes.toMap()
        
        dataStore.edit { prefs ->
            prefs[KEY_PAIRED_FINGERPRINTS] = com.dexstudios.dex.auth.AuthState.pairedFingerprints.toSet()
            prefs[KEY_PAIRED_TOKENS] = TokenCodec.encode(com.dexstudios.dex.auth.AuthState.pairedTokens.toMap())
            prefs[KEY_PAIRED_TIMES] = Json.encodeToString(timesMap)
        }
    }
}
