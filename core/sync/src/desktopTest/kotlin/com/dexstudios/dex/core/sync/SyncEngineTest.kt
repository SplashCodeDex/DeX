package com.dexstudios.dex.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 031 contract suite: deterministic convergence, offline-first semantics,
 * tombstone lifecycle, and the mechanically-enforced privacy law.
 */
class SyncEngineTest {

    /** In-memory SyncStorage — the reference semantics any adapter must reproduce. */
    private class MemoryStorage : SyncStorage {
        val records = LinkedHashMap<Pair<String, String>, SyncRecord>()
        var savedClock: HlcTimestamp? = null

        override suspend fun load(collection: String, key: String): SyncRecord? = records[collection to key]

        override suspend fun loadAll(collection: String): List<SyncRecord> = records.filterKeys { it.first == collection }.values.toList()

        override suspend fun store(record: SyncRecord) {
            records[record.collection to record.key] = record
        }

        override suspend fun purge(collection: String, key: String) {
            records.remove(collection to key)
        }

        override suspend fun loadClock(): HlcTimestamp? = savedClock

        override suspend fun storeClock(clock: HlcTimestamp) {
            savedClock = clock
        }
    }

    private class FakeTransport(var response: List<SyncRecord> = emptyList()) : SyncTransport {
        var lastExchange: List<SyncRecord> = emptyList()

        override suspend fun exchange(deltas: List<SyncRecord>): List<SyncRecord> {
            lastExchange = deltas
            return response
        }
    }

    private fun engine(storage: MemoryStorage, wall: Long = 1_000L, compactionMs: Long = SyncEngine.TOMBSTONE_COMPACTION_MS) = SyncEngine(
        storage = storage,
        clock = HybridLogicalClock(wallClock = { wall }),
        deviceId = "device-a",
        tombstoneCompactionMillis = compactionMs,
        wallClock = { wall },
    )

    private fun payload(alias: String): JsonObject = buildJsonObject {
        put("alias", alias)
        put("deviceType", "phone")
    }

