package com.dexstudios.dex.network

import android.content.Context

object PcMemory {
    private const val PREFS = "dex_pc_prefs"
    private const val KEY_FINGERPRINT = "last_pc_fingerprint"
    private const val KEY_IP = "last_pc_ip"

    fun save(context: Context, fingerprint: String, ip: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FINGERPRINT, fingerprint)
            .putString(KEY_IP, ip)
            .apply()
    }

    fun fingerprint(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FINGERPRINT, null)
    }

    fun ip(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IP, null)
    }
}
