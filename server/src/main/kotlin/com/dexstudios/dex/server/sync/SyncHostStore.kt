package com.dexstudios.dex.server.sync

import com.dexstudios.dex.core.sync.SyncCollections
import com.dexstudios.dex.core.sync.SyncLimits
import com.dexstudios.dex.core.sync.SyncRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory tenant store for the sync host surface (plan 032).
 *
 * Tenancy: every record lives under the verified googleSub subtree — `sub -> (collection
 * -> key -> record)`. Cross-tenant access is structurally impossible: the tenant id comes
 * ONLY from the verified ID token, never from request data.
 *
 * Merge rule: [SyncRecord.supersedes] — the SAME deterministic HLC-LWW law every peer
 * applies — so the host converges with clients regardless of arrival order.
 *
 * DELTA SYNC: every accepted record is stamped with the tenant's monotonic [Sequenced.seq];
 * clients keep the returned hostSeq as their cursor and receive only newer records
 * ([snapshotSince]) — an up-to-date client gets an empty list instead of the full subtree.
 *
 * Persistence: memory-only by design for the CX22 first cut (a restart costs a delta
 * re-push from clients — the exchange protocol is idempotent). Durable storage is a
 * later plan decision if/when sync data outgrows the process lifetime.
 */
class SyncHostStore {

    /** Host-stamped record: the original plus the tenant's monotonic sequence. */
    internal data class Sequenced(val record: SyncRecord, val seq: Long)

    private val tenants =
        ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<String, Sequenced>>>()

    /** Per-tenant monotonic host sequence — the delta-sync cursor source. */
    private val tenantSeq = ConcurrentHashMap<String, AtomicLong>()

    /** Legal collections enforced at the door — mirrors the client's SyncCollections law. */
    private val legalCollections = setOf(SyncCollections.DEVICES, SyncCollections.HISTORY, SyncCollections.SETTINGS)

    companion object {
        /**
         * Per-tenant record bound (all collections combined): a runaway or hostile client
         * in one account must never be able to OOM the host process. The client's history
         * cap (MAX_ENTRIES) and roster size are far below this; the bound only ever
         * triggers on abuse.
         */
        const val MAX_RECORDS_PER_TENANT = 20_000
    }

    /** Merges an incoming delta; returns true when the host state changed. */
    fun merge(tenant: String, incoming: SyncRecord): Boolean {
        if (incoming.collection !in legalCollections) return false
        val tenantStore = tenants.computeIfAbsent(tenant) { ConcurrentHashMap() }
        val collection = tenantStore.computeIfAbsent(incoming.collection) { ConcurrentHashMap() }
        val existing = collection[incoming.key]
        return when {
            existing == null -> {
                // New-key write at the tenant cap is rejected (existing keys still
                // update — a cap must never wedge an account's ability to sync).
                if (recordsFor(tenant) >= MAX_RECORDS_PER_TENANT) {
                    return false
                }
                collection[incoming.key] = Sequenced(incoming, nextSeq(tenant))
                true
            }

            incoming.supersedes(existing.record) -> {
                collection[incoming.key] = Sequenced(incoming, nextSeq(tenant))
                true
            }

            else -> false
        }
    }

    /** The tenant's current sequence — the cursor clients persist for delta windows. */
    fun currentSeq(tenant: String): Long = tenantSeq[tenant]?.get() ?: 0L

    /**
     * Delta snapshot: every record with seq > [since], ordered by seq (the cursor
     * contract), batched to [limit] with the hasMore flag. `since = 0` yields the
     * full snapshot (bootstrap / cursor loss).
     */
    fun snapshotSince(tenant: String, since: Long, limit: Int = SyncLimits.MAX_RECORDS_PER_RESPONSE): Pair<List<SyncRecord>, Boolean> {
        val tenantStore = tenants[tenant] ?: return emptyList<SyncRecord>() to false
        val all = tenantStore.values
            .flatMap { it.values.toList() }
            .filter { it.seq > since }
            .sortedBy { it.seq }
        val page = all.take(limit)
        return page.map { it.record } to (all.size > page.size)
    }

    /**
     * FULL snapshot (legacy path): everything in the tenant's subtree. Retained for
     * first contacts without a cursor and diagnostics.
     */
    fun snapshot(tenant: String): List<SyncRecord> = tenants[tenant]?.values?.flatMap { it.values.map { s -> s.record } } ?: emptyList()

    /** Direct read for tests/diagnostics. */
    fun record(tenant: String, collection: String, key: String): SyncRecord? = tenants[tenant]?.get(collection)?.get(key)?.record

    /** Test seam: clears a tenant subtree. */
    fun clearTenant(tenant: String) {
        tenants.remove(tenant)
        tenantSeq.remove(tenant)
    }

    private fun recordsFor(tenant: String): Int = tenants[tenant]?.values?.sumOf { it.size } ?: 0

    private fun nextSeq(tenant: String): Long = tenantSeq.computeIfAbsent(tenant) { AtomicLong() }.incrementAndGet()
}
