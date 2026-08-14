package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object DeviceManager {
    private const val PREFS_NAME = "dex_device_prefs"
    private const val KEY_PAIRED_FINGERPRINTS = "paired_fingerprints"
    private const val KEY_PAIRED_TOKENS = "paired_tokens"
    private const val KEY_PAIRED_TIMES = "paired_times"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPairedFingerprints()
        loadPairedTokens()
        loadPairedTimes()
    }

    private fun loadPairedFingerprints() {
        val saved = prefs.getStringSet(KEY_PAIRED_FINGERPRINTS, emptySet())
        AuthState.pairedFingerprints.clear()
        if (saved != null) {
            AuthState.pairedFingerprints.addAll(saved)
        }
    }

    private fun loadPairedTokens() {
        val saved = prefs.getString(KEY_PAIRED_TOKENS, null) ?: return
        try {
            val map = TokenCodec.decode(saved)
            AuthState.pairedTokens.clear()
            AuthState.pairedTokens.putAll(map)
        } catch (_: Exception) {}
    }

    private fun loadPairedTimes() {
        val saved = prefs.getString(KEY_PAIRED_TIMES, null) ?: return
        try {
            val json = org.json.JSONObject(saved)
            AuthState.pairedTimes.clear()
            json.keys().forEach { key ->
                AuthState.pairedTimes[key] = json.getLong(key)
            }
        } catch (_: Exception) {}
    }

    fun savePairedFingerprint(fingerprint: String) {
        AuthState.pairedFingerprints.add(fingerprint)
        if (!AuthState.pairedTimes.containsKey(fingerprint)) {
            AuthState.pairedTimes[fingerprint] = System.currentTimeMillis()
        }
        val timesJson = org.json.JSONObject()
        AuthState.pairedTimes.forEach { (k, v) -> timesJson.put(k, v) }
        
        prefs.edit { 
            putStringSet(KEY_PAIRED_FINGERPRINTS, AuthState.pairedFingerprints.toSet())
            putString(KEY_PAIRED_TIMES, timesJson.toString())
        }
    }

    fun savePairedToken(fingerprint: String, token: String) {
        AuthState.pairedTokens[fingerprint] = token
        prefs.edit { putString(KEY_PAIRED_TOKENS, TokenCodec.encode(AuthState.pairedTokens)) }
    }

    fun removePairedFingerprint(fingerprint: String) {
        TcpDownloadService.cancelIfFingerprint(fingerprint)
        AuthState.pairedFingerprints.remove(fingerprint)
        AuthState.pairedTokens.remove(fingerprint)
        AuthState.pairedTimes.remove(fingerprint)
        
        val timesJson = org.json.JSONObject()
        AuthState.pairedTimes.forEach { (k, v) -> timesJson.put(k, v) }
        
        prefs.edit {
            putStringSet(KEY_PAIRED_FINGERPRINTS, AuthState.pairedFingerprints.toSet())
            putString(KEY_PAIRED_TOKENS, TokenCodec.encode(AuthState.pairedTokens))
            putString(KEY_PAIRED_TIMES, timesJson.toString())
        }
    }
}
