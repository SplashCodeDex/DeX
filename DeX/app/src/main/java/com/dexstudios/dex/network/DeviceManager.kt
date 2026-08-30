package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.dexstudios.dex.ShortcutHelper
import timber.log.Timber

object DeviceManager {
    private const val PREFS_NAME = "dex_device_prefs"
    private const val KEY_PAIRED_FINGERPRINTS = "paired_fingerprints"
    private const val KEY_PAIRED_TOKENS = "paired_tokens"
    private const val KEY_PAIRED_TIMES = "paired_times"
    private const val KEY_PAIRED_ALIASES = "paired_aliases"

    private lateinit var prefs: SharedPreferences

    // Application context captured at init (DeXApplication passes `this`) so unpair
    // paths can tear down Direct Share shortcuts from anywhere.
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPairedFingerprints()
        loadPairedTokens()
        loadPairedTimes()
        loadPairedAliases()
    }

    /** Latest known display alias per paired fingerprint — powers Direct Share labels while the peer is offline. */
    val pairedAliases: Map<String, String> get() = AuthState.pairedAliases

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
        } catch (e: Exception) { Timber.w(e, "DeviceManager: failed to restore paired tokens") }
    }

    private fun loadPairedTimes() {
        val saved = prefs.getString(KEY_PAIRED_TIMES, null) ?: return
        try {
            val json = org.json.JSONObject(saved)
            AuthState.pairedTimes.clear()
            json.keys().forEach { key ->
                AuthState.pairedTimes[key] = json.getLong(key)
            }
        } catch (e: Exception) { Timber.w(e, "DeviceManager: failed to restore paired times") }
    }

    private fun loadPairedAliases() {
        val saved = prefs.getString(KEY_PAIRED_ALIASES, null) ?: return
        try {
            val json = org.json.JSONObject(saved)
            AuthState.pairedAliases.clear()
            json.keys().forEach { key ->
                AuthState.pairedAliases[key] = json.optString(key)
            }
        } catch (_: Exception) {}
    }

    /**
     * Records the freshest display alias for a paired fingerprint so Direct Share
     * shortcuts keep correct labels even when the peer is offline or the process
     * was restarted. No-ops when nothing changed to avoid redundant disk writes —
     * discovery rebroadcasts the same alias every cycle.
     */
    fun savePairedAlias(fingerprint: String, alias: String) {
        if (alias.isBlank()) return
        if (AuthState.pairedAliases[fingerprint] == alias) return
        AuthState.pairedAliases[fingerprint] = alias
        val aliasesJson = org.json.JSONObject()
        AuthState.pairedAliases.forEach { (k, v) -> aliasesJson.put(k, v) }
        prefs.edit { putString(KEY_PAIRED_ALIASES, aliasesJson.toString()) }
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
        AuthState.pairedAliases.remove(fingerprint)

        val timesJson = org.json.JSONObject()
        AuthState.pairedTimes.forEach { (k, v) -> timesJson.put(k, v) }
        val aliasesJson = org.json.JSONObject()
        AuthState.pairedAliases.forEach { (k, v) -> aliasesJson.put(k, v) }

        prefs.edit {
            putStringSet(KEY_PAIRED_FINGERPRINTS, AuthState.pairedFingerprints.toSet())
            putString(KEY_PAIRED_TOKENS, TokenCodec.encode(AuthState.pairedTokens))
            putString(KEY_PAIRED_TIMES, timesJson.toString())
            putString(KEY_PAIRED_ALIASES, aliasesJson.toString())
        }

        // Single funnel for every unpair path (UI forget, PC-initiated unpair, trust
        // downgrade) — the share-sheet target must die with the pairing in all of them.
        appContext?.let { ShortcutHelper.removeShortcut(it, fingerprint) }
    }
}
