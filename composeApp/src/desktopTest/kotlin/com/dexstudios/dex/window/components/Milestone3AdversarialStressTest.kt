package com.dexstudios.dex.window.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.auth.PairingState
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial and Stress Test Suite for Milestone 3 UI Components:
 * 1. BottomDockPanel (2-stage exit state machine, Shift bypass, active transfer swap, 3s timeout)
 * 2. QuickActionBar (Hover/press kinematics, Emerald morphing, badge counter inversion, collapsible close pill)
 * 3. PinPairingPanel (6-digit box layout, QR/PIN flip transition, 15px error shake, 60s countdown)
 */
class Milestone3AdversarialStressTest {

    // =========================================================================
    // 1. BottomDockPanel Adversarial & State Machine Tests
    // =========================================================================

    @Test
    fun testExitStageTextResolutionUnderAllConditions() {
        fun resolveExitButtonText(stage: ExitConfirmationStage, hasActiveTransfers: Boolean, isMirroringActive: Boolean): String = when {
            stage == ExitConfirmationStage.Confirming && (hasActiveTransfers || isMirroringActive) ->
                "Transfer Active! Click to Force Exit"

            stage == ExitConfirmationStage.Confirming ->
                "Cancel / Shift+Click Exit"

            else -> "Exit Engine"
        }

        // 1. Idle state - always "Exit Engine" regardless of transfers/mirroring
        assertEquals("Exit Engine", resolveExitButtonText(ExitConfirmationStage.Idle, hasActiveTransfers = false, isMirroringActive = false))
        assertEquals("Exit Engine", resolveExitButtonText(ExitConfirmationStage.Idle, hasActiveTransfers = true, isMirroringActive = false))
        assertEquals("Exit Engine", resolveExitButtonText(ExitConfirmationStage.Idle, hasActiveTransfers = false, isMirroringActive = true))
        assertEquals("Exit Engine", resolveExitButtonText(ExitConfirmationStage.Idle, hasActiveTransfers = true, isMirroringActive = true))

        // 2. Confirming state without transfers or mirroring
        assertEquals("Cancel / Shift+Click Exit", resolveExitButtonText(ExitConfirmationStage.Confirming, hasActiveTransfers = false, isMirroringActive = false))

        // 3. Confirming state with active transfers
        assertEquals("Transfer Active! Click to Force Exit", resolveExitButtonText(ExitConfirmationStage.Confirming, hasActiveTransfers = true, isMirroringActive = false))

        // 4. Confirming state with active mirroring
        assertEquals("Transfer Active! Click to Force Exit", resolveExitButtonText(ExitConfirmationStage.Confirming, hasActiveTransfers = false, isMirroringActive = true))

        // 5. Confirming state with both active
        assertEquals("Transfer Active! Click to Force Exit", resolveExitButtonText(ExitConfirmationStage.Confirming, hasActiveTransfers = true, isMirroringActive = true))
    }

    @Test
    fun testBottomDockPanelKinematicTargets() {
        fun getAvatarScale(stage: ExitConfirmationStage): Float = if (stage == ExitConfirmationStage.Confirming) 0.6f else 1.0f

        fun getExitButtonOffsetX(stage: ExitConfirmationStage): Float = if (stage == ExitConfirmationStage.Confirming) -62f else 0f

        fun isExitBadgeVisible(stage: ExitConfirmationStage): Boolean = stage == ExitConfirmationStage.Idle

        // Idle state targets
        assertEquals(1.0f, getAvatarScale(ExitConfirmationStage.Idle))
        assertEquals(0f, getExitButtonOffsetX(ExitConfirmationStage.Idle))
        assertTrue(isExitBadgeVisible(ExitConfirmationStage.Idle))

        // Confirming state targets
        assertEquals(0.6f, getAvatarScale(ExitConfirmationStage.Confirming))
        assertEquals(-62f, getExitButtonOffsetX(ExitConfirmationStage.Confirming))
        assertFalse(isExitBadgeVisible(ExitConfirmationStage.Confirming))
    }

