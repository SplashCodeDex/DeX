package com.dexstudios.dex.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dexstudios.dex.network.protocol.DeXPorts
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object PcMemory : KoinComponent {
    private val KEY_FINGERPRINT = stringPreferencesKey("last_pc_fingerprint")
    private val KEY_IP = stringPreferencesKey("last_pc_ip")
    private val KEY_PORT = intPreferencesKey("last_pc_port")
    private val KEY_QUIC_PORT = intPreferencesKey("last_pc_quic_port")

    private val dataStore: DataStore<Preferences> by inject()

    suspend fun save(fingerprint: String, ip: String, port: Int, quicPort: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_FINGERPRINT] = fingerprint
            prefs[KEY_IP] = ip
            prefs[KEY_PORT] = port
            prefs[KEY_QUIC_PORT] = quicPort
        }
    }

    suspend fun fingerprint(): String? {
        return dataStore.data.map { it[KEY_FINGERPRINT] }.firstOrNull()
    }

    suspend fun ip(): String? {
        return dataStore.data.map { it[KEY_IP] }.firstOrNull()
    }

    suspend fun port(): Int {
        return dataStore.data.map { it[KEY_PORT] }.firstOrNull() ?: DeXPorts.HTTPS
    }

    suspend fun quicPort(): Int {
        return dataStore.data.map { it[KEY_QUIC_PORT] }.firstOrNull() ?: DeXPorts.QUIC
    }
}
