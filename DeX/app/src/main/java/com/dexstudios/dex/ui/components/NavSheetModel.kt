package com.dexstudios.dex.ui.components

import kotlin.math.abs

/**
 * Pure geometry + settle decision for the 3-tier floating sheet (plan 043).
 *
 * Extracted verbatim from `NavBottomSheet.kt` so the tier math and the
 * drag-commit decision are unit-testable and the composable stays a thin
 * renderer. Every branch below reproduces the ORIGINAL onDragEnd/onDragCancel
 * logic exactly — values, comparisons, thresholds and haptic triggers.
 */

/** Height geometry for the three tiers, from a measured container. */
internal class NavSheetGeometry(
    val totalHeightPx: Float,
    val halfGapPx: Float,
    val highGapPx: Float,
) {
    // Tier heights (card height from the anchored bottom)
    val halfHeightPx: Float = (totalHeightPx * 0.50f) - halfGapPx
    val highHeightPx: Float = (totalHeightPx * 0.80f) - highGapPx
    val fullHeightPx: Float = totalHeightPx

    private val tierHeights: List<Pair<SheetTier, Float>> = listOf(
        SheetTier.Half to halfHeightPx,
        SheetTier.High to highHeightPx,
        SheetTier.Full to fullHeightPx
    )

    fun tierHeight(tier: SheetTier): Float = when (tier) {
        SheetTier.Half -> halfHeightPx
        SheetTier.High -> highHeightPx
        SheetTier.Full -> fullHeightPx
    }

    /** Closest settled tier for a current active height. */
    fun currentTierFor(heightPx: Float): SheetTier =
        tierHeights.minByOrNull { abs(it.second - heightPx) }?.first ?: SheetTier.Half

    /** Live expansion fraction: 0.0f at Half/50% to 1.0f at Full/100%. */
    fun expansionFractionFor(heightPx: Float): Float =
        if (fullHeightPx <= halfHeightPx) 0f
        else ((heightPx - halfHeightPx) / (fullHeightPx - halfHeightPx)).coerceIn(0f, 1f)

    /** Dynamic floating margin in dp: 6dp at 50%, 3dp at 80%, 0dp at 100%. */
    fun gapDpFor(heightPx: Float): Float {
        val h = heightPx
        return when {
            h <= halfHeightPx -> 6f
            h <= highHeightPx -> {
                val f = if (highHeightPx > halfHeightPx) ((h - halfHeightPx) / (highHeightPx - halfHeightPx)).coerceIn(0f, 1f) else 0f
                6f - 3f * f // 6dp -> 3dp
            }
            else -> {
                val f = if (fullHeightPx > highHeightPx) ((h - highHeightPx) / (fullHeightPx - highHeightPx)).coerceIn(0f, 1f) else 0f
                3f - 3f * f // 3dp -> 0dp
            }
        }
    }

    /** Corner radii in dp: 4 rounded corners at 50%/80%, seamless top corners at 100%. */
    fun cornerRadiiFor(heightPx: Float): NavSheetRadii {
        val h = heightPx
        return when {
            h <= highHeightPx -> {
                val f = if (highHeightPx > 0f) (h / highHeightPx).coerceIn(0f, 1f) else 0f
                val r = 44f - 8f * f // ~39dp at 50% -> 36dp at 80%
                NavSheetRadii(r, r, r, r)
            }
            else -> {
                val f = if (fullHeightPx > highHeightPx) ((h - highHeightPx) / (fullHeightPx - highHeightPx)).coerceIn(0f, 1f) else 0f
                val topR = 36f - 8f * f      // 36dp at 80% -> 28dp at 100%
                val bottomR = 36f * (1f - f) // 36dp at 80% -> 0dp at 100%
                NavSheetRadii(topR, topR, bottomR, bottomR)
            }
        }
    }

    /** Nearest settled height for the onDragCancel path. */
    fun nearestTierHeightFor(heightPx: Float): Float {
        val h = heightPx
        return when {
            h < halfHeightPx -> halfHeightPx
            h > fullHeightPx -> fullHeightPx
            else -> tierHeight(currentTierFor(h))
        }
    }
}

