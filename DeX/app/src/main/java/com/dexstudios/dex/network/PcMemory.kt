package com.dexstudios.dex.network

import android.content.Context
import androidx.core.content.edit

object PcMemory {
    private const val PREFS = "dex_pc_prefs"
    private const val KEY_FINGERPRINT = "last_pc_fingerprint"
    private const val KEY_IP = "last_pc_ip"
    private const val KEY_PORT = "last_pc_port"
    private const val KEY_QUIC_PORT = "last_pc_quic_port"

    fun save(context: Context, fingerprint: String, ip: String, port: Int, quicPort: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_FINGERPRINT, fingerprint)
            putString(KEY_IP, ip)
            putInt(KEY_PORT, port)
            putInt(KEY_QUIC_PORT, quicPort)
        }
    }

    fun fingerprint(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FINGERPRINT, null)
    }

    fun ip(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_IP, null)
    }

    fun port(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, DeXPorts.HTTPS)
    }

    fun quicPort(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_QUIC_PORT, DeXPorts.QUIC)
    }
}