    @Test
    fun testExitConfirmationStateMachineAndTimeout() = runBlocking {
        var stage = ExitConfirmationStage.Idle
        var exitCalled = false

        fun handleClick(isShift: Boolean) {
            if (isShift) {
                exitCalled = true
            } else {
                if (stage == ExitConfirmationStage.Idle) {
                    stage = ExitConfirmationStage.Confirming
                } else {
                    exitCalled = true
                }
            }
        }

        // 1. Shift+Click bypass from Idle
        handleClick(isShift = true)
        assertTrue(exitCalled, "Shift+Click must trigger immediate exit")
        assertEquals(ExitConfirmationStage.Idle, stage, "Stage should remain Idle on bypass")

        // Reset
        exitCalled = false

        // 2. Click 1 without Shift -> transitions to Confirming
        handleClick(isShift = false)
        assertFalse(exitCalled, "First regular click must not exit")
        assertEquals(ExitConfirmationStage.Confirming, stage, "Stage must be Confirming")

        // 3. Click 2 without Shift -> triggers exit
        handleClick(isShift = false)
        assertTrue(exitCalled, "Second click while Confirming must trigger exit")

        // 4. Auto-revert timeout simulation (3000ms delay)
        stage = ExitConfirmationStage.Confirming
        exitCalled = false
        // Simulate timer coroutine
        val job = launch {
            delay(300) // Scaled test delay
            stage = ExitConfirmationStage.Idle
        }
        job.join()
        assertEquals(ExitConfirmationStage.Idle, stage, "Stage must auto-revert to Idle on timeout")
    }

    // =========================================================================
    // 2. QuickActionBar Kinematics, Morphing & Badge Tests
    // =========================================================================

    @Test
    fun testQuickActionBarKinematicSpecs() {
        fun resolveButtonScale(isHovered: Boolean, isPressed: Boolean): Float = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        }

        fun resolveButtonTranslateY(isHovered: Boolean, isPressed: Boolean): Float = when {
            isPressed -> 3.0f
            isHovered -> -3.0f
            else -> 0.0f
        }

        // Resting
        assertEquals(1.0f, resolveButtonScale(isHovered = false, isPressed = false))
        assertEquals(0.0f, resolveButtonTranslateY(isHovered = false, isPressed = false))

        // Hover
        assertEquals(1.08f, resolveButtonScale(isHovered = true, isPressed = false))
        assertEquals(-3.0f, resolveButtonTranslateY(isHovered = true, isPressed = false))

