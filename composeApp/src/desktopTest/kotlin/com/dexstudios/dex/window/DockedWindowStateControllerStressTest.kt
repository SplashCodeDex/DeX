package com.dexstudios.dex.window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.WorkAreaBounds
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.Insets
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockedWindowStateControllerStressTest {

    private val testWorkArea = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 1920,
        bottom = 1040,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080)
    )

    private fun createController(): DockedWindowStateController {
        val windowState = WindowState(
            size = DpSize(1420.dp, 760.dp),
            position = WindowPosition(512.dp, 292.dp)
        )
        return DockedWindowStateController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            windowState = windowState,
            density = 1.0f
        )
    }

    @Test
    fun testFocusLoss5PointGuardExhaustiveTruthTable() {
        val controller = createController()

        // Exhaustive 32-permutation truth table test
        for (pinned in listOf(false, true)) {
            for (transition in listOf(false, true)) {
                for (pairing in listOf(false, true)) {
                    for (expanded in listOf(false, true)) {
                        for (modal in listOf(false, true)) {
                            controller.isPinned = pinned
                            controller.isShowingTransition = transition
                            controller.isPairingActive = pairing
                            controller.expandedPanel = if (expanded) ExpandedPanel.FileExplorer else null
                            controller.isModalDialogOpen = modal

                            val expectedDismiss = !pinned && !transition && !pairing && !expanded && !modal
                            val actualDismiss = controller.shouldDismissOnFocusLoss()

                            assertEquals(
                                expectedDismiss,
                                actualDismiss,
                                "Failed for: pinned=$pinned, trans=$transition, pair=$pairing, exp=$expanded, modal=$modal"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testRapidConsecutivePanelExpansionsAndContractions() {
        val controller = createController()

        // 1. Initially contracted
        assertFalse(controller.isExpanded)
        assertNull(controller.expandedPanel)

        // 2. Expand to FileExplorer
        controller.expandPanel(ExpandedPanel.FileExplorer)
        assertTrue(controller.isExpanded)
        assertEquals(ExpandedPanel.FileExplorer, controller.expandedPanel)

        // 3. Immediately switch to Settings without collapsing first
        controller.expandPanel(ExpandedPanel.Settings)
        assertTrue(controller.isExpanded)
        assertEquals(ExpandedPanel.Settings, controller.expandedPanel)

        // 4. Immediately switch to Pairing
        controller.expandPanel(ExpandedPanel.Pairing)
        assertTrue(controller.isExpanded)
        assertEquals(ExpandedPanel.Pairing, controller.expandedPanel)

        // 5. Toggle Pairing (should collapse)
        controller.togglePanel(ExpandedPanel.Pairing)
        assertFalse(controller.isExpanded)
        assertNull(controller.expandedPanel)

        // 6. Rapid toggle loop 50 iterations
        for (i in 0 until 50) {
            controller.togglePanel(ExpandedPanel.FileExplorer)
            assertEquals(ExpandedPanel.FileExplorer, controller.expandedPanel)
            controller.togglePanel(ExpandedPanel.FileExplorer)
            assertNull(controller.expandedPanel)
        }
    }

    @Test
    fun test3PhaseDragGesturesAndDeadzone() {
        val controller = createController()

        // Initial state
        assertFalse(controller.isDragging)
        assertFalse(controller.dragPending)
        assertFalse(controller.hasBeenDragged)

        // Phase 1: onDragStart
        controller.onDragStart(100, 100)
        assertTrue(controller.dragPending)
        assertFalse(controller.isDragging)

        // Move within 5px Manhattan deadzone (dx=2, dy=2 -> sum 4 < 5)
        controller.onDragMove(102, 102, 1.0f)
        assertTrue(controller.dragPending)
        assertFalse(controller.isDragging)
        assertFalse(controller.hasBeenDragged)

        // Phase 2: Exceed deadzone (dx=3, dy=3 -> sum 6 >= 5)
        controller.onDragMove(103, 103, 1.0f)
        assertFalse(controller.dragPending)
        assertTrue(controller.isDragging)
        assertTrue(controller.hasBeenDragged)

        // Phase 3: onDragEnd
        controller.onDragEnd()
        assertFalse(controller.dragPending)
        assertFalse(controller.isDragging)
        assertTrue(controller.hasBeenDragged)
    }

    @Test
    fun testDoubleTapResetInvocation() {
        val controller = createController()

        // 1. Double tap when not dragged and not pinned -> does not trigger shake
        controller.onDoubleTapReset()
        assertFalse(controller.isShaking)

        // 2. Double tap when pinned -> triggers shake animation
        controller.isPinned = true
        controller.onDoubleTapReset()

        // 3. Double tap when unpinned and dragged -> initiates reset animation
        controller.isPinned = false
        controller.hasBeenDragged = true
        controller.windowState.position = WindowPosition(100.dp, 100.dp)

        controller.onDoubleTapReset()
        assertFalse(controller.hasBeenDragged, "hasBeenDragged must be reset to false")
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val expectedX = TaskbarWorkAreaProvider.calculateRestingX(workArea, controller.canvasWidth)
        val expectedY = TaskbarWorkAreaProvider.calculateRestingY(workArea, controller.contractedCardHeight)
        assertEquals(expectedX.dp, controller.windowState.position.x)
        assertEquals(expectedY.dp, controller.windowState.position.y)
    }

    @Test
    fun testPanelExpandCollapseRestoration() {
        val controller = createController()
        val initialX = controller.windowState.position.x
        val initialY = controller.windowState.position.y

        // Expand to FileExplorer (deltaW = 754)
        controller.expandPanel(ExpandedPanel.FileExplorer)
        assertTrue(controller.isExpanded)
        assertEquals(ExpandedPanel.FileExplorer, controller.expandedPanel)

        // Collapse should restore to preExpandX / preExpandY
        controller.collapsePanel()
        assertFalse(controller.isExpanded)
        assertNull(controller.expandedPanel)
        assertEquals(initialX, controller.windowState.position.x)
        assertEquals(initialY, controller.windowState.position.y)
    }

    @Test
    fun testVisibilityAndDeltaDragging() {
        val controller = createController()
        assertFalse(controller.isVisible)

        controller.show()
        assertTrue(controller.isVisible)

        controller.toggleVisibility()
        assertFalse(controller.isVisible)

        controller.toggleVisibility()
        assertTrue(controller.isVisible)

        // Delta dragging
        val posX = controller.windowState.position.x.value
        val posY = controller.windowState.position.y.value
        controller.onDragDelta(30f, 40f)
        assertTrue(controller.hasBeenDragged)
        assertEquals((posX + 30f).dp, controller.windowState.position.x)
        assertEquals((posY + 40f).dp, controller.windowState.position.y)

        // Hide when expanded collapses the drawer
        controller.expandPanel(ExpandedPanel.Settings)
        assertTrue(controller.isExpanded)
        controller.hide()
        assertFalse(controller.isVisible)
        assertFalse(controller.isExpanded)
    }

    @Test
    fun testBoundaryClampingOnExtremeResolutions() {
        // Test low-resolution display (1280x720)
        val lowResWorkArea = WorkAreaBounds(
            left = 0,
            top = 0,
            right = 1280,
            bottom = 720,
            width = 1280,
            height = 720,
            insets = Insets(0, 0, 40, 0),
            screenBounds = Rectangle(0, 0, 1280, 720)
        )

        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = 100,
            currentWindowY = 200,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = lowResWorkArea,
            canvasWidth = 1420,
            margin = 25
        )

        // Verify post-expansion boundaries stay within lowResWorkArea
        val expLeft = targetX + 1420 - 25 - 1054
        val expRight = targetX + 1420 - 25
        val expTop = targetY + 760 - 25 - 625
        val expBottom = targetY + 760 - 25

        assertTrue(expLeft >= lowResWorkArea.left, "expLeft ($expLeft) must be >= 0")
        assertTrue(expTop >= lowResWorkArea.top, "expTop ($expTop) must be >= 0")
        assertTrue(expBottom <= lowResWorkArea.bottom, "expBottom ($expBottom) must be <= 720")
    }

    @Test
    fun testNegativeSecondaryMonitorWorkArea() {
        val secondaryLeftMonitor = WorkAreaBounds(
            left = -1920,
            top = 0,
            right = 0,
            bottom = 1040,
            width = 1920,
            height = 1040,
            insets = Insets(0, 0, 40, 0),
            screenBounds = Rectangle(-1920, 0, 1920, 1080)
        )

        val (snappedLeft, snappedTop) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = -1910,
            candidateContentTop = 500,
            cardWidth = 300,
            cardHeight = 430,
            workArea = secondaryLeftMonitor,
            snapThreshold = 20
        )

        assertEquals(-1920, snappedLeft, "Should magnetically snap to left monitor boundary -1920")
        assertEquals(500, snappedTop)
    }
}
