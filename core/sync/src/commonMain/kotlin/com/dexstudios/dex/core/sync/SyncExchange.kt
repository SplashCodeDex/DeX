package com.dexstudios.dex.core.sync

import kotlinx.serialization.Serializable

/**
 * REST wire contract between a peer's SyncTransport and the sync host (plan 031/032).
 * Defined ONCE here — the server (032) consumes this exact module, so the HTTP
 * exchange format can never drift between client and server.
 *
 * Auth: every request carries `Authorization: Bearer <googleIdToken>`; the host
 * verifies it and derives the tenant subtree from the verified `googleSub` — the
 * SAME identity namespace the trust model already uses (never the Firebase UID).
 */
@Serializable
data class SyncExchangeRequest(
    /** The caller's device identity (records' deviceId) — validated against the token tenant. */
    val deviceId: String,
    /** Records the caller believes the host may be missing (already coalesced per key). */
    val deltas: List<SyncRecord>,
    /**
     * The host sequence the caller last merged (from a previous response's
     * [SyncExchangeResponse.hostSeq]). Absent/0 on first contact = the host sends its
     * full snapshot (the bootstrap path). This is the delta-sync cursor: an
     * up-to-date client receives an EMPTY record list instead of the whole subtree —
     * the difference between a 5-minute full-snapshot download and a no-op.
     */
    val sinceHostSeq: Long = 0L,
)

@Serializable
data class SyncExchangeResponse(
    /** Records the host holds that supersede or extend the caller's view (merge by HLC-LWW). */
    val records: List<SyncRecord>,
    /** The host's current sequence — persist client-side and send as the next cursor. */
    val hostSeq: Long = 0L,
    /** True when [records] was truncated to the response batch cap; the client should immediately re-exchange with the returned cursor to drain the backlog. */
    val hasMore: Boolean = false,
)

/** Response batch cap shared by the host and every client (law: one definition). */
object SyncLimits {
    /** Max records in one exchange response — bounds host memory + client parse cost. */
    const val MAX_RECORDS_PER_RESPONSE = 5_000
}

/** Endpoint paths — law for both sides (host + every peer). */
object SyncEndpoints {
    const val EXCHANGE = "/sync/v1/exchange"
}