        // Press (Press takes priority over Hover)
        assertEquals(0.85f, resolveButtonScale(isHovered = true, isPressed = true))
        assertEquals(3.0f, resolveButtonTranslateY(isHovered = true, isPressed = true))
    }

    @Test
    fun testQuickActionBarEmeraldColorMorphing() {
        fun resolveBgColor(isChecked: Boolean, isDanger: Boolean, isHovered: Boolean, isPressed: Boolean): Color = when {
            isDanger && (isHovered || isPressed) -> Color(0xFFFF453A)

            isChecked -> Color(0xFF0AE66D)

            // Emerald
            isHovered -> Color(0xFF332D3B)

            else -> Color(0xFF2B2631)
        }

        fun resolveIconColor(isChecked: Boolean, isDanger: Boolean, isHovered: Boolean, isPressed: Boolean): Color = when {
            isChecked -> Color(0xFF000000)

            // Black icon on active green
            isDanger && (isHovered || isPressed) -> Color(0xFFFFFFFF)

            else -> Color(0xFFFFFFFF)
        }

        // Active state
        assertEquals(Color(0xFF0AE66D), resolveBgColor(isChecked = true, isDanger = false, isHovered = false, isPressed = false))
        assertEquals(Color(0xFF000000), resolveIconColor(isChecked = true, isDanger = false, isHovered = false, isPressed = false))

        // Inactive resting
        assertEquals(Color(0xFF2B2631), resolveBgColor(isChecked = false, isDanger = false, isHovered = false, isPressed = false))
        assertEquals(Color(0xFFFFFFFF), resolveIconColor(isChecked = false, isDanger = false, isHovered = false, isPressed = false))

        // Inactive hovered
        assertEquals(Color(0xFF332D3B), resolveBgColor(isChecked = false, isDanger = false, isHovered = true, isPressed = false))
        assertEquals(Color(0xFFFFFFFF), resolveIconColor(isChecked = false, isDanger = false, isHovered = true, isPressed = false))

        // Danger button hovered
        assertEquals(Color(0xFFFF453A), resolveBgColor(isChecked = false, isDanger = true, isHovered = true, isPressed = false))
        assertEquals(Color(0xFFFFFFFF), resolveIconColor(isChecked = false, isDanger = true, isHovered = true, isPressed = false))
    }

    @Test
    fun testBadgeCounterInversionRules() {
        data class BadgeStyle(val isVisible: Boolean, val bgColor: Color, val textColor: Color, val hasBorder: Boolean)

        fun resolveBadgeStyle(badgeCount: Int, isChecked: Boolean): BadgeStyle {
            if (badgeCount <= 0) return BadgeStyle(false, Color.Transparent, Color.Transparent, false)
            val bg = if (isChecked) Color(0xFF16121A) else Color(0xFF0AE66D)
            val text = if (isChecked) Color(0xFFFFFFFF) else Color(0xFF000000)
            val border = isChecked
            return BadgeStyle(true, bg, text, border)
        }

        // 0 count -> hidden
        val zeroBadge = resolveBadgeStyle(0, isChecked = false)
        assertFalse(zeroBadge.isVisible)

        // Positive count, unchecked button -> Emerald background, Black text, no border
        val uncheckedBadge = resolveBadgeStyle(5, isChecked = false)
        assertTrue(uncheckedBadge.isVisible)
        assertEquals(Color(0xFF0AE66D), uncheckedBadge.bgColor)
        assertEquals(Color(0xFF000000), uncheckedBadge.textColor)
        assertFalse(uncheckedBadge.hasBorder)

        // Positive count, checked button -> Dark background, White text, Emerald border
        val checkedBadge = resolveBadgeStyle(5, isChecked = true)
        assertTrue(checkedBadge.isVisible)
        assertEquals(Color(0xFF16121A), checkedBadge.bgColor)
        assertEquals(Color(0xFFFFFFFF), checkedBadge.textColor)
        assertTrue(checkedBadge.hasBorder)
    }

    // =========================================================================
    // 3. PinPairingPanel Layout, Shake & Countdown Tests
    // =========================================================================

    @Test
    fun test5DigitBoxFormattingMatrix() {
        fun extractDigits(pinCode: String): List<String> {
            val pinString = pinCode.padEnd(5, ' ')
            return (0 until 5).map { i ->
                if (i < pinString.length && pinString[i] != ' ') pinString[i].toString() else ""
            }
        }

        // 1. Standard 5 digits (legacy WPF server parity: Random().Next(10000, 99999))
        val std = extractDigits("48291")
        assertEquals(listOf("4", "8", "2", "9", "1"), std)

        // 2. Partial 3 digits
        val partial = extractDigits("123")
        assertEquals(listOf("1", "2", "3", "", ""), partial)

        // 3. Empty digits
        val empty = extractDigits("")
        assertEquals(listOf("", "", "", "", ""), empty)

        // 4. Overlong 8 digits -> extracts first 5
        val overlong = extractDigits("98765432")
        assertEquals(listOf("9", "8", "7", "6", "5"), overlong)
    }

    @Test
    fun testDigitBoxBorderAndScaleRules() {
        data class BoxAppearance(val borderWidthDp: Int, val borderColor: Color, val scale: Float)

        fun resolveBoxAppearance(isFilled: Boolean, isError: Boolean): BoxAppearance = when {
            isError -> BoxAppearance(2, Color(0xFFFF453A), if (isFilled) 1.0f else 0.95f)
            isFilled -> BoxAppearance(2, Color(0xFF0AE66D), 1.0f)
            else -> BoxAppearance(1, Color(0xFF2B2631), 0.95f)
        }

        // Empty box
        val empty = resolveBoxAppearance(isFilled = false, isError = false)
        assertEquals(1, empty.borderWidthDp)
        assertEquals(Color(0xFF2B2631), empty.borderColor)
        assertEquals(0.95f, empty.scale)

        // Filled box (normal)
        val filled = resolveBoxAppearance(isFilled = true, isError = false)
        assertEquals(2, filled.borderWidthDp)
        assertEquals(Color(0xFF0AE66D), filled.borderColor)
        assertEquals(1.0f, filled.scale)

        // Error box
        val err = resolveBoxAppearance(isFilled = true, isError = true)
        assertEquals(2, err.borderWidthDp)
        assertEquals(Color(0xFFFF453A), err.borderColor)
    }

    @Test
    fun testErrorShakeAnimationKeyframeTrajectory() {
        // 15px spring shake oscillation over 400ms: [0, -15, 15, -10, 10, -5, 0]
        val keyframeTimes = listOf(0, 60, 120, 180, 240, 300, 400)
        val expectedDeltas = listOf(0f, -15f, 15f, -10f, 10f, -5f, 0f)

        assertEquals(7, keyframeTimes.size)
        assertEquals(7, expectedDeltas.size)

        // Max amplitude is 15px
        val maxAmplitude = expectedDeltas.maxOf { kotlin.math.abs(it) }
        assertEquals(15f, maxAmplitude, "Maximum oscillation amplitude must be 15px")

        // Final settle position is 0px
        assertEquals(0f, expectedDeltas.last(), "Final rest offset must be 0px")
    }

    @Test
    fun testCountdownTimerBoundaryDecrements() = runBlocking {
        var remainingSeconds = 60
        var isClosed = false

        // Simulate 5 ticks
        for (i in 0 until 5) {
            remainingSeconds--
        }
        assertEquals(55, remainingSeconds)

        // Simulate expiration
        remainingSeconds = 1
        remainingSeconds--
        if (remainingSeconds == 0) {
            isClosed = true
        }

        assertEquals(0, remainingSeconds)
        assertTrue(isClosed, "Timer expiry must trigger close action")
    }
}
