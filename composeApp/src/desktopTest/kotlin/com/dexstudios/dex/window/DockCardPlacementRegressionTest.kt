package com.dexstudios.dex.window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.dexstudios.dex.platform.DisplayCoordinateSpace
import com.dexstudios.dex.platform.DockCardMetrics
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.WorkAreaBounds
import com.dexstudios.dex.platform.toDpSpace
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the dock card placement pipeline:
 * - Single source of truth parity between window-placement math and rendered Compose geometry
 * - Window<->content coordinate mapping round trips (both platform anchorings)
 * - Deterministic nearest-edge resolution when both edges of an axis qualify for magnetic snap
 * - Native AWT <-> dp work-area conversion
 * - Drawer-to-drawer expansion deltas, collapse restore validity, stranded-window recovery
 */
class DockCardPlacementRegressionTest {

    private val standard1080p = WorkAreaBounds(
        left = 0, top = 0, right = 1920, bottom = 1040,
        width = 1920, height = 1040,
        insets = java.awt.Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080)
    )

    private fun createController(x: Int = 512, y: Int = 292): DockedWindowStateController {
        val windowState = WindowState(
            size = DpSize(DockCardMetrics.CANVAS_WIDTH.dp, DockCardMetrics.CANVAS_HEIGHT.dp),
            position = WindowPosition(x.dp, y.dp)
        )
        return DockedWindowStateController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            windowState = windowState,
            density = 1.0f,
            mouseInputProvider = DeterministicMouseProvider
        )
    }

    /** Deterministic provider so guard paths never depend on real mouse state. */
    private object DeterministicMouseProvider : com.dexstudios.dex.platform.MouseInputProvider {
        override fun isLeftMouseButtonDown(): Boolean = false
        override fun getCursorPosition(): Pair<Int, Int> = Pair(0, 0)
    }

    private fun contentRect(winX: Int, winY: Int, cardW: Int, cardH: Int): Rectangle {
        val left = winX + DockCardMetrics.CANVAS_WIDTH - DockCardMetrics.CARD_MARGIN - cardW
        val top = winY + DockCardMetrics.CANVAS_HEIGHT - DockCardMetrics.CARD_MARGIN - cardH
        return Rectangle(left, top, cardW, cardH)
    }

    // === 1. Source-of-truth parity (guards against the 300-vs-320 drift regression) ===

    @Test
    fun testContractedWidthParityAcrossLayers() {
        assertEquals(
            DockCardAnimations.CARD_WIDTH_CONTRACTED.value.toInt(),
            DockCardMetrics.CARD_WIDTH_CONTRACTED,
            "Rendered contracted width must equal the placement-math contracted width"
        )
        assertEquals(
            DockCardAnimations.CARD_HEIGHT_CONTRACTED.value.toInt(),
            DockCardMetrics.CARD_HEIGHT_CONTRACTED
        )
        for (panel in ExpandedPanel.entries) {
            when (panel) {
                ExpandedPanel.FileExplorer -> assertEquals(DockCardAnimations.CARD_WIDTH_EXPANDED.value.toInt(), panel.expandedWidth)
                ExpandedPanel.Settings -> assertEquals(DockCardAnimations.SETTINGS_WIDTH_EXPANDED.value.toInt(), panel.expandedWidth)
                ExpandedPanel.Pairing -> assertEquals(DockCardAnimations.PAIRING_WIDTH_EXPANDED.value.toInt(), panel.expandedWidth)
            }
        }
        val controller = createController()
        assertEquals(DockCardMetrics.CARD_WIDTH_CONTRACTED, controller.contractedCardWidth)
        assertEquals(DockCardMetrics.CARD_HEIGHT_CONTRACTED, controller.contractedCardHeight)
        assertEquals(DockCardMetrics.CARD_HEIGHT_EXPANDED - DockCardMetrics.CARD_HEIGHT_CONTRACTED, 195)
    }

    // === 2. Window <-> content geometry round trip ===

    @Test
    fun testWindowContentMappingRoundTrip() {
        for (isMacOS in listOf(false, true)) {
            for (cardW in listOf(DockCardMetrics.CARD_WIDTH_CONTRACTED, DockCardMetrics.FILE_EXPLORER_WIDTH_EXPANDED)) {
                val content = DockCardPhysics.windowToContent(-500, 300, cardW, 625, 1420, 760, 25, isMacOS)
                val window = DockCardPhysics.contentToWindow(content.x, content.y, cardW, 625, 1420, 760, 25, isMacOS)
                assertEquals(-500, window.x, "X round trip must be lossless (isMacOS=$isMacOS)")
                assertEquals(300, window.y, "Y round trip must be lossless (isMacOS=$isMacOS)")
                if (!isMacOS) {
                    assertEquals(300 + 760 - 25 - 625, content.y, "Windows anchors content to canvas bottom (screen-space: windowY + inner offset)")
                } else {
                    assertEquals(300 + 25, content.y, "macOS anchors content to canvas top (screen-space: windowY + margin)")
                }
            }
        }
    }

    // === 3. Magnetic snap conflict resolves to the NEAREST edge ===

    @Test
    fun testMagneticSnapConflictPicksNearestEdge() {
        // Card nearly as wide as the area: both edges inside the 20px threshold
        val wideCard = 1900

        // dLeft=15 vs dRight=5 -> right edge is nearer
        val (rightWins, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 15, candidateContentTop = 100,
            cardWidth = wideCard, cardHeight = 430,
            workArea = standard1080p, snapThreshold = 20
        )
        assertEquals(20, rightWins, "Nearest edge (right, 5px) must win over left (15px)")

        // dLeft=8 vs dRight=12 -> left edge is nearer
        val (leftWins, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 8, candidateContentTop = 100,
            cardWidth = wideCard, cardHeight = 430,
            workArea = standard1080p, snapThreshold = 20
        )
        assertEquals(0, leftWins, "Nearest edge (left, 8px) must win over right (12px)")
    }

    @Test
    fun testMagneticSnapSingleEdgeUnchanged() {
        val (snapped, _) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 1590, candidateContentTop = 500,
            cardWidth = 320, cardHeight = 430,
            workArea = standard1080p, snapThreshold = 20
        )
        // 1590 + 320 = 1910 -> 10px INWARD of the right edge -> flush snap to 1920-320
        assertEquals(1600, snapped, "Right-edge snap with contracted 320dp card must align flush to 1920-320")
    }

    // === 4. Work area native -> dp conversion ===

    @Test
    fun testWorkAreaConversionToDpSpace() {
        val converted = standard1080p.toDpSpace(2.0f)
        val expectedScale = DisplayCoordinateSpace.scaleFactor(2.0f)
        assertEquals((standard1080p.right / expectedScale).toInt(), converted.right)
        assertEquals((standard1080p.bottom / expectedScale).toInt(), converted.bottom)
        assertEquals(converted.width, converted.right - converted.left)
        assertEquals(
            (standard1080p.screenBounds.width / expectedScale).toInt(),
            converted.screenBounds.width
        )

        // Identity conversion at density 1f regardless of platform
        assertEquals(standard1080p, standard1080p.toDpSpace(1.0f))
    }

    // === 5. Drawer-to-drawer expansion and valid restore ===

    @Test
    fun testDrawerSwitchUsesSignedDeltaAndRestoresValidPosition() {
        val controller = createController()

        controller.expandPanel(ExpandedPanel.FileExplorer)
        assertEquals(ExpandedPanel.FileExplorer, controller.expandedPanel)

        controller.expandPanel(ExpandedPanel.Settings)
        assertEquals(ExpandedPanel.Settings, controller.expandedPanel)
        assertTrue(controller.isExpanded)

        controller.expandPanel(ExpandedPanel.Pairing)
        assertEquals(ExpandedPanel.Pairing, controller.expandedPanel)

        controller.collapsePanel()
        assertFalse(controller.isExpanded)
        assertNull(controller.expandedPanel)

        // The restored contracted card must be reachable on its display
        val wa = TaskbarWorkAreaProvider.getActiveScreenWorkArea().toDpSpace(controller.density)
        val rect = contentRect(
            controller.windowState.position.x.value.toInt(),
            controller.windowState.position.y.value.toInt(),
            DockCardMetrics.CARD_WIDTH_CONTRACTED,
            DockCardMetrics.CARD_HEIGHT_CONTRACTED
        )
        assertTrue(rect.intersects(Rectangle(wa.left, wa.top, wa.width, wa.height)), "Restored card must remain on its display")
    }

    // === 6. Stranded-window recovery ===

    @Test
    fun testValidateResetsFullyStrandedWindow() {
        val controller = createController()
        controller.hasBeenDragged = true
        controller.windowState.position = WindowPosition((-6000).dp, (-6000).dp)

        controller.validateAndSnapToBounds()

        val wa = TaskbarWorkAreaProvider.getActiveScreenWorkArea().toDpSpace(controller.density)
        val expectedX = TaskbarWorkAreaProvider.calculateRestingX(wa, DockCardMetrics.CANVAS_WIDTH)
        val expectedY = TaskbarWorkAreaProvider.calculateRestingY(wa, DockCardMetrics.CARD_HEIGHT_CONTRACTED)
        assertEquals(expectedX, controller.windowState.position.x.value.toInt())
        assertEquals(expectedY, controller.windowState.position.y.value.toInt())
    }

    @Test
    fun testValidateKeepsLegalWindowPosition() {
        val controller = createController()
        controller.validateAndSnapToBounds()

        val wa = TaskbarWorkAreaProvider.getActiveScreenWorkArea().toDpSpace(controller.density)
        val rect = contentRect(
            controller.windowState.position.x.value.toInt(),
            controller.windowState.position.y.value.toInt(),
            DockCardMetrics.CARD_WIDTH_CONTRACTED,
            DockCardMetrics.CARD_HEIGHT_CONTRACTED
        )
        assertTrue(rect.intersects(Rectangle(wa.left, wa.top, wa.width, wa.height)))
    }

    // === 7. Drag-end sanity clamp keeps a grabbable margin ===

    @Test
    fun testOnDragDeltaClampsOffscreenStranding() {
        val controller = createController()
        controller.onDragDelta(10_000f, 10_000f)
        assertTrue(controller.hasBeenDragged)

        val wa = TaskbarWorkAreaProvider.getActiveScreenWorkArea().toDpSpace(controller.density)
        val grabX = maxOf((DockCardMetrics.CARD_WIDTH_CONTRACTED * 0.2f).toInt(), DockCardPhysics.MIN_GRAB_PX)
        val grabY = maxOf((DockCardMetrics.CARD_HEIGHT_CONTRACTED * 0.2f).toInt(), DockCardPhysics.MIN_GRAB_PX)
        val rect = contentRect(
            controller.windowState.position.x.value.toInt(),
            controller.windowState.position.y.value.toInt(),
            DockCardMetrics.CARD_WIDTH_CONTRACTED,
            DockCardMetrics.CARD_HEIGHT_CONTRACTED
        )
        assertEquals(wa.right - grabX, rect.x, "Extreme positive X must clamp to right-edge grab margin")
        assertEquals(wa.bottom - grabY, rect.y, "Extreme positive Y must clamp to bottom grab margin")
    }

    // === 8. Drag always wins over any pending window animation (single-writer guarantee) ===

    @Test
    fun testDragOverridesPendingAnimationTarget() {
        val controller = createController()
        controller.hasBeenDragged = true
        controller.windowState.position = WindowPosition((-3000).dp, (-3000).dp)

        controller.resetPositionToDefault()
        val baseX = controller.windowState.position.x.value
        val baseY = controller.windowState.position.y.value

        controller.onDragStart(500, 500)
        controller.onDragMove(550, 520, 1.0f)
        controller.onDragEnd()

        val pos = controller.windowState.position
        assertEquals(baseX + 50f, pos.x.value, "Post-drag position must track the gesture delta from the post-reset baseline")
        assertEquals(baseY + 20f, pos.y.value)
        assertTrue(controller.hasBeenDragged)
    }
}
