package com.dexstudios.dex.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for the sync layer (plan 031). Implementations live in the platform
 * layers (desktop: DataStore via core/data) — the engine itself stays storage-agnostic
 * so every peer reuses the merge logic verbatim.
 */
interface SyncStorage {
    /** Loads the local record for a key, or null when absent. */
    suspend fun load(collection: String, key: String): SyncRecord?

    /** Loads every local record in a collection (snapshot for delta exchange + bootstrapping). */
    suspend fun loadAll(collection: String): List<SyncRecord>

    /** Persists a record (insert-or-replace — the engine guarantees only winners land here). */
    suspend fun store(record: SyncRecord)

    /** Drops every trace of a key (tombstone compaction — NOT the delete path). */
    suspend fun purge(collection: String, key: String)

    /** Loads the persisted HLC state, or null on first run. */
    suspend fun loadClock(): HlcTimestamp?

    /** Persists the HLC state (survives restarts — monotonic restore contract). */
    suspend fun storeClock(clock: HlcTimestamp)
}

/**
 * Transport port for the sync host exchange (plan 031 client contract; the server
 * surface itself is plan 032). Implementations own authentication (Google ID Token),
 * TLS, retries, and offline buffering policy.
 */
interface SyncTransport {
    /**
     * Pushes local deltas (records the host may not have) and returns the records the
     * host held that we must merge. The exchange is idempotent: re-sending a record
     * the host already has is a no-op.
     */
    suspend fun exchange(deltas: List<SyncRecord>): List<SyncRecord>
}

/** Emits local mutations so the engine can queue them for the next exchange. */
interface SyncChangeSource {
    val changes: Flow<SyncRecord>
}

/** Connectivity/lifecycle port so the engine can schedule flushes per platform. */
interface SyncScheduler {
    /** Called when the queue becomes non-empty; implementation decides when to flush. */
    fun scheduleFlush()
}
