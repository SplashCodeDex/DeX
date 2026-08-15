package com.dexstudios.dex.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class TransferRecord(
    val id: String,
    val name: String,
    val size: Long,
    val timestamp: Long,
    val direction: String,
    val uri: String? = null,
    val peerDevice: String? = null,
    val status: String = "success"
)

object TransferHistory : KoinComponent {
    private val KEY_TRANSFERS = stringPreferencesKey("transfers")
    private const val MAX_ENTRIES = 200

    private val dataStore: DataStore<Preferences> by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _items = MutableStateFlow<List<TransferRecord>>(emptyList())
    val items: StateFlow<List<TransferRecord>> = _items.asStateFlow()

    fun init() {
        scope.launch {
            _items.value = read()
        }
    }

    fun refresh() {
        scope.launch {
            _items.value = read()
        }
    }

    fun delete(id: String) {
        val updated = _items.value.filter { it.id != id }
        _items.value = updated
        scope.launch { write(updated) }
    }

    fun clear() {
        _items.value = emptyList()
        scope.launch { write(emptyList()) }
    }

    fun log(
        name: String,
        size: Long,
        direction: String,
        uri: String? = null,
        peerDevice: String? = null,
        status: String = "success",
        timestamp: Long = com.dexstudios.dex.network.protocol.HashUtils.currentTimeMillis()
    ) {
        val record = TransferRecord(
            id = com.dexstudios.dex.network.protocol.HashUtils.generateUUID(),
            name = name,
            size = size,
            timestamp = timestamp,
            direction = direction,
            uri = uri,
            peerDevice = peerDevice,
            status = status
        )
        val updated = (listOf(record) + _items.value).take(MAX_ENTRIES)
        _items.value = updated
        scope.launch { write(updated) }
    }

    private suspend fun read(): List<TransferRecord> {
        val raw = dataStore.data.map { it[KEY_TRANSFERS] }.firstOrNull() ?: return emptyList()
        return try {
            Json.decodeFromString<List<TransferRecord>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun write(items: List<TransferRecord>) {
        val raw = Json.encodeToString(items)
        dataStore.edit { prefs ->
            prefs[KEY_TRANSFERS] = raw
        }
    }
}