/** Corner radii in dp (topStart, topEnd, bottomStart, bottomEnd). */
internal data class NavSheetRadii(
    val topStartDp: Float,
    val topEndDp: Float,
    val bottomStartDp: Float,
    val bottomEndDp: Float,
)

/** The decision a drag release settles into — maps 1:1 to the old onDragEnd body. */
internal sealed interface SheetSettleAction {
    /** Animate to 0, then fire onDismiss. */
    data object Dismiss : SheetSettleAction

    /** Animate to [tier]; perform the haptic tick when [triggerHaptic]. */
    data class SettleTo(val tier: SheetTier, val triggerHaptic: Boolean) : SheetSettleAction
}
/**
 * Dimming scrim alpha as sheet expands past 50%:
 * 0.0 at expansionFraction <= 0.15, ramping linearly to 0.75 at expansionFraction = 1.0.
 */
internal fun baseScrimAlphaFor(expansionFraction: Float): Float =
    ((expansionFraction - 0.15f) / 0.85f).coerceIn(0f, 1f) * 0.75f

/**
 * Drag-release decision engine (verbatim port of onDragEnd/onDragCancel):
 * fast-fling commit first, then position-based settling per drag-start tier.
 */
internal class NavSheetDecider(
    private val geometry: NavSheetGeometry,
    private val dismissThresholdPx: Float,
    private val flingVelocityThresholdPx: Float,
    private val dragCommitThresholdPx: Float,
    private val flingDismissBoundPx: Float,
) {
    val halfHeightPx: Float get() = geometry.halfHeightPx
    val highHeightPx: Float get() = geometry.highHeightPx
    val fullHeightPx: Float get() = geometry.fullHeightPx

    fun settleAfterDrag(dragStartTier: SheetTier, currentHeightPx: Float, velocityY: Float): SheetSettleAction {
        val h = currentHeightPx

        // 1. Fast downward fling (> threshold)
        if (velocityY > flingVelocityThresholdPx) {
            // Fast fling down from near or below 50%: dismiss; otherwise collapse to 50%
            return if (h <= halfHeightPx + flingDismissBoundPx) {
                SheetSettleAction.Dismiss
            } else {
                SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false)
            }
        }

        // 2. Fast upward fling (< -threshold)
        if (velocityY < -flingVelocityThresholdPx) {
            val nextTier = if (dragStartTier == SheetTier.Half) {
                if (h > highHeightPx) SheetTier.Full else SheetTier.High
            } else {
                SheetTier.Full
            }
            return SheetSettleAction.SettleTo(nextTier, triggerHaptic = nextTier != dragStartTier)
        }

        // 3. Position-based settling (effortless directional commit)
        if (h <= halfHeightPx - dismissThresholdPx) return SheetSettleAction.Dismiss
        if (h < halfHeightPx) return SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false)
        if (h > fullHeightPx) return SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false)

        return when (dragStartTier) {
            SheetTier.Full -> {
                if (h <= fullHeightPx - dragCommitThresholdPx) {
                    SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false)
                } else {
                    SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false)
                }
            }
            SheetTier.High -> {
                if (h <= highHeightPx - dragCommitThresholdPx) {
                    SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = true)
                } else if (h >= highHeightPx + dragCommitThresholdPx) {
                    SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true)
                } else {
                    SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = false)
                }
            }
            SheetTier.Half -> {
                if (h >= fullHeightPx - dragCommitThresholdPx) {
                    SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true)
                } else if (h >= halfHeightPx + dragCommitThresholdPx) {
                    SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = true)
                } else {
                    SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false)
                }
            }
        }
    }

    fun settleAfterCancel(currentHeightPx: Float): SheetSettleAction {
        val nearestTier = when {
            currentHeightPx < halfHeightPx -> SheetTier.Half
            currentHeightPx > fullHeightPx -> SheetTier.Full
            else -> geometry.currentTierFor(currentHeightPx)
        }
        return SheetSettleAction.SettleTo(nearestTier, triggerHaptic = false)
    }
}