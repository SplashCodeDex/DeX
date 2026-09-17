package com.dexstudios.dex.core.network

/**
 * Features that are DELIBERATELY PAUSED and must stay inert until the pause is lifted
 * explicitly. This object is the single place that records the decision, the reason, and
 * the exact steps a future change must take to resume the feature.
 *
 * Why a registry instead of scattered `if (false)` guards: a paused feature has to be
 * discoverable (one grep answers "is X shipping?"), auditable (the resume checklist lives
 * beside the flag), and impossible to half-enable by accident. Every consumer reads the
 * same constant, so a resume is a one-line change plus the checklist — not a hunt.
 *
 * Rules for paused features:
 * - Transport surfaces (wire handlers, server routes) must IGNORE paused traffic at the
 *   boundary instead of processing it and discarding the result. Inert is not enough: the
 *   parsing and side effects themselves are the risk.
 * - UI entry points must not offer the feature, and the feature's window/panel must state
 *   the pause rather than render a live stream.
 * - Pausing is never a substitute for a security fix: an unauthenticated path that happens
 *   to reach a paused feature is still an unauthenticated path.
 */
object PausedFeatures {

    /** Screen mirroring (phone display streamed to the desktop mirror window). */
    const val SCREEN_MIRROR = "screen-mirror"

    /**
     * Screen mirroring is PAUSED (user directive, 2026-09-17).
     *
     * Rationale: mirroring is not an immediate feature; product focus is on the core
     * transfer / pairing / sync surfaces. Capture-side bandwidth, MediaProjection consent
     * flow, and the desktop frame-decoding pipeline are therefore frozen as-is and must not
     * be extended, polished, or half-enabled while paused.
     *
     * While this is `false`:
     * - `/ws` on the desktop host ignores `mirror-start` / `mirror-config` / `mirror-stop`
     *   and inbound binary frames instead of feeding them to the mirror engine.
     * - The Android client ignores `mirror-start` / `mirror-stop` (no capture is started).
     * - The desktop UI does not open the mirror window.
     *
     * Resume checklist (all of it, in one change, before flipping this to `true`):
     * 1. Re-audit the `/ws` inbound control plane: every message that causes a local side
     *    effect must be gated on a TRUSTED session (see the gate table in `WebSocketRoutes`).
     *    Mirror frames/config are a local side effect and an unauthenticated peer must never
     *    be able to drive them.
     * 2. Confirm the Android capture path re-validates the requesting peer's trust before
     *    starting MediaProjection (never trust the frame alone).
     * 3. Bound the frame path (size, rate, and back-pressure) and verify CPU/bandwidth on a
     *    real device; the desktop decodes every frame it receives.
     * 4. Re-enable the UI entry point and the pause notice in the same change so the code and
     *    the docs cannot disagree about whether mirroring ships.
     */
    const val SCREEN_MIRROR_ENABLED = false

    /** Every feature currently paused, for greppable/auditable reporting. */
    val pausedFeatures: Set<String> = buildSet {
        if (!SCREEN_MIRROR_ENABLED) add(SCREEN_MIRROR)
    }

    fun isPaused(feature: String): Boolean = feature in pausedFeatures

    /** One-line, user-facing phrasing for logs and placeholder UI. */
    fun pauseNotice(feature: String): String = when (feature) {
        SCREEN_MIRROR -> "Screen mirroring is paused in this release and is not available."
        else -> "This feature is paused in this release."
    }
}
