package com.dexstudios.dex.window

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.dexstudios.dex.platform.DisplayCoordinateSpace
import com.dexstudios.dex.platform.DockCardMetrics
import com.dexstudios.dex.platform.GlobalMouseButtonHook
import com.dexstudios.dex.platform.MouseInputProvider
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.WorkAreaBounds
import com.dexstudios.dex.platform.toDpSpace
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Central State Machine and Kinematics Controller for the DeX Floating Docked Window.
 *
 * Coordinates:
 * - Window visibility, pinning, modal dialogues, pairing state
 * - Multi-monitor resting position calculations anchored to the window's own display
 * - 3-phase drag gestures with density-aware scaling and 20px magnetic edge snapping
 * - 5-point focus loss deactivation guard
 * - Dynamic Nudge-ForExpand boundary math with signed panel-switch deltas
 * - Cancellable atomic 2D window animations (450ms double-tap reset, expand, restore)
 */
class DockedWindowStateController(
    val scope: CoroutineScope,
    val windowState: WindowState = WindowState(
        size = DpSize(DockCardMetrics.CANVAS_WIDTH.dp, DockCardMetrics.CANVAS_HEIGHT.dp),
        position = WindowPosition(0.dp, 0.dp),
    ),
    var density: Float = 1.0f,
    val mouseInputProvider: MouseInputProvider = com.dexstudios.dex.platform.DesktopMouseInputProvider,
) {
    val canvasWidth = DockCardMetrics.CANVAS_WIDTH
    val canvasHeight = DockCardMetrics.CANVAS_HEIGHT
    val cardMargin = DockCardMetrics.CARD_MARGIN
    val contractedCardWidth = DockCardMetrics.CARD_WIDTH_CONTRACTED
    val contractedCardHeight = DockCardMetrics.CARD_HEIGHT_CONTRACTED

    private val isMacOS = com.dexstudios.dex.platform.DesktopEnvironment.isMacOS

    var isVisible by mutableStateOf(false)
    var isPinned by mutableStateOf(false)
    var isShowingTransition by mutableStateOf(false)
    var hasBeenDragged by mutableStateOf(false)
    var isPairingActive by mutableStateOf(false)
    var isModalDialogOpen by mutableStateOf(false) // Guards focus loss during native OS file pickers

    var expandedPanel by mutableStateOf<ExpandedPanel?>(null)
    val isExpanded: Boolean get() = expandedPanel != null

    var isDragging by mutableStateOf(false)
    var dragPending by mutableStateOf(false)
    var isShaking by mutableStateOf(false)

    private var preExpandX: Int? = null
    private var preExpandY: Int? = null

    // Drag tracking baseline coordinates
    private var dragStartCursorX = 0
    private var dragStartCursorY = 0
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0

    /**
     * Work area resolved once at gesture start from the window's own location.
     * Keeps snap/clamp reference stable even when the cursor crosses monitors mid-drag.
     */
    private var dragGestureWorkArea: WorkAreaBounds? = null

    /** Single writer for window-position animations; re-entry cancels the previous run. */
    private var positionAnimationJob: Job? = null

    init {
        recalculateDefaultDockPosition()
    }

    // === Content geometry helpers (single source of truth: DockCardMetrics) ===

    private fun currentContentWidth(): Int = expandedPanel?.expandedWidth ?: DockCardMetrics.CARD_WIDTH_CONTRACTED

    private fun currentContentHeight(): Int = if (expandedPanel != null && expandedPanel != ExpandedPanel.Pairing) {
        DockCardMetrics.CARD_HEIGHT_EXPANDED
    } else {
        // WPF parity: the pairing view slides into the fixed-size column without growing
        // the card, so window placement math must treat Pairing as contracted-height too.
        DockCardMetrics.CARD_HEIGHT_CONTRACTED
    }

    private fun windowToContent(winX: Int, winY: Int): IntOffset = DockCardPhysics.windowToContent(
        windowX = winX,
        windowY = winY,
        cardWidth = currentContentWidth(),
        cardHeight = currentContentHeight(),
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        margin = cardMargin,
        isMacOS = isMacOS,
    )

    private fun contentToWindow(contentLeft: Int, contentTop: Int): IntOffset = DockCardPhysics.contentToWindow(
        contentLeft = contentLeft,
        contentTop = contentTop,
        cardWidth = currentContentWidth(),
        cardHeight = currentContentHeight(),
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        margin = cardMargin,
        isMacOS = isMacOS,
    )

    /**
     * Resolves the work area of the display that currently owns most of the card content.
     * Falls back to the cursor-based active screen when the point lies on no device.
     */
    private fun resolveWindowWorkArea(): WorkAreaBounds {
        val winX = windowState.position.x.value.toInt()
        val winY = windowState.position.y.value.toInt()
        val (contentLeft, contentTop) = windowToContent(winX, winY)
        val centerDpX = contentLeft + currentContentWidth() / 2
        val centerDpY = contentTop + currentContentHeight() / 2
        return TaskbarWorkAreaProvider.getWorkAreaForPoint(
            DisplayCoordinateSpace.dpToNative(centerDpX, density),
            DisplayCoordinateSpace.dpToNative(centerDpY, density),
        )
    }

    private fun anchoredWorkAreaInDp(densityOverride: Float = density): WorkAreaBounds {
        val area = if (isDragging) {
            dragGestureWorkArea ?: resolveWindowWorkArea()
        } else {
            resolveWindowWorkArea()
        }
        return area.toDpSpace(densityOverride)
    }

    /**
     * Recomputes resting dock coordinates above the taskbar on the active monitor.
     * Formula:
     *   X = workArea.right - CANVAS_WIDTH + RESTING_CANVAS_OVERHANG
     *   Y = workArea.bottom - CANVAS_HEIGHT + RESTING_CANVAS_OVERHANG   (Windows/Linux)
     *   Y = workArea.top + 10                                           (macOS)
     */
    fun recalculateDefaultDockPosition() {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea().toDpSpace(density)
        val defaultX = TaskbarWorkAreaProvider.calculateRestingX(workArea, canvasWidth)
        val defaultY = TaskbarWorkAreaProvider.calculateRestingY(workArea, contractedCardHeight)
        windowState.position = WindowPosition(defaultX.dp, defaultY.dp)
    }

    private var dragDropDeferJob: Job? = null

    /**
     * 5-point safety guard for focus loss deactivation:
     * Card auto-dismisses on click-outside UNLESS:
     * 1. isPinned: User pinned card to screen
     * 2. isShowingTransition: Mid-animation entrance/exit
     * 3. isPairingActive: Active PIN/QR pairing session in progress
     * 4. isExpanded: File Explorer / Settings drawer is open (external drag-and-drop)
     * 5. isModalDialogOpen: Native OS file/folder picker dialog currently has focus
     * 6. JNA Drag-and-Drop Hook: User is actively holding the left mouse button (dragging a file)
     */
    fun shouldDismissOnFocusLoss(): Boolean {
        if (mouseInputProvider.isLeftMouseButtonDown()) {
            // Left mouse is actively held down (likely an external drag and drop in progress)
            deferHideOnDragDrop()
            return false
        }
        return !isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen
    }

    private fun deferHideOnDragDrop() {
        if (dragDropDeferJob?.isActive == true) return
        dragDropDeferJob = scope.launch(Dispatchers.IO) {
            try {
                // PERF-01: release detection is event-driven on Windows via the global
                // WH_MOUSE_LL hook (no busy-waiting at all). The 2s timeout + authoritative
                // re-check closes the subscribe/release race; macOS and test stubs keep the
                // lightweight 50ms poll as their fallback.
                val useHook = mouseInputProvider is com.dexstudios.dex.platform.DesktopMouseInputProvider &&
                    GlobalMouseButtonHook.ensureInstalled()
                while (isActive) {
                    if (!mouseInputProvider.isLeftMouseButtonDown()) {
                        // Mouse released!
                        val (cursorNativeX, cursorNativeY) = mouseInputProvider.getCursorPosition()

                        val winX = windowState.position.x.value.toInt()
                        val winY = windowState.position.y.value.toInt()
                        val (contentLeft, contentTop) = windowToContent(winX, winY)
                        val cardW = currentContentWidth()
                        val cardH = currentContentHeight()

                        val cursorDpX = DisplayCoordinateSpace.nativeToDp(cursorNativeX, density)
                        val cursorDpY = DisplayCoordinateSpace.nativeToDp(cursorNativeY, density)

                        val isInside = cursorDpX >= contentLeft && cursorDpX <= contentLeft + cardW &&
                            cursorDpY >= contentTop && cursorDpY <= contentTop + cardH

                        if (!isInside && shouldDismissOnFocusLoss()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                hide()
                            }
                        }
                        break
                    }
                    if (useHook) {
                        withTimeoutOrNull(2_000) { GlobalMouseButtonHook.leftButtonUpEvents.first() }
                    } else {
                        delay(50)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Validates that the card content still intersects its own display's work area;
     * restores the default dock when the window ended up fully stranded off-screen.
     */
    fun validateAndSnapToBounds() {
        val workArea = anchoredWorkAreaInDp()
        val winX = windowState.position.x.value.toInt()
        val winY = windowState.position.y.value.toInt()
        val (contentLeft, contentTop) = windowToContent(winX, winY)
        val cardW = currentContentWidth()
        val cardH = currentContentHeight()

        val intersects = contentLeft < workArea.right && contentLeft + cardW > workArea.left &&
            contentTop < workArea.bottom && contentTop + cardH > workArea.top
        if (!intersects) {
            recalculateDefaultDockPosition()
        }
    }

    fun show() {
        if (!hasBeenDragged) {
            recalculateDefaultDockPosition()
        } else {
            validateAndSnapToBounds()
        }
        isVisible = true
    }

    var lastHideTime: Long = 0L

    fun hide() {
        isVisible = false
        lastHideTime = System.currentTimeMillis()
        if (isExpanded) {
            contractPanel()
        }
    }

    fun toggleVisibility() {
        if (isVisible) hide() else show()
    }

    /**
     * Expands the card leftward/downward to reveal the specified drawer panel.
     * Executes dynamic Nudge-ForExpand if near display boundaries. Deltas are computed
     * against the CURRENT content size so direct drawer-to-drawer switches stay correct.
     */
    fun expandPanel(panel: ExpandedPanel) {
        cancelPositionAnimation()
        val currentW = currentContentWidth()
        val currentH = currentContentHeight()

        if (preExpandX == null) preExpandX = windowState.position.x.value.toInt()
        if (preExpandY == null) preExpandY = windowState.position.y.value.toInt()

        val deltaW = panel.expandedWidth - currentW
        val deltaH = DockCardMetrics.CARD_HEIGHT_EXPANDED - currentH

        val workArea = anchoredWorkAreaInDp()
        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = windowState.position.x.value.toInt(),
            currentWindowY = windowState.position.y.value.toInt(),
            cardWidth = currentW,
            cardHeight = currentH,
            expandDeltaWidth = deltaW,
            expandDeltaHeight = deltaH,
            workArea = workArea,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            margin = cardMargin,
            isMacOS = isMacOS,
        )

        expandedPanel = panel

        animateWindowTo(targetX, targetY)
    }

    /**
     * Collapses the drawer panel back to the compact card.
     * Restores pre-expansion position (re-clamped against the current work area), or
     * performs contraction clamping (void prevention) when no restore point exists.
     */
    fun collapsePanel() {
        cancelPositionAnimation()
        expandedPanel = null

        val restoreX = preExpandX
        val restoreY = preExpandY
        preExpandX = null
        preExpandY = null

        val workArea = anchoredWorkAreaInDp()

        val target: IntOffset = if (restoreX != null && restoreY != null) {
            // Restore must re-clamp only: magnetic snapping here would corrupt exact
            // restoration whenever the pre-expand spot sits within 20px of an edge.
            val (clampedLeft, clampedTop) = DockCardPhysics.applySanityClamp(
                contentLeft = restoreX + canvasWidth - cardMargin - contractedCardWidth,
                contentTop = if (isMacOS) {
                    restoreY + cardMargin
                } else {
                    restoreY + canvasHeight - cardMargin - contractedCardHeight
                },
                cardWidth = contractedCardWidth,
                cardHeight = contractedCardHeight,
                workArea = workArea,
            )
            DockCardPhysics.contentToWindow(
                clampedLeft,
                clampedTop,
                contractedCardWidth,
                contractedCardHeight,
                canvasWidth,
                canvasHeight,
                cardMargin,
                isMacOS,
            )
        } else {
            // Contraction Clamping (Void Prevention)
            val currentWinX = windowState.position.x.value.toInt()
            val safeWinX = DockCardPhysics.calculateContractionOrigin(
                currentWindowX = currentWinX,
                contractedCardWidth = contractedCardWidth,
                workArea = workArea,
                canvasWidth = canvasWidth,
                margin = cardMargin,
            )
            IntOffset(safeWinX, windowState.position.y.value.toInt())
        }

        animateWindowTo(target.x, target.y)
    }

    fun contractPanel() = collapsePanel()

    fun togglePanel(panel: ExpandedPanel) {
        if (expandedPanel == panel) {
            collapsePanel()
        } else {
            expandPanel(panel)
        }
    }

    /**
     * Phase 1: Initiates pending drag state from mouse coordinates.
     * Cancels any in-flight window animation so the user's grab always wins.
     */
    fun onDragStart(cursorScreenX: Int, cursorScreenY: Int) {
        cancelPositionAnimation()
        dragPending = true
        isDragging = false
        dragStartCursorX = cursorScreenX
        dragStartCursorY = cursorScreenY
        dragStartWindowX = windowState.position.x.value.toInt()
        dragStartWindowY = windowState.position.y.value.toInt()
        dragGestureWorkArea = resolveWindowWorkArea()
    }

    fun onDragStart(screenX: Float, screenY: Float) {
        onDragStart(screenX.roundToInt(), screenY.roundToInt())
    }

    /**
     * Phase 2: Active drag tracking with a density-aware 5dp Manhattan deadzone filter,
     * high-DPI cursor scaling, and 20px magnetic snapping against the gesture-stable
     * work area captured at drag start.
     */
    fun onDragMove(cursorScreenX: Int, cursorScreenY: Int, currentDensity: Float = density) {
        val dxPhysical = cursorScreenX - dragStartCursorX
        val dyPhysical = cursorScreenY - dragStartCursorY

        // Phase 1 check: 5dp Manhattan deadzone threshold (scaled to physical px)
        if (dragPending && !isDragging) {
            val scale = DisplayCoordinateSpace.scaleFactor(currentDensity)
            val logicalDistance = abs(dxPhysical / scale) + abs(dyPhysical / scale)
            if (logicalDistance < DockCardPhysics.MANHATTAN_DEADZONE_PX) return
            dragPending = false
            isDragging = true
            hasBeenDragged = true
            preExpandX = null
            preExpandY = null
        }

        if (!isDragging) return

        // High-DPI scaling: convert physical mouse deltas to dp units
        val dpScale = DisplayCoordinateSpace.scaleFactor(currentDensity)
        val dpDx = (dxPhysical / dpScale).roundToInt()
        val dpDy = (dyPhysical / dpScale).roundToInt()

        val candidateX = dragStartWindowX + dpDx
        val candidateY = dragStartWindowY + dpDy

        val cardW = currentContentWidth()
        val cardH = currentContentHeight()
        val (candidateContentLeft, candidateContentTop) = DockCardPhysics.windowToContent(
            candidateX,
            candidateY,
            cardW,
            cardH,
            canvasWidth,
            canvasHeight,
            cardMargin,
            isMacOS,
        )

        // 20px Magnetic Edge Snapping (inward-only, stable per-gesture work area)
        val (snappedLeft, snappedTop) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = candidateContentLeft,
            candidateContentTop = candidateContentTop,
            cardWidth = cardW,
            cardHeight = cardH,
            workArea = anchoredWorkAreaInDp(currentDensity),
        )

        val finalWin = DockCardPhysics.contentToWindow(
            snappedLeft,
            snappedTop,
            cardW,
            cardH,
            canvasWidth,
            canvasHeight,
            cardMargin,
            isMacOS,
        )
        windowState.position = WindowPosition(finalWin.x.dp, finalWin.y.dp)
    }

    /**
     * Direct delta drag helper (fallback when absolute cursor tracking is unavailable).
     * Applies off-screen sanity clamping without magnetic snapping to preserve exact deltas.
     */
    fun onDragDelta(deltaX: Float, deltaY: Float) {
        hasBeenDragged = true
        val workArea = anchoredWorkAreaInDp()
        val winX = windowState.position.x.value
        val winY = windowState.position.y.value
        val candidateX = winX + deltaX
        val candidateY = winY + deltaY

        val cardW = currentContentWidth()
        val cardH = currentContentHeight()
        val (candidateLeft, candidateTop) = DockCardPhysics.windowToContent(
            candidateX.toInt(),
            candidateY.toInt(),
            cardW,
            cardH,
            canvasWidth,
            canvasHeight,
            cardMargin,
            isMacOS,
        )
        val (clampedLeft, clampedTop) = DockCardPhysics.applySanityClamp(
            contentLeft = candidateLeft,
            contentTop = candidateTop,
            cardWidth = cardW,
            cardHeight = cardH,
            workArea = workArea,
        )
        val finalWin = DockCardPhysics.contentToWindow(
            clampedLeft,
            clampedTop,
            cardW,
            cardH,
            canvasWidth,
            canvasHeight,
            cardMargin,
            isMacOS,
        )
        windowState.position = WindowPosition(finalWin.x.dp, finalWin.y.dp)
    }

    /**
     * Phase 3: Drag release, off-screen sanity clamping against the gesture-stable work
     * area. When the card was dragged fully onto another display, the clamp re-targets
     * the display owning the released content instead of yanking it back.
     */
    fun onDragEnd() {
        if (isDragging) {
            val winX = windowState.position.x.value.toInt()
            val winY = windowState.position.y.value.toInt()
            val cardW = currentContentWidth()
            val cardH = currentContentHeight()

            var gestureArea = anchoredWorkAreaInDp()
            val (cLeft, cTop) = DockCardPhysics.windowToContent(
                winX,
                winY,
                cardW,
                cardH,
                canvasWidth,
                canvasHeight,
                cardMargin,
                isMacOS,
            )
            val sb = gestureArea.screenBounds
            val fullyOutsideGestureDisplay =
                cLeft >= sb.x + sb.width || cLeft + cardW <= sb.x ||
                    cTop >= sb.y + sb.height || cTop + cardH <= sb.y
            if (fullyOutsideGestureDisplay) {
                dragGestureWorkArea = resolveWindowWorkArea()
                gestureArea = dragGestureWorkArea!!.toDpSpace(density)
            }

            val (clampedLeft, clampedTop) = DockCardPhysics.applySanityClamp(
                contentLeft = cLeft,
                contentTop = cTop,
                cardWidth = cardW,
                cardHeight = cardH,
                workArea = gestureArea,
            )

            val finalWin = DockCardPhysics.contentToWindow(
                clampedLeft,
                clampedTop,
                cardW,
                cardH,
                canvasWidth,
                canvasHeight,
                cardMargin,
                isMacOS,
            )
            windowState.position = WindowPosition(finalWin.x.dp, finalWin.y.dp)
        }
        dragGestureWorkArea = null
        dragPending = false
        isDragging = false
    }

    fun onDoubleTapReset() {
        resetPositionToDefault()
    }

    /**
     * Reset card position to default resting dock coordinates via atomic 2D animation.
     */
    fun resetPositionToDefault() {
        if (isPinned) {
            triggerPinShake()
            return
        }
        if (hasBeenDragged) {
            cancelPositionAnimation()
            val workArea = anchoredWorkAreaInDp()
            val targetX = TaskbarWorkAreaProvider.calculateRestingX(workArea, canvasWidth)
            val targetY = TaskbarWorkAreaProvider.calculateRestingY(workArea, contractedCardHeight)

            animateWindowTo(targetX, targetY)
            positionAnimationJob?.invokeOnCompletion { cause -> if (cause == null) hasBeenDragged = false }
        }
    }

    /**
     * 3-cycle shake animation (±5px over 50ms per cycle) when double-clicking while pinned.
     */
    fun triggerPinShake() {
        cancelPositionAnimation()
        scope.launch {
            isShaking = true
            val baseX = windowState.position.x.value
            val baseY = windowState.position.y.value
            val offsets = listOf(5f, -5f, 4f, -4f, 2f, -2f, 0f)
            for (offset in offsets) {
                windowState.position = WindowPosition((baseX + offset).dp, baseY.dp)
                delay(25)
            }
            windowState.position = WindowPosition(baseX.dp, baseY.dp)
            isShaking = false
        }
    }

    private fun cancelPositionAnimation() {
        positionAnimationJob?.cancel()
        positionAnimationJob = null
    }

    /**
     * Launches the single atomic 2D window-position animation toward the target.
     * Any previously running animation is cancelled first, guaranteeing exactly one
     * coroutine ever writes [windowState.position] at a time.
     */
    private fun animateWindowTo(targetX: Int, targetY: Int) {
        val currentX = windowState.position.x.value.toInt()
        val currentY = windowState.position.y.value.toInt()
        if (currentX == targetX && currentY == targetY) return
        cancelPositionAnimation()
        positionAnimationJob = scope.launch {
            if (coroutineContext[MonotonicFrameClock] != null) {
                val startX = windowState.position.x.value
                val startY = windowState.position.y.value
                val anim = Animatable(0f)

                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = com.dexstudios.dex.window.kinematics.DockCardAnimations.ExpansionSettleSpec,
                ) {
                    val curX = startX + (targetX - startX) * value
                    val curY = startY + (targetY - startY) * value
                    windowState.position = WindowPosition(curX.dp, curY.dp)
                }
            } else {
                // Headless / Unit-test coroutine scope fallback when no MonotonicFrameClock is attached
                windowState.position = WindowPosition(targetX.dp, targetY.dp)
            }
        }
    }
}
