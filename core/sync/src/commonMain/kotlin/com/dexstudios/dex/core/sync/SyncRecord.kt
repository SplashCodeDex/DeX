package com.dexstudios.dex.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The synced collections (plan 031). These three and ONLY these three — adding a new
 * synced surface is a user decision (plan STOP condition), not a drive-by.
 */
object SyncCollections {
    /** Device roster: fingerprint, alias, deviceType, platform, push token. */
    const val DEVICES = "devices"

    /** Transfer history metadata (name/size/direction/peer/status — never content). */
    const val HISTORY = "history"

    /** User settings/preferences. */
    const val SETTINGS = "settings"
}

/**
 * One unit of sync: a key within a [SyncCollection], its opaque payload, and the
 * causality stamp deciding Last-Write-Wins. Deletions are tombstones (payload null)
 * that propagate before compaction removes them.
 *
 * PRIVACY LAW: the payload of a synced record may NEVER carry file content, clipboard
 * content, or paired-token VALUES. Enforcement is layered: adapters validate at the
 * boundary (plan 032 server double-checks); this model keeps the contract explicit.
 *
 * The payload is intentionally [JsonElement] — collection-specific schemas are defined
 * by the owning feature, not by the sync layer, so new fields never break old peers
 * (forward-compat by unknown-field-tolerance, same policy as the WS protocol).
 */
@Serializable
data class SyncRecord(
    /** Owning collection — one of [SyncCollections]. */
    val collection: String,
    /** Record key within the collection (e.g. a fingerprint for DEVICES, record id for HISTORY). */
    val key: String,
    /** HLC stamp of the change — LWW arbiter. */
    val hlc: HlcTimestamp,
    /** Device that authored the change — tiebreaker sanity + tombstone compaction scoping. */
    val deviceId: String,
    /** Payload as of this change; null ONLY for tombstones. */
    val payload: JsonElement? = null,
) {
    val isTombstone: Boolean get() = payload == null

    init {
        require(collection.isNotBlank()) { "collection must be named" }
        require(key.isNotBlank()) { "record key must be named" }
        require(deviceId.isNotBlank()) { "deviceId must be named" }
    }

    /** The merge rule: strict HLC order,deviceId tiebreak — deterministic on every peer. */
    fun supersedes(other: SyncRecord): Boolean {
        if (collection != other.collection || key != other.key) {
            throw IllegalArgumentException("supersedes() compares only same-collection same-key records")
        }
        val byStamp = hlc.compareTo(other.hlc)
        return when {
            byStamp > 0 -> true
            byStamp < 0 -> false
            else -> deviceId > other.deviceId
        }
    }
}

/** Outcome of merging an incoming record into the local sync set. */
enum class MergeOutcome {
    /** Incoming record won — local state changed. */
    APPLIED,

    /** Local record won — incoming discarded (and must NOT be re-broadcast). */
    IGNORED,

    /** Identical stamp+author — nothing to do. */
    NOOP,
}
