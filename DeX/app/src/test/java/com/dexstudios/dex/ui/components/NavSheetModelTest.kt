package com.dexstudios.dex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavSheetModelTest {

    private val totalHeightPx = 1000f
    private val halfGapPx = 10f
    private val highGapPx = 5f

    private val geometry = NavSheetGeometry(
        totalHeightPx = totalHeightPx,
        halfGapPx = halfGapPx,
        highGapPx = highGapPx
    )

    private val decider = NavSheetDecider(
        geometry = geometry,
        dismissThresholdPx = 50f,
        flingVelocityThresholdPx = 350f,
        dragCommitThresholdPx = 36f,
        flingDismissBoundPx = 30f
    )

    // --- Geometry Golden Values ---

    @Test
    fun geometryTierHeights_matchExpectedMath() {
        // half = 1000 * 0.50 - 10 = 490
        assertEquals(490f, geometry.halfHeightPx, 0.001f)
        // high = 1000 * 0.80 - 5 = 795
        assertEquals(795f, geometry.highHeightPx, 0.001f)
        // full = 1000
        assertEquals(1000f, geometry.fullHeightPx, 0.001f)

        assertEquals(490f, geometry.tierHeight(SheetTier.Half), 0.001f)
        assertEquals(795f, geometry.tierHeight(SheetTier.High), 0.001f)
        assertEquals(1000f, geometry.tierHeight(SheetTier.Full), 0.001f)
    }

    @Test
    fun expansionFraction_boundsAndInterpolation() {
        // At or below half: 0.0
        assertEquals(0.0f, geometry.expansionFractionFor(490f), 0.001f)
        assertEquals(0.0f, geometry.expansionFractionFor(400f), 0.001f)

        // At full: 1.0
        assertEquals(1.0f, geometry.expansionFractionFor(1000f), 0.001f)
        assertEquals(1.0f, geometry.expansionFractionFor(1100f), 0.001f)

        // Midpoint between half (490) and full (1000): (745 - 490) / 510 = 0.5
        assertEquals(0.5f, geometry.expansionFractionFor(745f), 0.001f)
    }

    @Test
    fun dynamicGapDp_boundsAndInterpolation() {
        // At or below half: 6dp
        assertEquals(6f, geometry.gapDpFor(490f), 0.001f)
        assertEquals(6f, geometry.gapDpFor(400f), 0.001f)

        // At high: 3dp
        assertEquals(3f, geometry.gapDpFor(795f), 0.001f)

        // At full: 0dp
        assertEquals(0f, geometry.gapDpFor(1000f), 0.001f)
        assertEquals(0f, geometry.gapDpFor(1100f), 0.001f)
    }

    @Test
    fun cornerRadii_boundsAndInterpolation() {
        // At 0: 44dp on all 4 corners
        val r0 = geometry.cornerRadiiFor(0f)
        assertEquals(44f, r0.topStartDp, 0.001f)
        assertEquals(44f, r0.bottomStartDp, 0.001f)

        // At highHeight (795): 36dp on all corners
        val rHigh = geometry.cornerRadiiFor(795f)
        assertEquals(36f, rHigh.topStartDp, 0.001f)
        assertEquals(36f, rHigh.bottomStartDp, 0.001f)

        // At fullHeight (1000): top = 28dp, bottom = 0dp
        val rFull = geometry.cornerRadiiFor(1000f)
        assertEquals(28f, rFull.topStartDp, 0.001f)
        assertEquals(28f, rFull.topEndDp, 0.001f)
        assertEquals(0f, rFull.bottomStartDp, 0.001f)
        assertEquals(0f, rFull.bottomEndDp, 0.001f)
    }

    @Test
    fun currentTierFor_settlesToNearest() {
        assertEquals(SheetTier.Half, geometry.currentTierFor(490f))
        assertEquals(SheetTier.Half, geometry.currentTierFor(550f))
        assertEquals(SheetTier.High, geometry.currentTierFor(795f))
        assertEquals(SheetTier.High, geometry.currentTierFor(700f))
        assertEquals(SheetTier.Full, geometry.currentTierFor(950f))
        assertEquals(SheetTier.Full, geometry.currentTierFor(1000f))
    }

    @Test
    fun nearestTierHeightFor_boundsClamped() {
        assertEquals(490f, geometry.nearestTierHeightFor(300f), 0.001f)
        assertEquals(1000f, geometry.nearestTierHeightFor(1200f), 0.001f)
        assertEquals(795f, geometry.nearestTierHeightFor(800f), 0.001f)
    }

    // --- Scrim Formula Golden Values ---

    @Test
    fun baseScrimAlpha_matchesGoldenRamp() {
        // Formula: ((expansionFraction - 0.15f) / 0.85f).coerceIn(0f, 1f) * 0.75f
        assertEquals(0.0f, baseScrimAlphaFor(0.0f), 0.001f)
        assertEquals(0.0f, baseScrimAlphaFor(0.15f), 0.001f)
        assertEquals(0.0f, baseScrimAlphaFor(0.10f), 0.001f)
        assertEquals(0.75f, baseScrimAlphaFor(1.0f), 0.001f)
        assertEquals(0.75f, baseScrimAlphaFor(1.2f), 0.001f)

        // Midpoint: fraction = 0.15 + 0.85/2 = 0.575 -> alpha = 0.5 * 0.75 = 0.375
        assertEquals(0.375f, baseScrimAlphaFor(0.575f), 0.001f)
    }

    // --- Drag-Release Decisions (NavSheetDecider) ---

    @Test
    fun fastDownwardFling_nearOrBelowHalf_dismisses() {
        // halfHeightPx is 490. flingDismissBound is 30. Threshold is 520.
        val actionBelow = decider.settleAfterDrag(SheetTier.Half, 450f, 400f)
        assertEquals(SheetSettleAction.Dismiss, actionBelow)

        val actionAtBound = decider.settleAfterDrag(SheetTier.Half, 520f, 400f)
        assertEquals(SheetSettleAction.Dismiss, actionAtBound)
    }

    @Test
    fun fastDownwardFling_aboveHalf_collapsesToHalfWithoutHaptic() {
        // Fling down from above 520 collapses to Half
        val action = decider.settleAfterDrag(SheetTier.High, 700f, 400f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), action)

        val actionFromFull = decider.settleAfterDrag(SheetTier.Full, 900f, 500f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), actionFromFull)
    }

    @Test
    fun fastUpwardFling_fromHalf() {
        // From Half with height <= high (795) -> High with haptic
        val action1 = decider.settleAfterDrag(SheetTier.Half, 600f, -400f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = true), action1)

        // From Half with height > high (795) -> Full with haptic
        val action2 = decider.settleAfterDrag(SheetTier.Half, 850f, -400f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true), action2)
    }

    @Test
    fun fastUpwardFling_fromHighAndFull() {
        // From High -> Full with haptic
        val actionHigh = decider.settleAfterDrag(SheetTier.High, 800f, -400f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true), actionHigh)

        // From Full -> Full without haptic (since nextTier == dragStartTier)
        val actionFull = decider.settleAfterDrag(SheetTier.Full, 1000f, -400f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false), actionFull)
    }

    @Test
    fun positionBased_dismissBelowThreshold() {
        // halfHeight (490) - dismissThreshold (50) = 440
        val action = decider.settleAfterDrag(SheetTier.Half, 440f, 0f)
        assertEquals(SheetSettleAction.Dismiss, action)

        val actionDeeper = decider.settleAfterDrag(SheetTier.Half, 300f, 0f)
        assertEquals(SheetSettleAction.Dismiss, actionDeeper)
    }

    @Test
    fun positionBased_belowHalf_recoversToHalf() {
        // Between 440 and 490: recovers to Half without haptic
        val action = decider.settleAfterDrag(SheetTier.Half, 460f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), action)
    }

    @Test
    fun positionBased_overscrollAboveFull_recoversToFull() {
        val action = decider.settleAfterDrag(SheetTier.Full, 1050f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false), action)
    }

    @Test
    fun positionBased_commitFromFull() {
        // fullHeight (1000) - dragCommitThreshold (36) = 964
        // At or below 964: commits to Half
        val actionCommit = decider.settleAfterDrag(SheetTier.Full, 960f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), actionCommit)

        // Above 964: springs back to Full
        val actionStay = decider.settleAfterDrag(SheetTier.Full, 980f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false), actionStay)
    }

    @Test
    fun positionBased_commitFromHigh() {
        // highHeight (795) - 36 = 759 -> Half with haptic
        val actionDown = decider.settleAfterDrag(SheetTier.High, 750f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = true), actionDown)

        // highHeight (795) + 36 = 831 -> Full with haptic
        val actionUp = decider.settleAfterDrag(SheetTier.High, 840f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true), actionUp)

        // In between: stays at High without haptic
        val actionStay = decider.settleAfterDrag(SheetTier.High, 790f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = false), actionStay)
    }

    @Test
    fun positionBased_commitFromHalf() {
        // fullHeight (1000) - 36 = 964 -> Full with haptic
        val actionFull = decider.settleAfterDrag(SheetTier.Half, 970f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = true), actionFull)

        // halfHeight (490) + 36 = 526 -> High with haptic
        val actionHigh = decider.settleAfterDrag(SheetTier.Half, 530f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = true), actionHigh)

        // Below 526: springs back to Half without haptic
        val actionStay = decider.settleAfterDrag(SheetTier.Half, 510f, 0f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), actionStay)
    }

    @Test
    fun settleAfterCancel_clampsOrSettlesToNearest() {
        val cancelBelow = decider.settleAfterCancel(300f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Half, triggerHaptic = false), cancelBelow)

        val cancelAbove = decider.settleAfterCancel(1100f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.Full, triggerHaptic = false), cancelAbove)

        val cancelNearHigh = decider.settleAfterCancel(800f)
        assertEquals(SheetSettleAction.SettleTo(SheetTier.High, triggerHaptic = false), cancelNearHigh)
    }
}
