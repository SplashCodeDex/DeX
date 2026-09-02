package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.sync.SyncEngine
import com.dexstudios.dex.core.sync.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The desktop SyncScheduler (plan 031, closing the client loop): periodically drains the
 * engine's queued deltas through the transport while sync is usable, and backs off on
 * failure so an unreachable host never becomes a busy loop.
 *
 * Enabled gate: a non-empty sync-host URL (resolved per iteration so settings changes
 * apply without a restart). Empty = the user has not configured a sync host; the loop
 * stays idle and queued deltas simply wait (offline queue semantics — nothing is lost).
 *
 * Sign-in gate: no live ID token means the transport would 401 anyway; skipping keeps
 * the queue intact until the next sign-in refreshes the token.
 *
 * Lifetime: [start] launches the loop into [scope]; [stop] cancels it. Wired to the
 * server lifecycle (DeXServer) and the desktop shutdown coordinator.
 */
class DesktopSyncScheduler(
    private val engine: SyncEngine,
    private val transport: SyncTransport,
    private val syncHostUrlProvider: () -> String,
    private val tokenProvider: suspend () -> String?,
    private val scope: CoroutineScope,
    // Test seam: deterministic cadence (backoff shares it).
    private val flushIntervalMillis: Long = FLUSH_INTERVAL_MS,
    // Test seam: zero jitter keeps virtual-time assertions exact; production uses
    // randomized jitter so a fleet of devices never flushes on the same wall-clock grid.
    private val jitterMillis: Long = DEFAULT_JITTER_MS,
) {
    companion object {
        /** Steady-state cadence: cheap (one empty delta push when nothing changed). */
        const val FLUSH_INTERVAL_MS = 5 * 60 * 1000L

        /**
         * Thundering-herd jitter: ±30s spread across the device fleet so synchronized
         * flush grids (every device hitting the host at :00/:05/:10...) become smooth.
         * Randomized per sleep, bounded — never enough to feel like a stalled sync.
         */
        const val DEFAULT_JITTER_MS = 30_000L
    }

    private var loop: Job? = null

    /** Interval + bounded random jitter (zero when the seam disables it). */
    private suspend fun sleepWithJitter() {
        val jitter = if (jitterMillis <= 0L) {
            0L
        } else {
            (0..jitterMillis).random() - (jitterMillis / 2)
        }
        kotlinx.coroutines.delay(flushIntervalMillis + jitter.coerceAtLeast(0L))
    }

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                val url = syncHostUrlProvider()
                if (url.isBlank()) {
                    // Sync disabled: idle-wait, re-check the setting periodically.
                    sleepWithJitter()
                    continue
                }

                val token = tokenProvider()
                if (token.isNullOrBlank()) {
                    sleepWithJitter()
                    continue
                }

                try {
                    // A no-delta flush still pulls host records (merge + convergence);
                    // failures throw, the queue survives, backoff follows.
                    engine.flush(transport)
                    sleepWithJitter()
                } catch (e: Exception) {
                    // Unreachable host: back off (cadence-magnitude), never busy-loop.
                    sleepWithJitter()
                }
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }
}
