package com.dexstudios.dex.platform

/**
 * Single source of truth for all DeX dock card layout metrics.
 *
 * Every window-placement calculation (resting dock, magnetic snapping, expansion nudge,
 * sanity clamping, drag hit-testing) and every rendered Compose dimension MUST derive
 * from these constants. Never restate a value inline elsewhere.
 *
 * All values are expressed in dp (logical pixels).
 */
object DockCardMetrics {
    /** Transparent bounding OS-window canvas the card is anchored inside. */
    const val CANVAS_WIDTH = 1420
    const val CANVAS_HEIGHT = 760

    /** Inset between the canvas edge and the card container. */
    const val CARD_MARGIN = 25

    /** Overhang of the transparent canvas beyond the work-area edge at resting dock. */
    const val RESTING_CANVAS_OVERHANG = 12

    const val CARD_WIDTH_CONTRACTED = 320
    const val CARD_HEIGHT_CONTRACTED = 430

    const val SETTINGS_WIDTH_EXPANDED = 675
    const val PAIRING_WIDTH_EXPANDED = 400
    const val FILE_EXPLORER_WIDTH_EXPANDED = 1054
    const val CARD_HEIGHT_EXPANDED = 625
}
