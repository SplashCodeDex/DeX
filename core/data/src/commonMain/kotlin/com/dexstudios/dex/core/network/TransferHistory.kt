package com.dexstudios.dex.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val status: String = "success",
)

object TransferHistory : KoinComponent {
    private val KEY_TRANSFERS = stringPreferencesKey("transfers")
    private const val MAX_ENTRIES = 200

    private val dataStore: DataStore<Preferences> by inject()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutationMutex = Mutex()
    private var isLoaded = false

    private val _items = MutableStateFlow<List<TransferRecord>>(emptyList())
    val items: StateFlow<List<TransferRecord>> = _items.asStateFlow()

    fun init() {
        scope.launch { reload() }
    }

    fun refresh() {
        scope.launch { reload() }
    }

    fun delete(id: String) {
        deleteAll(listOf(id))
    }

    fun deleteAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            mutationMutex.withLock {
                val idSet = ids.toHashSet()
                mutateLocked { current -> current.filterNot { it.id in idSet } }
            }
        }
    }

    fun clear() {
        scope.launch {
            mutationMutex.withLock {
                mutateLocked { emptyList() }
            }
        }
    }

    fun log(name: String, size: Long, direction: String, uri: String? = null, peerDevice: String? = null, status: String = "success", timestamp: Long = HashUtils.currentTimeMillis()) {
        val record = TransferRecord(
            id = HashUtils.generateUUID(),
            name = name,
            size = size,
            timestamp = timestamp,
            direction = direction,
            uri = uri,
            peerDevice = peerDevice,
            status = status,
        )
        scope.launch {
            mutationMutex.withLock {
                mutateLocked { current -> (listOf(record) + current).take(MAX_ENTRIES) }
            }
        }
    }

    private suspend fun reload() {
        mutationMutex.withLock {
            val loaded = read()
            _items.value = loaded
            isLoaded = true
        }
    }

    private suspend fun mutateLocked(transform: (List<TransferRecord>) -> List<TransferRecord>) {
        if (!isLoaded) {
            _items.value = read()
            isLoaded = true
        }
        val updated = transform(_items.value)
        _items.value = updated
        write(updated)
    }

    private suspend fun read(): List<TransferRecord> {
        val raw = dataStore.data.map { prefs -> prefs[KEY_TRANSFERS] }.firstOrNull() ?: return emptyList()
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
