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
        var savedCursor: Long? = null

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

        override suspend fun loadCursor(): Long? = savedCursor

        override suspend fun storeCursor(cursor: Long) {
            savedCursor = cursor
        }
    }

    private class FakeTransport(var response: SyncExchangeBatch = SyncExchangeBatch(emptyList(), 0L, false)) : SyncTransport {
        var lastExchange: List<SyncRecord> = emptyList()
        var lastSince: Long? = null

        override suspend fun exchange(deltas: List<SyncRecord>, sinceHostSeq: Long?): SyncExchangeBatch {
            lastExchange = deltas
            lastSince = sinceHostSeq
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
        val transport = FakeTransport(response = SyncExchangeBatch(listOf(hostRecord), hostSeq = 7L, hasMore = false))
        sync.flush(transport)

        assertEquals(1, transport.lastExchange.size)
        assertEquals("Pixel", (sync.read(SyncCollections.DEVICES, "fp-1") as JsonObject)["alias"]?.let { (it as JsonPrimitive).content })
        assertEquals("dark", (sync.read(SyncCollections.SETTINGS, "theme") as JsonObject)["theme"]?.let { (it as JsonPrimitive).content })
        assertEquals(SyncEngine.SyncState.IDLE, sync.state.value)
        assertEquals(7L, storage.savedCursor, "the host cursor must be persisted after a clean flush")
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
            override suspend fun exchange(deltas: List<SyncRecord>, sinceHostSeq: Long?): SyncExchangeBatch {
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
    fun `a failure re-queue never regresses a newer delta queued mid-exchange`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)
        sync.mutate(SyncCollections.SETTINGS, "theme", payload("old-value"))

        val failing = object : SyncTransport {
            override suspend fun exchange(deltas: List<SyncRecord>, sinceHostSeq: Long?): SyncExchangeBatch {
                // Mid-exchange, the user writes a NEWER value for the same key.
                sync.mutate(SyncCollections.SETTINGS, "theme", payload("new-value"))
                throw java.io.IOException("offline")
            }
        }

        try {
            sync.flush(failing)
        } catch (_: java.io.IOException) {
            // expected
        }

        val ok = FakeTransport()
        sync.flush(ok)
        // The stale pre-exchange delta must NOT have overwritten the newer write.
        val pushed = ok.lastExchange.single { it.key == "theme" }
        assertTrue(
            (pushed.payload as JsonObject)["alias"]?.let { (it as JsonPrimitive).content } == "new-value",
            "the newest write must win the re-queue, got ${pushed.payload}",
        )
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

    @Test
    fun `a restart with a restored clock never regresses against its own prior writes`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        var wall = 1_000_000L

        // Session 1: write, persist clock, then "restart".
        val clock1 = com.dexstudios.dex.core.sync.HybridLogicalClock(wallClock = { wall })
        val sync1 = SyncEngine(storage, clock1, "device-a", wallClock = { wall })
        sync1.mutate(SyncCollections.SETTINGS, "theme", payload("session-1-value"))

        // Session 2: NEW clock + engine over the SAME storage, clock restored from
        // persistence (the production restart path). A naive fresh clock would reissue
        // physical=wall and LOSE to session 1's record in every future conflict.
        val clock2 = com.dexstudios.dex.core.sync.HybridLogicalClock(wallClock = { wall })
        val saved = storage.loadClock()
        if (saved != null) clock2.restore(saved)
        val sync2 = SyncEngine(storage, clock2, "device-a", wallClock = { wall })
        sync2.mutate(SyncCollections.SETTINGS, "theme", payload("session-2-value"))

        // The session-2 write must supersede the session-1 write (same device, later).
        val stored = storage.load(SyncCollections.SETTINGS, "theme")!!
        assertEquals("session-2-value", (stored.payload as JsonObject)["alias"]?.let { (it as JsonPrimitive).content })
        // And a hostile "resend" of the session-1 record must now LOSE the merge.
        val staleResend = storage.load(SyncCollections.SETTINGS, "theme")!!.copy(
            hlc = com.dexstudios.dex.core.sync.HlcTimestamp(saved!!.physical, saved.counter),
            payload = payload("stale-session-1"),
        )
        assertEquals(MergeOutcome.IGNORED, sync2.mergeIncoming(staleResend))
    }

    @Test
    fun `the client enforces the legal-collections law on incoming records`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        // A hostile/buggy host answers with an unknown collection: REJECTED locally,
        // no garbage row in storage, no crash.
        val hostile = SyncRecord("clipboard-content", "c1", HlcTimestamp(9_999L, 0L), "device-evil", payload("leak"))
        assertEquals(MergeOutcome.IGNORED, sync.mergeIncoming(hostile))
        assertEquals(null, storage.load("clipboard-content", "c1"))

        // Crafted prefix variants (devicez/devices-x) must not slip through either.
        val crafted = SyncRecord("devices-x", "c2", HlcTimestamp(9_999L, 0L), "device-evil", payload("x"))
        assertEquals(MergeOutcome.IGNORED, sync.mergeIncoming(crafted))
        assertEquals(null, storage.load("devices-x", "c2"))
    }

    @Test
    fun `mutate rejects illegal collections with a contract error`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        val sync = engine(storage)

        var rejected = false
        try {
            sync.mutate("arbitrary-junk", "k1", payload("junk"))
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected, "illegal collections must be refused at the API surface")
        assertTrue(storage.records.isEmpty(), "nothing may be written for illegal collections")
    }

    @Test
    fun `compact bounds the synced history to the freshest records`() = kotlinx.coroutines.test.runTest {
        val storage = MemoryStorage()
        var wall = 1_000L
        val sync = engine(storage, wall = wall)
        // 602 records (2 over the cap) with strictly increasing stamps.
        repeat(602) { i ->
            wall += 10
            sync.mutate(SyncCollections.HISTORY, "rec-$i", payload("item-$i"))
        }

        sync.compact()

        val survivors = storage.loadAll(SyncCollections.HISTORY)
        assertEquals(SyncEngine.MAX_SYNCED_HISTORY, survivors.size, "history must be trimmed to the cap")
        // The FRESHEST survive: rec-601 and rec-600 exist, rec-000 is gone.
        assertTrue(survivors.any { it.key == "rec-601" }, "newest record survives")
        assertTrue(survivors.any { it.key == "rec-600" }, "second-newest record survives")
        assertTrue(survivors.none { it.key == "rec-000" }, "oldest record is trimmed")
        assertTrue(survivors.none { it.key == "rec-001" }, "second-oldest record is trimmed")
    }
}
