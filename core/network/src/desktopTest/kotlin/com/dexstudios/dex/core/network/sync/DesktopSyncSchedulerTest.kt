package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.sync.HlcTimestamp
import com.dexstudios.dex.core.sync.SyncCollections
import com.dexstudios.dex.core.sync.SyncEngine
import com.dexstudios.dex.core.sync.SyncExchangeBatch
import com.dexstudios.dex.core.sync.SyncRecord
import com.dexstudios.dex.core.sync.SyncTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 031 contract suite for the desktop scheduler — the loop that closes the client
 * side: cadence, disabled-state idling (no host configured), sign-out idling (queue
 * retained, not dropped), and failure backoff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSyncSchedulerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: java.nio.file.Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dex_scheduler_test")
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        testDispatcher.scheduler.cancelChildren()
        tempDir.toFile().deleteRecursively()
    }

    /** Counting transport: records every exchange and answers programmatically. */
    private class RecordingTransport(var behavior: suspend (Int) -> SyncExchangeBatch = { SyncExchangeBatch(emptyList(), 0L, false) }) : SyncTransport {
        val calls = ArrayList<List<SyncRecord>>()

        override suspend fun exchange(deltas: List<SyncRecord>, sinceHostSeq: Long?): SyncExchangeBatch {
            calls.add(deltas)
            return behavior(calls.size)
        }
    }

    /** In-memory storage — NO real file IO: virtual-time tests must never suspend on
     *  a real Dispatchers.IO, or the clock races past uncompleted flushes. */
    private class MemoryStorage : com.dexstudios.dex.core.sync.SyncStorage {
        private val records = LinkedHashMap<Pair<String, String>, SyncRecord>()
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

    /** Mutable one-field holder standing in for the settings surface the loop reads. */
    private class HostSetting(var url: String)

    private fun deviceConfig(syncHost: String): HostSetting = HostSetting(syncHost)

    private fun engine(deviceId: String = "device-a") = SyncEngine(
        storage = MemoryStorage(),
        clock = com.dexstudios.dex.core.sync.HybridLogicalClock(wallClock = { 1_000L }),
        deviceId = deviceId,
        wallClock = { 1_000L },
    )

    private suspend fun SyncEngine.enqueueSample(key: String) = mutate(SyncCollections.SETTINGS, key, kotlinx.serialization.json.buildJsonObject { put("k", key) })

    @Test
    fun `flushes on the configured cadence while enabled`() = runTest(testDispatcher) {
        val config = deviceConfig("https://sync.example.com")
        val syncEngine = engine()
        syncEngine.enqueueSample("theme")
        val transport = RecordingTransport()
        val scheduler = DesktopSyncScheduler(
            engine = syncEngine,
            transport = transport,
            syncHostUrlProvider = { config.url },
            tokenProvider = { "live-token" },
            scope = backgroundScope,
            flushIntervalMillis = 60_000L,
            jitterMillis = 0L,
        )

        scheduler.start()
        runCurrent()
        advanceTimeBy(60_001L)
        runCurrent()
        advanceTimeBy(60_001L)
        runCurrent()

        assertTrue(transport.calls.size >= 3, "expected >= 3 flushes (immediate + 2 cadence ticks), got ${transport.calls.size}")
        assertEquals(listOf("theme"), transport.calls.first().map { it.key })
        scheduler.stop()
    }

    @Test
    fun `empty host url idles without exchanging`() = runTest(testDispatcher) {
        val config = deviceConfig("") // sync disabled: no host configured
        val syncEngine = engine()
        syncEngine.enqueueSample("theme")
        val transport = RecordingTransport()
        val scheduler = DesktopSyncScheduler(
            engine = syncEngine,
            transport = transport,
            syncHostUrlProvider = { config.url },
            tokenProvider = { "live-token" },
            scope = backgroundScope,
            flushIntervalMillis = 60_000L,
            jitterMillis = 0L,
        )

        scheduler.start()
        advanceTimeBy(300_000L)
        runCurrent()

        assertEquals(0, transport.calls.size, "disabled sync must never exchange")
        // The queued delta survives for whenever sync gets configured.
        assertEquals(1, syncEngine.readAll(SyncCollections.SETTINGS).size)
        scheduler.stop()
    }

    @Test
    fun `signed out idles without exchanging and retains the queue`() = runTest(testDispatcher) {
        val config = deviceConfig("https://sync.example.com")
        val syncEngine = engine()
        syncEngine.enqueueSample("theme")
        val transport = RecordingTransport()
        val scheduler = DesktopSyncScheduler(
            engine = syncEngine,
            transport = transport,
            syncHostUrlProvider = { config.url },
            tokenProvider = { null }, // signed out
            scope = backgroundScope,
            flushIntervalMillis = 60_000L,
            jitterMillis = 0L,
        )

        scheduler.start()
        advanceTimeBy(300_000L)
        runCurrent()

        assertEquals(0, transport.calls.size, "signed-out sync must never exchange")
        assertEquals(1, syncEngine.readAll(SyncCollections.SETTINGS).size, "queue must be retained, not dropped")
        scheduler.stop()
    }

    @Test
    fun `failure backs off instead of busy-looping`() = runTest(testDispatcher) {
        val config = deviceConfig("https://sync.example.com")
        val syncEngine = engine()
        syncEngine.enqueueSample("theme")
        val transport = RecordingTransport(behavior = { throw java.io.IOException("host unreachable") })
        val scheduler = DesktopSyncScheduler(
            engine = syncEngine,
            transport = transport,
            syncHostUrlProvider = { config.url },
            tokenProvider = { "live-token" },
            scope = backgroundScope,
            flushIntervalMillis = 60_000L,
            jitterMillis = 0L,
        )

        scheduler.start()
        runCurrent()
        advanceTimeBy(60_001L)
        runCurrent()
        advanceTimeBy(60_001L)
        runCurrent()

        // Attempt at t=0 + one per interval (backoff shares the cadence): exactly 3 in
        // 2 intervals — a busy loop would fire far more, a dead loop would fire 1.
        assertEquals(3, transport.calls.size, "failure cadence: attempt -> backoff -> retry per interval, got ${transport.calls.size}")
        scheduler.stop()
    }

    @Test
    fun `a settings change to the host url takes effect without restart`() = runTest(testDispatcher) {
        val config = deviceConfig("") // disabled at boot
        val syncEngine = engine()
        syncEngine.enqueueSample("theme")
        val transport = RecordingTransport()
        val scheduler = DesktopSyncScheduler(
            engine = syncEngine,
            transport = transport,
            syncHostUrlProvider = { config.url },
            tokenProvider = { "live-token" },
            scope = backgroundScope,
            flushIntervalMillis = 60_000L,
            jitterMillis = 0L,
        )

        scheduler.start()
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0, transport.calls.size, "still disabled")

        config.url = "https://sync.example.com" // user configures the host mid-run
        advanceTimeBy(60_000L)
        runCurrent()

        assertTrue(transport.calls.size >= 1, "the next cadence tick must pick up the new host")
        scheduler.stop()
    }
}
