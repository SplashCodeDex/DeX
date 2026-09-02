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
    // MANDATORY injection — same purity rule as the HLC's wallClock.
    private val wallClock: () -> Long,
) {
    private val pendingDeltas = ArrayDeque<SyncRecord>()
    private val pendingLock = Any()

    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    enum class SyncState { IDLE, QUEUED, SYNCING }

    companion object {
        /** Tombstones outlive 30 days before compaction (plan 031 default). */
        const val TOMBSTONE_COMPACTION_MS = 30L * 24 * 60 * 60 * 1000

        /** Local synced-history bound — matches the desktop UI's TransferHistory window. */
        const val MAX_SYNCED_HISTORY = 500

        /**
         * Legal collections — the CLIENT mirror of the server's door law. A hostile or
         * buggy host that answers with unknown collections would otherwise write garbage
         * rows into local storage forever (unbounded, never compacted, invisible to the
         * features). The engine enforces the same set the server does.
         */
        val LEGAL_COLLECTIONS = setOf(SyncCollections.DEVICES, SyncCollections.HISTORY, SyncCollections.SETTINGS)

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
        require(collection in LEGAL_COLLECTIONS) {
            "illegal sync collection '$collection' — devices/history/settings ONLY (plan 031 law)"
        }
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
        persistClockIfChanged()
        enqueueDelta(record)
        _state.value = SyncState.QUEUED
    }

    /**
     * Merges an INCOMING record (from the host exchange or a direct peer push).
     * Deterministic convergence: [SyncRecord.supersedes] decides; the losing side is
     * discarded and must not re-broadcast. Returns the outcome for transport batching.
     *
     * Records in unknown collections are REJECTED (the client mirror of the server's
     * door law) — a hostile host cannot plant garbage rows in local storage.
     */
    suspend fun mergeIncoming(incoming: SyncRecord): MergeOutcome {
        if (incoming.collection !in LEGAL_COLLECTIONS) return MergeOutcome.IGNORED
        validatePayload(incoming.collection, incoming.payload)

        // Causality: absorb the remote stamp so OUR next local write beats it. The
        // advanced state is persisted with the record — both sides of the restart
        // contract.
        clock.receive(incoming.hlc)
        persistClockIfChanged()

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
     *
     * An EMPTY queue still performs the exchange: pulling the host's deltas is how a
     * device with no local changes converges (fresh boot, new roster member). With a
     * cursor, an up-to-date client's exchange is a cheap no-op instead of a full
     * snapshot download; `hasMore` responses drain in a loop until caught up.
     *
     * Cancellation is honored as cancellation: a cancelled flush (shutdown, scope death)
     * re-queues its deltas and RETHROWS the CancellationException — swallowing it would
     * leave the coroutine in a zombie running state.
     */
    suspend fun flush(transport: SyncTransport) {
        val deltas = drainDeltas()
        _state.value = if (deltas.isEmpty()) SyncState.IDLE else SyncState.SYNCING
        try {
            // The persisted cursor makes an up-to-date client's exchange a no-op pull;
            // absent cursor (first contact) yields the full snapshot server-side.
            val cursor = storage.loadCursor()
            var batch = transport.exchange(deltas, sinceHostSeq = cursor)
            // Bounded drain: hasMore pages are fetched immediately until caught up. The
            // page cap keeps any single batch bounded; the loop count is bounded by the
            // tenant record cap (20k/5k = 4 worst case).
            var pages = 0
            while (true) {
                batch.records.forEach { mergeIncoming(it) }
                if (!batch.hasMore) break
                if (++pages > MAX_EXCHANGE_PAGES) {
                    // Absurd page count implies a hostile/buggy host — stop draining but
                    // still ADVANCE the cursor to what we merged; next flush re-checks.
                    break
                }
                batch = transport.exchange(emptyList(), sinceHostSeq = batch.hostSeq)
            }
            storage.storeCursor(batch.hostSeq)
            _state.value = SyncState.IDLE
        } catch (e: kotlinx.coroutines.CancellationException) {
            requeueDrained(deltas)
            _state.value = SyncState.QUEUED
            throw e
        } catch (e: Exception) {
            requeueDrained(deltas)
            _state.value = SyncState.QUEUED
            throw e
        }
    }

    private fun requeueDrained(deltas: List<SyncRecord>) {
        // Offline-first: re-queue everything for the next flush — WITHOUT regressing
        // any newer delta queued for the same key while the exchange was in flight
        // (the exchange window is exactly when new writes land).
        deltas.asReversed().forEach { requeueWithoutRegressing(it) }
    }

    /** Safety bound on hasMore drain loops (host bugs/hostility must not spin us). */
    private val MAX_EXCHANGE_PAGES = 8

    /**
     * Persists the clock state ONLY when it actually advanced. On a bootstrap snapshot
     * merge (thousands of records) the clock state rarely changes between records —
     * writing it per-record was 5k DataStore writes for one logical state change.
     */
    private var lastPersistedClock: com.dexstudios.dex.core.sync.HlcTimestamp? = null

    private suspend fun persistClockIfChanged() {
        val current = clock.now()
        if (current != lastPersistedClock) {
            storage.storeClock(current)
            lastPersistedClock = current
        }
    }

    /**
     * Compacts tombstones older than the window and bounds synced HISTORY growth.
     * Called by the platform on boot (and safe to call any time — purging a still-live
     * tombstone would risk resurrection, so the age check is conservative).
     *
     * HISTORY cap: synced transfer metadata accumulates forever on every device
     * otherwise. Oldest-first trim to [MAX_SYNCED_HISTORY] — deleted local copies are
     * NOT tombstoned (history is an append log; the trim is a local GC, exactly like
     * TransferHistory's own MAX_ENTRIES on the desktop UI side).
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

        // Local GC for the append-only history collection.
        val history = storage.loadAll(SyncCollections.HISTORY)
        if (history.size > MAX_SYNCED_HISTORY) {
            // Oldest by HLC first; the freshest MAX_SYNCED_HISTORY survive.
            val keep = history.sortedByDescending { it.hlc }.take(MAX_SYNCED_HISTORY).map { it.key }.toSet()
            history.filter { it.key !in keep }.forEach { storage.purge(SyncCollections.HISTORY, it.key) }
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

    /**
     * Failure-path re-queue: puts [record] back ONLY when no newer delta for the same
     * key was queued in the meantime (a mutate landing mid-exchange must not be
     * overwritten by the stale record the failed exchange was carrying).
     */
    private fun requeueWithoutRegressing(record: SyncRecord) {
        synchronized(pendingLock) {
            val newer = pendingDeltas.any {
                it.collection == record.collection && it.key == record.key && it.hlc > record.hlc
            }
            if (!newer) {
                enqueueDelta(record)
            }
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
