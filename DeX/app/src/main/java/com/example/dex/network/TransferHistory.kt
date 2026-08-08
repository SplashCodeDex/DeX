package com.example.dex.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TransferRecord(
    val id: String,
    val name: String,
    val size: Long,
    val timestamp: Long,
    val direction: String,
    val uri: String? = null
)

object TransferHistory {
    private const val PREFS = "dex_history_prefs"
    private const val KEY = "transfers"
    private const val MAX_ENTRIES = 200

    private val _items = MutableStateFlow<List<TransferRecord>>(emptyList())
    val items: StateFlow<List<TransferRecord>> = _items.asStateFlow()

    fun init(context: Context) {
        _items.value = read(context)
    }

    fun refresh(context: Context) {
        _items.value = read(context)
    }

    fun log(context: Context, name: String, size: Long, direction: String, uri: String? = null) {
        val record = TransferRecord(
            id = UUID.randomUUID().toString(),
            name = name,
            size = size,
            timestamp = System.currentTimeMillis(),
            direction = direction,
            uri = uri
        )
        val updated = (listOf(record) + _items.value).take(MAX_ENTRIES)
        _items.value = updated
        write(context, updated)
    }

    private fun read(context: Context): List<TransferRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        TransferRecord(
                            id = o.optString("id", i.toString()),
                            name = o.optString("name", "unknown"),
                            size = o.optLong("size", 0L),
                            timestamp = o.optLong("timestamp", 0L),
                            direction = o.optString("direction", "received"),
                            uri = if (o.has("uri")) o.optString("uri") else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, items: List<TransferRecord>) {
        val arr = JSONArray()
        items.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("size", r.size)
            o.put("timestamp", r.timestamp)
            o.put("direction", r.direction)
            r.uri?.let { o.put("uri", it) }
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
