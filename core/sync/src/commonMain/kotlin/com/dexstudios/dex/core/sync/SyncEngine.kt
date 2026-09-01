package com.dexstudios.dex.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sync engine (plan 031): offline-first, metadata-only, HLC-resolved.
 *
 * LOCAL DATA IS THE SOURCE OF TRUTH. Every mutation is written to [SyncStorage]
 * immediately and queued; the network is a deferred optimization. When [flush] runs
 * (platform scheduler decides when), deltas exchange with the host and incoming
 * records merge by the deterministic HLC-LWW rule — every peer converges on the same
 * winners regardless of arrival order.
 *
 * Deletions are TOMBSTONES (payload=null records), never purges: a purge would let a
 * stale peer resurrect the deleted record. Tombstones compact after the compaction
 * window (default 30 days) — far past any realistic offline peer — via [compact].
 *
 * PRIVACY LAW: this engine moves [SyncRecord]s only — the models forbid content
 * fields, adapters validate, the server re-checks (plan 032).
 */
class SyncEngine(
    private val storage: SyncStorage,
    private val clock: HybridLogicalClock,
    private val deviceId: String,
    // Test seam: deterministic compaction threshold.
    private val tombstoneCompactionMillis: Long = TOMBSTONE_COMPACTION_MS,
    // Wall-clock for compaction age math only (never ordering — that is the HLC's job).
    private val wallClock: () -> Long = { com.dexstudios.dex.core.network.HashUtils.currentTimeMillis() },
) {
    private val pendingDeltas = ArrayDeque<SyncRecord>()
    private val pendingLock = Any()

    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    enum class SyncState { IDLE, QUEUED, SYNCING }

    companion object {
        /** Tombstones outlive 30 days before compaction (plan 031 default). */
        const val TOMBSTONE_COMPACTION_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * Privacy law, mechanically enforced: these payload field names must NEVER
         * appear in a synced record's payload. Server re-checks the same list (032).
         */
        val FORBIDDEN_PAYLOAD_FIELDS = setOf("imageBase64", "token", "pairToken", "text", "content", "bytes")
    }

    /**
     * Records a LOCAL mutation: stamps it with the HLC, persists it, queues the delta.
     * The local write happens unconditionally — offline-first means this NEVER waits
     * on the network.
     */
    suspend fun mutate(collection: String, key: String, payload: kotlinx.serialization.json.JsonElement?) {
        require(collection.isNotBlank() && key.isNotBlank())
        validatePayload(collection, payload)

        val stamp = clock.tick()
        val record = SyncRecord(
            collection = collection,
            key = key,
            hlc = stamp,
            deviceId = deviceId,
            payload = payload,
        )
        storage.store(record)
        enqueueDelta(record)
        _state.value = SyncState.QUEUED
    }

    /**
     * Merges an INCOMING record (from the host exchange or a direct peer push).
     * Deterministic convergence: [SyncRecord.supersedes] decides; the losing side is
     * discarded and must not re-broadcast. Returns the outcome for transport batching.
     */
    suspend fun mergeIncoming(incoming: SyncRecord): MergeOutcome {
        validatePayload(incoming.collection, incoming.payload)

        // Causality: absorb the remote stamp so OUR next local write beats it.
        clock.receive(incoming.hlc)

        val local = storage.load(incoming.collection, incoming.key)
        return when {
            local == null -> {
                // No local record: an incoming tombstone for an unknown key still
                // persists (guards against resurrection by a slower third peer).
                storage.store(incoming)
                MergeOutcome.APPLIED
            }

            incoming.supersedes(local) -> {
                storage.store(incoming)
                MergeOutcome.APPLIED
            }

            local.supersedes(incoming) || (local.hlc == incoming.hlc && local.deviceId == incoming.deviceId) -> {
                MergeOutcome.IGNORED
            }

            else -> {
                // Same stamp, different authors: the tiebreak already resolved in
                // supersedes(); this branch is unreachable-by-construction.
                MergeOutcome.IGNORED
            }
        }
    }

    /**
     * Flushes queued deltas through the transport and merges the host's response.
     * Safe to retry: the exchange is idempotent and losing records are discarded
     * server-side by the same rule.
     */
    suspend fun flush(transport: SyncTransport) {
        val deltas = drainDeltas()
        if (deltas.isEmpty()) {
            _state.value = SyncState.IDLE
            return
        }
        _state.value = SyncState.SYNCING
        try {
            val hostRecords = transport.exchange(deltas)
            hostRecords.forEach { mergeIncoming(it) }
            _state.value = SyncState.IDLE
        } catch (e: Exception) {
            // Offline-first: re-queue everything; the next scheduled flush retries.
            deltas.asReversed().forEach { enqueueDelta(it) }
            _state.value = SyncState.QUEUED
            throw e
        }
    }

    /**
     * Compacts tombstones older than the window. Called by the platform on boot
     * (and safe to call any time — purging a still-live tombstone would risk
     * resurrection, so the age check is conservative).
     */
    suspend fun compact() {
        val cutoffWall = wallClock() - tombstoneCompactionMillis
        for (collection in listOf(SyncCollections.DEVICES, SyncCollections.HISTORY, SyncCollections.SETTINGS)) {
            storage.loadAll(collection).forEach { record ->
                if (record.isTombstone && record.deviceId == deviceId) {
                    // Only OUR tombstones: another peer's tombstone may still be in
                    // flight to a device we have not talked to since it was minted.
                    val wallAgeMs = wallClock() - record.hlc.physical
                    if (wallAgeMs > tombstoneCompactionMillis && record.hlc.physical <= cutoffWall) {
                        storage.purge(record.collection, record.key)
                    }
                }
            }
        }
    }

    /** Loads a local record's payload for the owning feature (offline-first reads). */
    suspend fun read(collection: String, key: String): kotlinx.serialization.json.JsonElement? = storage.load(collection, key)?.payload

    /** Snapshot reads for feature bootstrap (e.g. roster at app start). */
    suspend fun readAll(collection: String): Map<String, kotlinx.serialization.json.JsonElement> = storage.loadAll(collection).filterNot { it.isTombstone }.associate { it.key to it.payload!! }

    private fun enqueueDelta(record: SyncRecord) {
        synchronized(pendingLock) {
            // Coalesce: only the newest delta per (collection,key) needs pushing.
            pendingDeltas.removeAll { it.collection == record.collection && it.key == record.key }
            pendingDeltas.addLast(record)
        }
    }

    private fun drainDeltas(): List<SyncRecord> = synchronized(pendingLock) {
        val drained = pendingDeltas.toList()
        pendingDeltas.clear()
        drained
    }

    private fun validatePayload(collection: String, payload: kotlinx.serialization.json.JsonElement?) {
        if (payload == null) return
        jsonElementIterator(payload).forEach { element ->
            if (element is kotlinx.serialization.json.JsonObject) {
                element.keys.forEach { field ->
                    require(field !in FORBIDDEN_PAYLOAD_FIELDS) {
                        "PRIVACY LAW violation: '$field' may never appear in a synced $collection payload " +
                            "(content and credentials are never synced — plan 031)"
                    }
                }
            }
        }
    }
}

/**
 * Walks every nested element of a JSON tree (depth-first) — the privacy validator
 * needs to inspect objects at any nesting level, not just the root.
 */
private fun jsonElementIterator(root: kotlinx.serialization.json.JsonElement): Sequence<kotlinx.serialization.json.JsonElement> = sequence {
    val stack = ArrayDeque<kotlinx.serialization.json.JsonElement>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        yield(element)
        when (element) {
            is kotlinx.serialization.json.JsonObject -> element.values.forEach { stack.addLast(it) }
            is kotlinx.serialization.json.JsonArray -> element.forEach { stack.addLast(it) }
            else -> Unit
        }
    }
}
