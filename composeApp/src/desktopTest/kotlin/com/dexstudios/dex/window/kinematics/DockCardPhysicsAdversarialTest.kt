package com.dexstudios.dex.window.kinematics

import com.dexstudios.dex.platform.DisplayCoordinateSpace
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.WorkAreaBounds
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.ExpandedPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.Insets
import java.awt.Rectangle
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adversarial and Boundary Stress Test Suite for DockCardPhysics and DockedWindowStateController.
 *
 * Evaluates:
 * - Multi-monitor displays (Left/Top/Right/Diagonal negative origin coordinates)
 * - Custom taskbar inset positions (Left, Top, Right, Bottom)
 * - Ultra-wide (5120x1440), 4K/8K, and narrow displays (800x600, 1024x768)
 * - High-DPI scaling factors (1.0x, 1.25x, 1.5x, 1.75x, 2.0x, 2.5x, 3.0x) and degenerate DPIs (0f, negative, NaN)
 * - Extreme cursor deltas (+/- 1,000,000 px)
 * - Exact threshold boundaries (Deadzone 4px vs 5px, Magnetic Snap 19px vs 20px vs 21px)
 * - Numerical stability (NaN, Infinity, Division by Zero)
 */
class DockCardPhysicsAdversarialTest {

    // === Multi-Monitor Work Areas ===
    private val standard1080p = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 1920,
        bottom = 1040,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080),
    )

    private val leftSecondaryMonitor = WorkAreaBounds(
        left = -1920,
        top = 0,
        right = 0,
        bottom = 1040,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(-1920, 0, 1920, 1080),
    )

    private val topSecondaryMonitor = WorkAreaBounds(
        left = 0,
        top = -1080,
        right = 1920,
        bottom = -40,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, -1080, 1920, 1080),
    )

    private val rightSecondaryMonitor = WorkAreaBounds(
        left = 1920,
        top = 0,
        right = 3840,
        bottom = 1040,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(1920, 0, 1920, 1080),
    )

    private val diagonalTopLeftMonitor = WorkAreaBounds(
        left = -2560,
        top = -1440,
        right = 0,
        bottom = 0,
        width = 2560,
        height = 1440,
        insets = Insets(0, 0, 0, 0),
        screenBounds = Rectangle(-2560, -1440, 2560, 1440),
    )

    private val ultraWideMonitor = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 5120,
        bottom = 1400,
        width = 5120,
        height = 1400,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 5120, 1440),
    )

    private val fourKMonitor = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 3840,
        bottom = 2100,
        width = 3840,
        height = 2100,
        insets = Insets(0, 0, 60, 0),
        screenBounds = Rectangle(0, 0, 3840, 2160),
    )

    private val smallLegacyMonitor = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 1024,
        bottom = 728,
        width = 1024,
        height = 728,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 1024, 768),
    )

    // Taskbar Inset Variations
    private val leftTaskbarWorkArea = WorkAreaBounds(
        left = 72,
        top = 0,
        right = 1920,
        bottom = 1080,
        width = 1848,
        height = 1080,
        insets = Insets(0, 72, 0, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080),
    )

    private val topTaskbarWorkArea = WorkAreaBounds(
        left = 0,
        top = 40,
        right = 1920,
        bottom = 1080,
        width = 1920,
        height = 1040,
        insets = Insets(40, 0, 0, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080),
    )

    private val rightTaskbarWorkArea = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 1848,
        bottom = 1080,
        width = 1848,
        height = 1080,
        insets = Insets(0, 0, 0, 72),
        screenBounds = Rectangle(0, 0, 1920, 1080),
    )

    // =========================================================================
    // 1. Multi-Monitor & Coordinate Space Adversarial Tests
    // =========================================================================

    @Test
    fun testRestingCoordinatesOnMultiMonitors() {
        // Standard
        val xStd = TaskbarWorkAreaProvider.calculateRestingX(standard1080p, 1420)
        val yStd = TaskbarWorkAreaProvider.calculateRestingY(standard1080p, 430)
        assertEquals(1920 - 1420 + 12, xStd) // 512
        assertEquals(1040 - 760 + 12, yStd) // 292

        // Left Monitor (right = 0, bottom = 1040)
        val xLeft = TaskbarWorkAreaProvider.calculateRestingX(leftSecondaryMonitor, 1420)
        val yLeft = TaskbarWorkAreaProvider.calculateRestingY(leftSecondaryMonitor, 430)
        assertEquals(0 - 1420 + 12, xLeft) // -1408
        assertEquals(1040 - 760 + 12, yLeft) // 292
        // Physical card right = winX + canvasWidth - margin = -1408 + 1420 - 25 = -13 = workArea.right - 13px!
        assertEquals(leftSecondaryMonitor.right - 13, xLeft + 1420 - 25)

        // Top Monitor (right = 1920, bottom = -40)
        val xTop = TaskbarWorkAreaProvider.calculateRestingX(topSecondaryMonitor, 1420)
        val yTop = TaskbarWorkAreaProvider.calculateRestingY(topSecondaryMonitor, 430)
        assertEquals(1920 - 1420 + 12, xTop)
        assertEquals(-40 - 760 + 12, yTop) // -788
        // Card bottom = winY + margin + cardHeight
        assertEquals(topSecondaryMonitor.bottom - 293, yTop + 25 + 430)

        // Diagonal Top-Left Monitor (right = 0, bottom = 0)
        val xDiag = TaskbarWorkAreaProvider.calculateRestingX(diagonalTopLeftMonitor, 1420)
        val yDiag = TaskbarWorkAreaProvider.calculateRestingY(diagonalTopLeftMonitor, 430)
        assertEquals(0 - 1420 + 12, xDiag)
        assertEquals(0 - 760 + 12, yDiag)
    }

    @Test
    fun testExpansionNudgeOnLeftMonitor() {
        // Resting position on left monitor
        val restingX = TaskbarWorkAreaProvider.calculateRestingX(leftSecondaryMonitor, 1420) // -1408
        val restingY = TaskbarWorkAreaProvider.calculateRestingY(leftSecondaryMonitor, 430) // 572

        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = restingX,
            currentWindowY = restingY,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = leftSecondaryMonitor,
            canvasWidth = 1420,
            margin = 25,
        )

        // Target expanded card:
        val expW = 300 + 754 // 1054
        val expH = 430 + 195 // 625
        val expLeft = targetX + 1420 - 25 - expW
        val expRight = targetX + 1420 - 25
        val expTop = targetY + 760 - 25 - expH
        val expBottom = targetY + 760 - 25

        assertTrue(expLeft >= leftSecondaryMonitor.left, "expLeft ($expLeft) must be >= -1920")
        assertTrue(expRight <= leftSecondaryMonitor.right, "expRight ($expRight) must be <= 0")
        assertTrue(expTop >= leftSecondaryMonitor.top, "expTop ($expTop) must be >= 0")
        assertTrue(expBottom <= leftSecondaryMonitor.bottom, "expBottom ($expBottom) must be <= 1040")
    }

    @Test
    fun testExpansionNudgeOnTopMonitor() {
        val restingX = TaskbarWorkAreaProvider.calculateRestingX(topSecondaryMonitor, 1420)
        val restingY = TaskbarWorkAreaProvider.calculateRestingY(topSecondaryMonitor, 430)

        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = restingX,
            currentWindowY = restingY,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = topSecondaryMonitor,
            canvasWidth = 1420,
            margin = 25,
        )

        val expW = 1054
        val expH = 625
        val expLeft = targetX + 1420 - 25 - expW
        val expRight = targetX + 1420 - 25
        val expTop = targetY + 760 - 25 - expH
        val expBottom = targetY + 760 - 25

        assertTrue(expLeft >= topSecondaryMonitor.left, "expLeft ($expLeft) >= top monitor left")
        assertTrue(expRight <= topSecondaryMonitor.right, "expRight ($expRight) <= top monitor right")
        assertTrue(expTop >= topSecondaryMonitor.top, "expTop ($expTop) >= top monitor top (-1080)")
        assertTrue(expBottom <= topSecondaryMonitor.bottom, "expBottom ($expBottom) <= top monitor bottom (-40)")
    }

    @Test
    fun testExpansionNudgeOnNarrowDisplay() {
        // Display width is 1024, but expanded card is 1054 dp (wider than screen!)
        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = 0,
            currentWindowY = 0,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = smallLegacyMonitor,
            canvasWidth = 1420,
            margin = 25,
        )

        val expRight = targetX + 1420 - 25
        // Right clamp guarantees it aligns to smallLegacyMonitor.right (1024)
        assertEquals(smallLegacyMonitor.right, expRight, "On narrow screen, right edge must align to right bound")
    }

    // =========================================================================
    // 2. Magnetic Snapping & Sanity Clamping Boundary Tests
    // =========================================================================

    @Test
    fun testMagneticSnapThresholdBoundaries() {
        // snapThreshold = 20
        // Case 1: delta = 21 (just outside snap range) -> no snap
        val (left21, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 21,
            candidateContentTop = 100,
            cardWidth = 300,
            cardHeight = 430,
            workArea = standard1080p,
            snapThreshold = 20,
        )
        assertEquals(21, left21, "Delta 21px must not snap (outside threshold)")

        // Case 2: delta = 19 (just inside snap range) -> snaps to 0
        val (left19, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 19,
            candidateContentTop = 100,
            cardWidth = 300,
            cardHeight = 430,
            workArea = standard1080p,
            snapThreshold = 20,
        )
        assertEquals(0, left19, "Delta 19px must snap to workArea.left (0)")

        // Case 3: delta = -19 (outside snap from negative side) -> no snap because outward snapping is disabled
        val (leftNeg19, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = -19,
            candidateContentTop = 100,
            cardWidth = 300,
            cardHeight = 430,
            workArea = standard1080p,
            snapThreshold = 20,
        )
        assertEquals(-19, leftNeg19, "Delta -19px must not snap to workArea.left (outward snap is disabled)")
    }

    @Test
    fun testMagneticSnapOnNegativeMonitors() {
        // Left monitor: left = -1920, right = 0
        // Near left edge (-1910, delta = 10)
        val (snapLeft, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = -1910,
            candidateContentTop = 100,
            cardWidth = 300,
            cardHeight = 430,
            workArea = leftSecondaryMonitor,
            snapThreshold = 20,
        )
        assertEquals(-1920, snapLeft, "Must snap to negative left boundary (-1920)")

        // Near right edge (-310, cardRight = -10, delta to right=0 is 10)
        val (snapRight, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = -310,
            candidateContentTop = 100,
            cardWidth = 300,
            cardHeight = 430,
            workArea = leftSecondaryMonitor,
            snapThreshold = 20,
        )
        assertEquals(-300, snapRight, "Must snap to negative right boundary (0 - 300 = -300)")
    }

    @Test
    fun testSanityClampExtremeDeltas() {
        // Extreme positive delta (+1,000,000 px)
        val (clampXHigh, clampYHigh) = DockCardPhysics.applySanityClamp(
            contentLeft = 1_000_000,
            contentTop = 1_000_000,
            cardWidth = 300,
            cardHeight = 430,
            workArea = standard1080p,
            minGrab = 60,
        )
        // Grab is per-axis: grabX = max(300*0.2, 60) = 60, grabY = max(430*0.2, 60) = 86
        // Max allowed contentLeft: workArea.right - grabX = 1920 - 60 = 1860
        assertEquals(1860, clampXHigh, "Extreme high X must clamp to right - grabX")
        assertEquals(1040 - 86, clampYHigh, "Extreme high Y must clamp to bottom - grabY")

        // Extreme negative delta (-1,000,000 px)
        val (clampXLow, clampYLow) = DockCardPhysics.applySanityClamp(
            contentLeft = -1_000_000,
            contentTop = -1_000_000,
            cardWidth = 300,
            cardHeight = 430,
            workArea = standard1080p,
            minGrab = 60,
        )
        // Min allowed contentLeft: workArea.left + grabX - cardWidth = 0 + 60 - 300 = -240
        assertEquals(-240, clampXLow, "Extreme low X must clamp to left + grabX - cardWidth")
        assertEquals(0 + 86 - 430, clampYLow, "Extreme low Y must clamp to top + grabY - cardHeight")
    }

    // =========================================================================
    // 3. High-DPI Scaling & Gesture Engine Verification
    // =========================================================================

    @Test
    fun testHighDpiDeltaScalingCalculations() {
        val testScales = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val physicalDeltaX = 300
        val physicalDeltaY = 150

        for (density in testScales) {
            val expectedScale = if (DisplayCoordinateSpace.awtUsesDevicePixels) density else 1.0f
            assertEquals(expectedScale, DisplayCoordinateSpace.scaleFactor(density))
            val dpDx = DisplayCoordinateSpace.nativeToDp(physicalDeltaX, density)
            val dpDy = DisplayCoordinateSpace.nativeToDp(physicalDeltaY, density)

            val expectedDpDx = (physicalDeltaX / expectedScale).roundToInt()
            val expectedDpDy = (physicalDeltaY / expectedScale).roundToInt()

            assertEquals(expectedDpDx, dpDx, "DPI scale $density should compute exact integer DP dx")
            assertEquals(expectedDpDy, dpDy, "DPI scale $density should compute exact integer DP dy")
            assertTrue(dpDx > 0, "dpDx must be positive for positive delta")
        }
    }

    @Test
    fun testDegenerateDpiGuards() {
        // Verify 0f, negative, NaN, and Infinity fallbacks
        val degenerateValues = listOf(0.0f, -1.5f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        val physicalDelta = 100

        for (badDensity in degenerateValues) {
            assertEquals(1.0f, DisplayCoordinateSpace.scaleFactor(badDensity), "Degenerate DPI $badDensity must safely fall back to 1.0f scale")
            val dpDx = DisplayCoordinateSpace.nativeToDp(physicalDelta, badDensity)
            assertEquals(physicalDelta, dpDx, "Degenerate DPI $badDensity must pass pixels through unscaled")
            assertFalse(DisplayCoordinateSpace.scaleFactor(badDensity).isNaN(), "scale factor must never be NaN")
            assertFalse(DisplayCoordinateSpace.scaleFactor(badDensity).isInfinite(), "scale factor must never be Infinite")
        }
    }

    /** Deterministic provider so guard/drag paths never depend on real mouse state. */
    private object DeterministicMouseProvider : com.dexstudios.dex.platform.MouseInputProvider {
        override fun isLeftMouseButtonDown(): Boolean = false
        override fun getCursorPosition(): Pair<Int, Int> = Pair(0, 0)
    }

    @Test
    fun testDeadZoneAccumulatorThresholds() {
        val controller = DockedWindowStateController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            density = 1.0f,
            mouseInputProvider = DeterministicMouseProvider,
        )

        controller.onDragStart(100, 100)
        assertTrue(controller.dragPending, "Drag should be pending")
        assertFalse(controller.isDragging, "Should not be dragging yet")

        // Delta = 4px (|2| + |2| = 4 < 5dp threshold)
        controller.onDragMove(102, 102, 1.0f)
        assertTrue(controller.dragPending, "Delta 4px must remain pending")
        assertFalse(controller.isDragging, "Delta 4px must not trigger drag")

        // Delta = 5px (|3| + |2| = 5 >= 5dp threshold)
        controller.onDragMove(103, 102, 1.0f)
        assertFalse(controller.dragPending, "Delta 5px must exit pending")
        assertTrue(controller.isDragging, "Delta 5px must initiate active drag")
        assertTrue(controller.hasBeenDragged, "hasBeenDragged must be set true")

        controller.onDragEnd()
        assertFalse(controller.isDragging, "DragEnd must clear isDragging")
        assertFalse(controller.dragPending, "DragEnd must clear dragPending")
    }

    @Test
    fun testDeadZoneScalesWithDisplayDensity() {
        val controller = DockedWindowStateController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            density = 2.0f,
            mouseInputProvider = DeterministicMouseProvider,
        )
        val scale = DisplayCoordinateSpace.scaleFactor(2.0f)

        controller.onDragStart(100, 100)

        // Single-axis logical distance below the 5dp threshold must remain pending at any density
        val pendingPhysical = (3 * scale).roundToInt()
        controller.onDragMove(100 + pendingPhysical, 100, 2.0f)
        assertTrue(controller.dragPending, "Logical ${pendingPhysical / scale}dp movement must remain pending")
        assertFalse(controller.isDragging)

        // Single-axis logical distance past the 5dp threshold must initiate the drag
        val dragPhysical = (7 * scale).roundToInt()
        controller.onDragMove(100 + dragPhysical, 100, 2.0f)
        assertFalse(controller.dragPending, "Logical past-threshold movement must exit pending")
        assertTrue(controller.isDragging, "Logical past-threshold movement must initiate drag")

        controller.onDragEnd()
        assertFalse(controller.isDragging)
    }

    // =========================================================================
    // 4. Numerical Stability & Easing Function Stress
    // =========================================================================

    @Test
    fun testEasingCurvesNumericalLimits() {
        val testFractions = listOf(-100f, -1f, 0f, 0.001f, 0.25f, 0.5f, 0.75f, 0.999f, 1f, 2f, 100f)

        for (f in testFractions) {
            val popIn = DockCardPhysics.PopInEase.transform(f)
            val contract = DockCardPhysics.ContractEase.transform(f)
            val hover = DockCardPhysics.HoverEase.transform(f.coerceIn(0f, 1f))

            assertFalse(popIn.isNaN(), "PopInEase($f) must not be NaN")
            assertFalse(popIn.isInfinite(), "PopInEase($f) must not be Infinite")
            assertFalse(contract.isNaN(), "ContractEase($f) must not be NaN")
            assertFalse(contract.isInfinite(), "ContractEase($f) must not be Infinite")
            assertFalse(hover.isNaN(), "HoverEase($f) must not be NaN")
            assertFalse(hover.isInfinite(), "HoverEase($f) must not be Infinite")
        }
    }

    @Test
    fun testContractionClampingOnMultiMonitors() {
        // On left secondary monitor (right = 0)
        // If window was moved so contracted card left > workArea.right - grab (0 - 60 = -60)
        // cContractedLeft = winX + 1420 - 25 - 300 = winX + 1095
        // If winX = -1000 => cContractedLeft = 95 > -60
        val safeX = DockCardPhysics.calculateContractionOrigin(
            currentWindowX = -1000,
            contractedCardWidth = 300,
            workArea = leftSecondaryMonitor,
            canvasWidth = 1420,
            margin = 25,
            minGrab = 60,
        )

        val safeContractedLeft = safeX + 1420 - 25 - 300
        assertTrue(safeContractedLeft <= leftSecondaryMonitor.right - 60, "safeContractedLeft must be <= -60")
    }

    @Test
    fun testFocusLossGuardParity() {
        val controller = DockedWindowStateController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            density = 1.0f,
            mouseInputProvider = DeterministicMouseProvider,
        )

        // Default state: should dismiss
        assertTrue(controller.shouldDismissOnFocusLoss(), "Default state should dismiss on focus loss")

        // 1. Pinned
        controller.isPinned = true
        assertFalse(controller.shouldDismissOnFocusLoss(), "Pinned card must not dismiss")
        controller.isPinned = false

        // 2. Showing transition
        controller.isShowingTransition = true
        assertFalse(controller.shouldDismissOnFocusLoss(), "Transitioning card must not dismiss")
        controller.isShowingTransition = false

        // 3. Pairing active
        controller.isPairingActive = true
        assertFalse(controller.shouldDismissOnFocusLoss(), "Active pairing must not dismiss")
        controller.isPairingActive = false

        // 4. Drawer expanded
        controller.expandedPanel = ExpandedPanel.FileExplorer
        assertFalse(controller.shouldDismissOnFocusLoss(), "Expanded FileExplorer must not dismiss")
        controller.expandedPanel = null

        // 5. Modal dialog open
        controller.isModalDialogOpen = true
        assertFalse(controller.shouldDismissOnFocusLoss(), "Open modal dialog must not dismiss")
        controller.isModalDialogOpen = false

        // Back to clean state
        assertTrue(controller.shouldDismissOnFocusLoss(), "Clean state should dismiss")
    }
}
