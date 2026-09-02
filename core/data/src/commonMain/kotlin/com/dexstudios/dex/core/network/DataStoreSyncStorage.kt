package com.dexstudios.dex.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dexstudios.dex.core.sync.HlcTimestamp
import com.dexstudios.dex.core.sync.SyncRecord
import com.dexstudios.dex.core.sync.SyncStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Desktop adapter for the sync-layer persistence port (plan 031 WP2): stores one
 * [SyncRecord] per (collection, key) in Preferences DataStore, plus the persisted HLC
 * state. Record payloads are stored as serialized JSON strings — metadata only by
 * construction (the sync layer's privacy law already rejects content-bearing payloads
 * before anything reaches this adapter).
 *
 * Tolerance contract (mirrors DeviceManager/TransferHistory): a corrupt entry degrades
 * to absent — sync data must never block app startup or crash a flush.
 */
class DataStoreSyncStorage(private val dataStore: DataStore<Preferences>) : SyncStorage {

    @Serializable
    private data class StoredRecord(val collection: String, val key: String, val hlcPhysical: Long, val hlcCounter: Long, val deviceId: String, val payload: String?) {
        fun toRecord(): SyncRecord = SyncRecord(
            collection = collection,
            key = key,
            hlc = HlcTimestamp(hlcPhysical, hlcCounter),
            deviceId = deviceId,
            payload = payload?.let { Json.parseToJsonElement(it) },
        )

        companion object {
            fun from(record: SyncRecord): StoredRecord = StoredRecord(
                collection = record.collection,
                key = record.key,
                hlcPhysical = record.hlc.physical,
                hlcCounter = record.hlc.counter,
                deviceId = record.deviceId,
                payload = record.payload?.let { Json.encodeToString(it) },
            )
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Storage key: `sync.<collection>.<key>`. INVARIANT (defense-in-depth for the
     * engine's legal-collections law): neither segment may contain dots — otherwise a
     * crafted collection could collide with another collection's prefix namespace in
     * [loadAll]'s prefix scan. The engine already blocks unknown collections; this
     * guard keeps the adapter independently safe.
     */
    private fun recordKey(collection: String, key: String): String {
        require(!collection.contains('.') && !key.contains('.')) {
            "dots are namespace separators in sync storage keys"
        }
        return "sync.$collection.$key"
    }

    private fun StoredRecord.decode(): SyncRecord? = runCatching {
        // Stored payload strings were encoded as JsonElement trees; parse back safely.
        copy(payload = payload).toRecord()
    }.getOrNull()

    private fun decode(raw: String?): SyncRecord? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val stored = json.decodeFromString<StoredRecord>(raw)
            stored.toRecord()
        }.getOrNull()
    }

    override suspend fun load(collection: String, key: String): SyncRecord? {
        val raw = dataStore.data.map { it[stringPreferencesKey(recordKey(collection, key))] }.firstOrNull()
        return decode(raw)
    }

    override suspend fun loadAll(collection: String): List<SyncRecord> {
        val prefix = "sync.$collection."
        val prefs = dataStore.data.first()
        return prefs.asMap().entries
            .filter { (k, _) -> k.name.startsWith(prefix) }
            .mapNotNull { (_, v) -> decode(v as? String) }
            .sortedBy { it.key }
    }

    override suspend fun store(record: SyncRecord) {
        val stored = StoredRecord.from(record)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(recordKey(record.collection, record.key))] = json.encodeToString(stored)
        }
    }

    override suspend fun purge(collection: String, key: String) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(recordKey(collection, key)))
        }
    }

    override suspend fun loadClock(): HlcTimestamp? {
        val raw = dataStore.data.map { it[KEY_CLOCK] }.firstOrNull() ?: return null
        return HlcTimestamp.parse(raw)
    }

    override suspend fun storeClock(clock: HlcTimestamp) {
        dataStore.edit { prefs ->
            prefs[KEY_CLOCK] = clock.toString()
        }
    }

    override suspend fun loadCursor(): Long? = dataStore.data.map { it[KEY_CURSOR]?.toLongOrNull() }.firstOrNull()

    override suspend fun storeCursor(cursor: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_CURSOR] = cursor.toString()
        }
    }

    private companion object {
        val KEY_CLOCK = stringPreferencesKey("sync.hlc_clock")

        /** Delta-sync cursor: the host sequence this device has fully merged. */
        val KEY_CURSOR = stringPreferencesKey("sync.host_seq_cursor")
    }
}
