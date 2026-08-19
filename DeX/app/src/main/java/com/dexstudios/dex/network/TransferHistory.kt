package com.dexstudios.dex.network

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

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

object TransferHistory {
    private const val PREFS = "dex_history_prefs"
    private const val KEY = "transfers"
    private const val MAX_ENTRIES = 200

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _items = MutableStateFlow<List<TransferRecord>>(emptyList())
    val items: StateFlow<List<TransferRecord>> = _items.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun init(context: Context) {
        scope.launch {
            _items.value = read(context)
        }
    }

    fun refresh(context: Context) {
        scope.launch {
            _items.value = read(context)
        }
    }

    fun delete(context: Context, id: String) {
        val current = _items.value
        val updated = current.filter { it.id != id }
        if (current.size != updated.size) {
            _items.value = updated
            scope.launch {
                write(context, updated)
            }
        }
    }

    fun clear(context: Context) {
        _items.value = emptyList()
        scope.launch {
            write(context, emptyList())
        }
    }

    fun log(
        context: Context,
        name: String,
        size: Long,
        direction: String,
        uri: String? = null,
        peerDevice: String? = null,
        status: String = "success",
        timestamp: Long = System.currentTimeMillis()
    ) {
        val record = TransferRecord(
            id = UUID.randomUUID().toString(),
            name = name,
            size = size,
            timestamp = timestamp,
            direction = direction,
            uri = uri,
            peerDevice = peerDevice,
            status = status
        )
        val current = _items.value
        val updated = (listOf(record) + current).take(MAX_ENTRIES)
        _items.value = updated
        scope.launch {
            write(context, updated)
        }
    }

    private fun read(context: Context): List<TransferRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<TransferRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, items: List<TransferRecord>) {
        try {
            val raw = json.encodeToString(items)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY, raw)
            }
        } catch (_: Exception) {
            // Log error if needed
        }
    }
}