    @Test
    fun `local mutate persists immediately and queues a delta`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.DEVICES, "fp-1", payload("Pixel"))

        val stored = storage.load(SyncCollections.DEVICES, "fp-1")
        assertEquals("Pixel", (stored!!.payload as JsonObject)["alias"]?.let { (it as JsonPrimitive).content })
        assertEquals(SyncEngine.SyncState.QUEUED, sync.state.value)
    }

    @Test
    fun `flush pushes deltas and merges host records`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)
        val hostPayload = buildJsonObject {
            put("theme", "dark")
            put("deviceType", "phone")
        }
        val hostRecord = SyncRecord(SyncCollections.SETTINGS, "theme", HlcTimestamp(2_000L, 0L), "device-b", hostPayload)

        sync.mutate(SyncCollections.DEVICES, "fp-1", payload("Pixel"))
        val transport = FakeTransport(response = listOf(hostRecord))
        sync.flush(transport)

        assertEquals(1, transport.lastExchange.size)
        assertEquals("Pixel", (sync.read(SyncCollections.DEVICES, "fp-1") as JsonObject)["alias"]?.let { (it as JsonPrimitive).content })
        assertEquals("dark", (sync.read(SyncCollections.SETTINGS, "theme") as JsonObject)["theme"]?.let { (it as JsonPrimitive).content })
        assertEquals(SyncEngine.SyncState.IDLE, sync.state.value)
    }

    @Test
    fun `incoming newer record wins and older record is ignored without rebroadcast`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.SETTINGS, "theme", payload("light"))
        val localRecord = storage.load(SyncCollections.SETTINGS, "theme")!!

        val newer = localRecord.copy(hlc = HlcTimestamp(localRecord.hlc.physical + 10L, 0L), deviceId = "device-b")
        assertEquals(MergeOutcome.APPLIED, sync.mergeIncoming(newer))
        assertEquals(newer, storage.load(SyncCollections.SETTINGS, "theme"))

        val older = localRecord.copy(hlc = HlcTimestamp(localRecord.hlc.physical - 10L, 0L), deviceId = "device-b")
        assertEquals(MergeOutcome.IGNORED, sync.mergeIncoming(older))
        assertEquals(newer, storage.load(SyncCollections.SETTINGS, "theme"))
    }

    @Test
    fun `same stamp different authors resolve by deviceId tiebreak deterministically`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.SETTINGS, "theme", payload("mine"))
        val local = storage.load(SyncCollections.SETTINGS, "theme")!!

        val sameStampOtherAuthor = local.copy(deviceId = "device-z")
        // device-z > device-a: the tiebreak says the incoming record wins.
        assertEquals(MergeOutcome.APPLIED, sync.mergeIncoming(sameStampOtherAuthor))
        assertEquals("device-z", storage.load(SyncCollections.SETTINGS, "theme")!!.deviceId)

        // Reverse the author: our local copy of the same instant must win.
        val sameStampEarlierAuthor = local.copy(deviceId = "device-0")
        assertEquals(MergeOutcome.IGNORED, sync.mergeIncoming(sameStampEarlierAuthor))
        assertEquals("device-z", storage.load(SyncCollections.SETTINGS, "theme")!!.deviceId)
    }

    @Test
    fun `tombstone deletes propagate and cannot be resurrected by a stale peer`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.HISTORY, "rec-1", payload("transfer"))
        sync.mutate(SyncCollections.HISTORY, "rec-1", payload = null) // the delete
        assertTrue(storage.load(SyncCollections.HISTORY, "rec-1")!!.isTombstone)
        assertNull(sync.read(SyncCollections.HISTORY, "rec-1"))

        // A slower peer re-sends the pre-delete payload: it must NOT resurrect.
        val staleWrite = storage.load(SyncCollections.HISTORY, "rec-1")!!.copy(
            hlc = HlcTimestamp(0L, 0L),
            deviceId = "device-b",
            payload = payload("stale"),
        )
        assertEquals(MergeOutcome.IGNORED, sync.mergeIncoming(staleWrite))
        assertTrue(storage.load(SyncCollections.HISTORY, "rec-1")!!.isTombstone, "a tombstone must survive stale writes")
        assertNull(sync.read(SyncCollections.HISTORY, "rec-1"))
    }

    @Test
    fun `tombstones compact only after the window and only for own records`() = kotlinx.coroutines.test.runTest {
        var wall = 1_000_000L
        val storage = MemoryStorage()
        val sync = SyncEngine(
            storage = storage,
            clock = HybridLogicalClock(wallClock = { wall }),
            deviceId = "device-a",
            tombstoneCompactionMillis = 1_000L,
            wallClock = { wall },
        )

        sync.mutate(SyncCollections.HISTORY, "rec-old", payload = null)
        wall += 2_000L // past the window
        sync.compact()
        assertNull(storage.load(SyncCollections.HISTORY, "rec-old"), "expired own tombstone must compact away")

        // Another device's tombstone never compacts on our clock.
        val foreignTombstone = SyncRecord(SyncCollections.HISTORY, "rec-foreign", HlcTimestamp(1L, 0L), "device-b", null)
        sync.mergeIncoming(foreignTombstone)
        sync.compact()
        assertTrue(storage.load(SyncCollections.HISTORY, "rec-foreign") != null, "foreign tombstones survive local compaction")
    }

    @Test
    fun `offline failure re-queues deltas for the next flush`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)
        sync.mutate(SyncCollections.SETTINGS, "theme", payload("dark"))

        val failing = object : SyncTransport {
            var calls = 0
            override suspend fun exchange(deltas: List<SyncRecord>): List<SyncRecord> {
                calls++
                throw java.io.IOException("offline")
            }
        }

        var threw = false
        try {
            sync.flush(failing)
        } catch (_: java.io.IOException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(SyncEngine.SyncState.QUEUED, sync.state.value)

        // Connectivity returns: the re-queued delta flushes cleanly.
        val ok = FakeTransport()
        sync.flush(ok)
        assertEquals(1, ok.lastExchange.size)
        assertEquals(SyncEngine.SyncState.IDLE, sync.state.value)
    }

    @Test
    fun `coalesced deltas push only the newest version per key`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.SETTINGS, "theme", payload("a"))
        sync.mutate(SyncCollections.SETTINGS, "theme", payload("b"))
        sync.mutate(SyncCollections.SETTINGS, "theme", payload("c"))

        val transport = FakeTransport()
        sync.flush(transport)
        assertEquals(1, transport.lastExchange.size, "rapid edits must coalesce to one delta")
        assertEquals("c", (transport.lastExchange.single().payload as JsonObject)["alias"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `privacy law rejects forbidden payload fields at any nesting depth`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        val evil = buildJsonObject {
            put("alias", "Pixel")
            put(
                "nested",
                buildJsonObject {
                    put("imageBase64", "secret-content")
                },
            )
        }

        var rejected = false
        try {
            sync.mutate(SyncCollections.DEVICES, "fp-1", evil)
        } catch (e: IllegalArgumentException) {
            rejected = true
            assertTrue(e.message!!.contains("PRIVACY LAW"))
        }
        assertTrue(rejected, "content-bearing payloads must never enter the sync queue")
        assertNull(storage.load(SyncCollections.DEVICES, "fp-1"))
    }

    @Test
    fun `readAll hides tombstones from feature bootstrap`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        sync.mutate(SyncCollections.DEVICES, "fp-1", payload("Pixel"))
        sync.mutate(SyncCollections.DEVICES, "fp-2", payload("Watch"))
        sync.mutate(SyncCollections.DEVICES, "fp-2", payload = null)

        val roster = sync.readAll(SyncCollections.DEVICES)
        assertEquals(setOf("fp-1"), roster.keys)
    }
}
