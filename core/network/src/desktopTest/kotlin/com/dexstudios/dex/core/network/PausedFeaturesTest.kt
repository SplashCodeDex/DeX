package com.dexstudios.dex.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the deliberate feature pauses documented in [PausedFeatures].
 *
 * Flipping a pause back on is a product decision plus a checklist (transport trust gate,
 * capture-side trust check, frame bounds, UI entry point) — never a drive-by `if (true)`.
 * These assertions make an accidental re-enable fail loudly instead of silently shipping a
 * half-audited surface.
 */
class PausedFeaturesTest {

    @Test
    fun `screen mirroring stays paused until the resume checklist is executed`() {
        assertTrue(PausedFeatures.isPaused(PausedFeatures.SCREEN_MIRROR), "Screen mirroring must stay paused")
        assertFalse(PausedFeatures.SCREEN_MIRROR_ENABLED, "Re-enable mirroring only with the PausedFeatures checklist")
        assertEquals(setOf(PausedFeatures.SCREEN_MIRROR), PausedFeatures.pausedFeatures)
    }

    @Test
    fun `a paused feature reports a notice and an unknown feature is not paused`() {
        assertTrue(PausedFeatures.pauseNotice(PausedFeatures.SCREEN_MIRROR).isNotBlank())
        assertFalse(PausedFeatures.isPaused("transfer"), "Only deliberately paused features are reported as paused")
    }
}
