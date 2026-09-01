package com.dexstudios.dex.core.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 031 STOP condition: the HLC must be property-tested before any real data flows.
 * These tests are the release gate for the sync layer's conflict resolution.
 */
class HybridLogicalClockTest {

    @Test
    fun `local ticks are strictly monotonic under a frozen wall clock`() {
        var wall = 1_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        val stamps = (1..100).map { clock.tick() }

        // Wall clock frozen: counter must keep the ordering strictly increasing.
        assertTrue(stamps.zipWithNext().all { (a, b) -> a < b }, "every tick must beat the previous")
        assertEquals(1_000L, stamps.last().physical)
        // First tick adopts the fresh physical time at counter 0; the remaining 99 increment it.
        assertEquals(99L, stamps.last().counter)
    }

    @Test
    fun `local ticks are strictly monotonic when the wall clock jumps backwards`() {
        var wall = 10_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        val t1 = clock.tick()
        wall = 5_000L // NTP correction yanks the clock backwards mid-operation
        val t2 = clock.tick()

        assertTrue(t2 > t1, "a backwards wall clock must never drag the HLC backwards")
        assertEquals(10_000L, t2.physical, "physical time holds at the last-seen maximum")
    }

    @Test
    fun `wall clock moving forward resets the counter`() {
        var wall = 1_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        clock.tick()
        clock.tick()
        wall = 2_000L
        val t3 = clock.tick()

        assertEquals(HlcTimestamp(2_000L, 0L), t3)
    }

    @Test
    fun `receive beats both the remote and every previously issued local timestamp`() {
        var wall = 1_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        val localBefore = clock.tick()
        // A remote device with a badly skewed FUTURE clock sends a record.
        val remote = HlcTimestamp(9_999L, 7L)
        val received = clock.receive(remote)

        assertTrue(received > localBefore, "receive must beat prior local stamps")
        assertTrue(received > remote, "receive must beat the remote stamp it absorbed")
        assertEquals(9_999L, received.physical, "the skewed future physical time is respected (max rule)")
    }

    @Test
    fun `receive from a stale past remote never moves the clock backwards`() {
        var wall = 5_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        val localBefore = clock.tick()
        val staleRemote = HlcTimestamp(100L, 50L)
        val received = clock.receive(staleRemote)

        assertTrue(received > localBefore, "a stale remote must still yield a fresh stamp")
        assertTrue(received.physical >= 5_000L, "physical never regresses below local max")
    }

    @Test
    fun `receive at the same physical instant increments the counter past the remote`() {
        var wall = 1_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        val remote = HlcTimestamp(1_000L, 3L)
        val received = clock.receive(remote)

        assertEquals(HlcTimestamp(1_000L, 4L), received)
    }

    @Test
    fun `a skewed phone cannot win conflicts against an honest device via LWW`() {
        // The scenario that killed wall-clock LWW: phone clock is 24h fast.
        val honestWall = 1_000_000L
        val honest = HybridLogicalClock(wallClock = { honestWall })
        val skewed = HybridLogicalClock(wallClock = { honestWall + 86_400_000L })

        // Honest device writes AFTER the skewed device wrote its poisoned record...
        val poisonedWrite = skewed.tick()
        val honestWrite = honest.tick()

        // ...in pure wall-clock LWW the poisoned write would "win" (bigger timestamp).
        assertTrue(poisonedWrite.physical > honestWrite.physical, "fixture sanity: skew is real")

        // But once the honest device RECEIVES the poisoned record, its next write
        // causally supersedes it — the user's later action wins, regardless of skew.
        honest.receive(poisonedWrite)
        val correctiveWrite = honest.tick()
        assertTrue(correctiveWrite > poisonedWrite, "post-receive local writes must supersede the poisoned record")
    }

    @Test
    fun `restore only ever advances the clock`() {
        var wall = 1_000L
        val clock = HybridLogicalClock(wallClock = { wall })

        clock.tick()
        clock.tick()
        val persisted = clock.now()

        // A restart that loads a STALE persisted clock (backup of an older session).
        clock.restore(HlcTimestamp(10L, 2L))
        assertEquals(persisted, clock.now(), "stale restore must be ignored")

        // A restart that loads a NEWER persisted clock (drifted, or restored forward).
        clock.restore(HlcTimestamp(9_999L, 0L))
        assertEquals(HlcTimestamp(9_999L, 0L), clock.now(), "forward restore must stick")

        val next = clock.tick()
        assertTrue(next > HlcTimestamp(9_999L, 0L), "ticks continue past the restored state")
    }

    @Test
    fun `timestamp comparison is total and matches the encoded form ordering`() {
        val ordered = listOf(
            HlcTimestamp(1L, 0L),
            HlcTimestamp(1L, 1L),
            HlcTimestamp(1L, 99L),
            HlcTimestamp(2L, 0L),
            HlcTimestamp(2L, 1L),
        )
        assertTrue(ordered.zipWithNext().all { (a, b) -> a < b })
        assertTrue(ordered.sortedDescending().zipWithNext().all { (a, b) -> a > b })
        assertEquals(0, HlcTimestamp(5L, 5L).compareTo(HlcTimestamp(5L, 5L)))
    }

    @Test
    fun `timestamp round-trips through the canonical string form`() {
        for (ts in listOf(HlcTimestamp(0L, 0L), HlcTimestamp(42L, 7L), HlcTimestamp(Long.MAX_VALUE, Long.MAX_VALUE))) {
            assertEquals(ts, HlcTimestamp.parse(ts.toString()), "round trip failed for $ts")
        }
        // Negative components are not a valid wire form: parse must fail closed.
        assertEquals(null, HlcTimestamp.parse(HlcTimestamp(-3L, 9L).toString()))
        assertEquals(null, HlcTimestamp.parse("garbage"))
        assertEquals(null, HlcTimestamp.parse("1.2.3"))
        assertEquals(null, HlcTimestamp.parse("x.y"))
    }

    @Test
    fun `timestamp serializes as a JSON primitive via kotlinx`() {
        val ts = HlcTimestamp(12L, 34L)
        val json = Json.encodeToString(HlcTimestamp.serializer(), ts)
        assertTrue(json.contains("12") && json.contains("34"), "encoded form carries both components: $json")
        assertEquals(ts, Json.decodeFromString(HlcTimestamp.serializer(), json))
    }

    @Test
    fun `concurrent ticks from a shared clock never issue duplicates`() {
        val clock = HybridLogicalClock(wallClock = { 500L })
        val threads = (1..8).map {
            Thread {
                repeat(1_000) { clock.tick() }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 8000 total ticks from counter 0 at a frozen wall: ends at 7999 (first is 0).
        assertEquals(HlcTimestamp(500L, 7_999L), clock.now())
    }

    @Test
    fun `parse rejects negative counter forms rather than throwing`() {
        // Not a valid form on our wire, but must fail closed (null), never crash.
        assertEquals(null, HlcTimestamp.parse("-1.-1"))
    }
}
