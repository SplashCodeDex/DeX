package com.dexstudios.dex.core.sync

/**
 * Hybrid Logical Clock (plan 031).
 *
 * Timestamps are `pt . lc` — physical wall time in the high bits, logical counter in
 * the low bits. Comparison is total and causal: a message timestamp always beats its
 * sender's prior timestamps, and receiving one never moves the clock backwards.
 *
 * WHY THIS EXISTS: plain wall-clock Last-Write-Wins breaks silently on device clock
 * skew — a phone with a wrong date "wins" every conflict. HLC gives LWW semantics that
 * are immune to skew while still keeping timestamps roughly wall-clock-meaningful for
 * human display and tombstone compaction windows.
 *
 * NOT a substitute for NTP: values are only guaranteed causally consistent WITHIN the
 * sync set, never globally ordered against unrelated events.
 *
 * Thread-safety: internal state is guarded by a lock — one clock instance may be shared
 * by all of a device's sync paths.
 */
class HybridLogicalClock(
    /** Wall-clock source; injectable for deterministic tests and virtual time. */
    private val wallClock: () -> Long = { com.dexstudios.dex.core.network.HashUtils.currentTimeMillis() },
    initialPhysical: Long = 0L,
    initialCounter: Long = 0L,
) {
    @Volatile
    private var physical = initialPhysical

    @Volatile
    private var counter = initialCounter

    private val lock = Any()

    /**
     * Issues the timestamp for a LOCAL event (a record being created/updated on this
     * device). Monotonic even when the wall clock stalls or jumps backwards.
     */
    fun tick(): HlcTimestamp {
        synchronized(lock) {
            val now = wallClock()
            return if (now > physical) {
                physical = now
                counter = 0L
                HlcTimestamp(physical, 0L)
            } else {
                counter += 1L
                HlcTimestamp(physical, counter)
            }
        }
    }

    /**
     * Absorbs a REMOTE timestamp (from a record arriving over sync) and issues the
     * timestamp for the receive event. Guarantees the result beats BOTH our clock and
     * the remote's — causality across the wire.
     */
    fun receive(remote: HlcTimestamp): HlcTimestamp {
        synchronized(lock) {
            val now = wallClock()
            val maxPhysical = maxOf(now, physical, remote.physical)
            val nextCounter = if (maxPhysical == remote.physical && physical == remote.physical) {
                maxOf(counter, remote.counter) + 1L
            } else if (maxPhysical == remote.physical) {
                remote.counter + 1L
            } else if (maxPhysical == physical || maxPhysical == now) {
                counter + 1L
            } else {
                0L
            }
            physical = maxPhysical
            counter = nextCounter
            return HlcTimestamp(physical, counter)
        }
    }

    /** Current clock value for persistence/serialization (survives process restart). */
    fun now(): HlcTimestamp = synchronized(lock) { HlcTimestamp(physical, counter) }

    /**
     * Restores persisted state — only ever advances (a stale persisted clock must never
     * drag the live clock backwards past events it already issued).
     */
    fun restore(saved: HlcTimestamp) {
        synchronized(lock) {
            if (saved.physical > physical || (saved.physical == physical && saved.counter > counter)) {
                physical = saved.physical
                counter = saved.counter
            }
        }
    }
}

/**
 * The comparable timestamp carried by every [SyncRecord]. Physical time (ms since epoch)
 * in [physical]; logical disambiguator in [counter].
 */
@kotlinx.serialization.Serializable
data class HlcTimestamp(val physical: Long, val counter: Long) : Comparable<HlcTimestamp> {
    override fun compareTo(other: HlcTimestamp): Int = compareValuesBy(this, other, { it.physical }, { it.counter })

    companion object {
        fun parse(raw: String): HlcTimestamp? {
            val parts = raw.split('.')
            if (parts.size != 2) return null
            val physical = parts[0].toLongOrNull() ?: return null
            if (physical < 0L) return null
            val counter = parts[1].toLongOrNull() ?: return null
            if (counter < 0L) return null
            return HlcTimestamp(physical, counter)
        }
    }

    /** Canonical wire/persistence form: `"<physical>.<counter>"`. */
    override fun toString(): String = "$physical.$counter"
}
