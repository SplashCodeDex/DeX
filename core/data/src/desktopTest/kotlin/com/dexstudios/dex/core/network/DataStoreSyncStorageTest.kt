package com.dexstudios.dex.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dexstudios.dex.core.sync.HlcTimestamp
import com.dexstudios.dex.core.sync.SyncCollections
import com.dexstudios.dex.core.sync.SyncRecord
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 031 WP2 adapter contract: the DataStore-backed [SyncStorage] implementation must
 * round-trip records + the HLC state faithfully, snapshot only its own collection, and
 * degrade corrupt entries to absent — sync data must never block startup or crash a
 * flush (the tolerance rule every persistence adapter in this repo follows).
 */
class DataStoreSyncStorageTest {

    private val tempDir = Files.createTempDirectory("dex_sync_storage_test")
    private val storePath = tempDir.resolve("sync.preferences_pb")
    private lateinit var scope: kotlinx.coroutines.CoroutineScope

    @Before
    fun setUp() {
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.coroutineContext.cancelChildren()
        tempDir.toFile().deleteRecursively()
    }

    private fun TestScope.newStore(): androidx.datastore.core.DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        produceFile = { storePath.toString().toPath() },
    )

    private fun record(key: String, hlc: HlcTimestamp = HlcTimestamp(1_000L, 0L), deviceId: String = "device-a", alias: String? = "Pixel") = SyncRecord(
        collection = SyncCollections.DEVICES,
        key = key,
        hlc = hlc,
        deviceId = deviceId,
        payload = alias?.let { buildJsonObject { put("alias", it) } },
    )

    @Test
    fun `store and load round-trip a record with payload`() = runTest {
        val storage = DataStoreSyncStorage(newStore())
        val original = record("fp-1", alias = "Pixel")

        storage.store(original)

        assertEquals(original, storage.load(SyncCollections.DEVICES, "fp-1"))
    }

    @Test
    fun `tombstone round-trips with null payload`() = runTest {
        val storage = DataStoreSyncStorage(newStore())
        val tombstone = record("fp-1", alias = null)

        storage.store(tombstone)

        val loaded = storage.load(SyncCollections.DEVICES, "fp-1")!!
        assertTrue(loaded.isTombstone)
        assertEquals(tombstone, loaded)
    }

    @Test
    fun `loadAll snapshots only the requested collection, sorted by key`() = runTest {
        val storage = DataStoreSyncStorage(newStore())

        storage.store(record("fp-b"))
        storage.store(record("fp-a"))
        storage.store(SyncRecord(SyncCollections.HISTORY, "rec-1", HlcTimestamp(2L, 0L), "device-a", buildJsonObject { put("name", "photo") }))

        val devices = storage.loadAll(SyncCollections.DEVICES)
        assertEquals(listOf("fp-a", "fp-b"), devices.map { it.key })
        assertEquals(listOf("rec-1"), storage.loadAll(SyncCollections.HISTORY).map { it.key })
    }

    @Test
    fun `purge removes every trace of a key`() = runTest {
        val storage = DataStoreSyncStorage(newStore())
        storage.store(record("fp-1"))

        storage.purge(SyncCollections.DEVICES, "fp-1")

        assertNull(storage.load(SyncCollections.DEVICES, "fp-1"))
        assertTrue(storage.loadAll(SyncCollections.DEVICES).isEmpty())
    }

    @Test
    fun `clock state round-trips through persistence`() = runTest {
        val storage = DataStoreSyncStorage(newStore())
        assertNull(storage.loadClock(), "a fresh store has no clock state")

        storage.storeClock(HlcTimestamp(12L, 34L))

        assertEquals(HlcTimestamp(12L, 34L), storage.loadClock())
    }

    @Test
    fun `clock state survives a simulated restart on the same disk store`() = runTest {
        // Production reality: Koin holds ONE DataStore; a "restart" is a new ADAPTER
        // instance over the same persistent file (DataStore forbids two live instances
        // per file, so the store itself is reused).
        val store = newStore()
        DataStoreSyncStorage(store).storeClock(HlcTimestamp(7L, 9L))

        val restarted = DataStoreSyncStorage(store)
        assertEquals(HlcTimestamp(7L, 9L), restarted.loadClock())
    }

    @Test
    fun `corrupt record entry degrades to absent instead of crashing`() = runTest {
        val store = newStore()
        // Write raw garbage where a record would live (e.g. partial write / disk corruption).
        store.edit { prefs ->
            prefs[stringPreferencesKey("sync.devices.fp-corrupt")] = "not-json{{{{"
        }

        val storage = DataStoreSyncStorage(store)

        assertNull(storage.load(SyncCollections.DEVICES, "fp-corrupt"))
        assertTrue(storage.loadAll(SyncCollections.DEVICES).isEmpty(), "corrupt entries are skipped, not fatal")
    }

    @Test
    fun `corrupt clock value degrades to null`() = runTest {
        val store = newStore()
        store.edit { prefs ->
            prefs[stringPreferencesKey("sync.hlc_clock")] = "garbage"
        }

        assertNull(DataStoreSyncStorage(store).loadClock())
    }
}
