package com.dexstudios.dex.window

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.WorkAreaBounds
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.isActive

/**
 * Central State Machine and Kinematics Controller for the DeX Floating Docked Window.
 *
 * Coordinates:
 * - Window visibility, pinning, modal dialogues, pairing state
 * - Multi-monitor resting position calculations
 * - 3-phase drag gestures with high-DPI scaling and 20px magnetic edge snapping
 * - 5-point focus loss deactivation guard
 * - Dynamic Nudge-ForExpand boundary math and contraction clamping
 * - 450ms atomic 2D double-tap position reset
 */
class DockedWindowStateController(
    val scope: CoroutineScope,
    val windowState: WindowState = WindowState(
        size = DpSize(TaskbarWorkAreaProvider.DEFAULT_CANVAS_WIDTH.dp, TaskbarWorkAreaProvider.DEFAULT_CANVAS_HEIGHT.dp),
        position = WindowPosition(0.dp, 0.dp)
    ),
    var density: Float = 1.0f,
    val mouseInputProvider: com.dexstudios.dex.platform.MouseInputProvider = com.dexstudios.dex.platform.DesktopMouseInputProvider
) {
    val canvasWidth = TaskbarWorkAreaProvider.DEFAULT_CANVAS_WIDTH
    val canvasHeight = TaskbarWorkAreaProvider.DEFAULT_CANVAS_HEIGHT
    val cardMargin = TaskbarWorkAreaProvider.CARD_MARGIN
    val contractedCardWidth = TaskbarWorkAreaProvider.DEFAULT_CARD_CONTRACTED_WIDTH
    val contractedCardHeight = TaskbarWorkAreaProvider.DEFAULT_CARD_CONTRACTED_HEIGHT

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

    init {
        recalculateDefaultDockPosition()
    }

    /**
     * Recomputes resting dock coordinates above the taskbar on the active monitor.
     * Formula:
     *   X = workArea.right - 1420 + 12
     *   Y = workArea.bottom - 430 - 38
     */
    fun recalculateDefaultDockPosition() {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val defaultX = TaskbarWorkAreaProvider.calculateRestingX(workArea, canvasWidth)
        val defaultY = TaskbarWorkAreaProvider.calculateRestingY(workArea, contractedCardHeight)
        windowState.position = WindowPosition(defaultX.dp, defaultY.dp)
    }

    private var dragDropDeferJob: kotlinx.coroutines.Job? = null

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
        dragDropDeferJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                while (isActive) {
                    if (!mouseInputProvider.isLeftMouseButtonDown()) {
                        // Mouse released!
                        val (cursorX, cursorY) = mouseInputProvider.getCursorPosition()
                        
                        val winX = windowState.position.x.value.toInt()
                        val winY = windowState.position.y.value.toInt()
                        
                        val currentCardW = when (expandedPanel) {
                            ExpandedPanel.Settings -> 675
                            ExpandedPanel.Pairing -> 400
                            ExpandedPanel.FileExplorer -> 1054
                            null -> contractedCardWidth
                        }
                        val currentCardH = if (isExpanded) 625 else contractedCardHeight
                        
                        val isMacOS = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
                        val contentLeft = winX + canvasWidth - cardMargin - currentCardW
                        val contentTop = if (isMacOS) {
                            winY + cardMargin
                        } else {
                            winY + canvasHeight - cardMargin - currentCardH
                        }
                        
                        val isInside = cursorX >= contentLeft && cursorX <= contentLeft + currentCardW &&
                                       cursorY >= contentTop && cursorY <= contentTop + currentCardH
                        
                        if (!isInside && shouldDismissOnFocusLoss()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                hide()
                            }
                        }
                        break
                    }
                    kotlinx.coroutines.delay(50)
                }
            } catch (e: Throwable) {}
        }
    }

    fun validateAndSnapToBounds() {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val currentX = windowState.position.x.value.toInt()
        val currentY = windowState.position.y.value.toInt()
        
        // If window is outside the boundaries of the active work area
        val height = workArea.bottom - workArea.top
        if (currentX < workArea.left || currentX > workArea.left + workArea.width ||
            currentY < workArea.top || currentY > workArea.top + height) {
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
     * Executes dynamic Nudge-ForExpand if near display boundaries.
     */
    fun expandPanel(panel: ExpandedPanel) {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val currentX = windowState.position.x.value.toInt()
        val currentY = windowState.position.y.value.toInt()

        if (preExpandX == null) preExpandX = currentX
        if (preExpandY == null) preExpandY = currentY

        val deltaW = when (panel) {
            ExpandedPanel.Settings -> 375 // 675 - 300
            ExpandedPanel.Pairing -> 100  // 400 - 300
            ExpandedPanel.FileExplorer -> 754 // 1054 - 300
        }
        val deltaH = 195 // 625 - 430

        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = currentX,
            currentWindowY = currentY,
            cardWidth = contractedCardWidth,
            cardHeight = contractedCardHeight,
            expandDeltaWidth = deltaW,
            expandDeltaHeight = deltaH,
            workArea = workArea,
            canvasWidth = canvasWidth,
            margin = cardMargin
        )

        expandedPanel = panel

        if (targetX != currentX || targetY != currentY) {
            scope.launch {
                animateWindowTo(targetX, targetY)
            }
        }
    }

    /**
     * Collapses the drawer panel back to the compact card.
     * Restores pre-expansion position or performs contraction clamping (void prevention).
     */
    fun collapsePanel() {
        expandedPanel = null

        val restoreX = preExpandX
        val restoreY = preExpandY
        preExpandX = null
        preExpandY = null

        if (restoreX != null && restoreY != null) {
            scope.launch {
                animateWindowTo(restoreX, restoreY)
            }
        } else {
            // Contraction Clamping (Void Prevention)
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val currentWinX = windowState.position.x.value.toInt()
            val safeWinX = DockCardPhysics.calculateContractionOrigin(
                currentWindowX = currentWinX,
                contractedCardWidth = contractedCardWidth,
                workArea = workArea,
                canvasWidth = canvasWidth,
                margin = cardMargin
            )
            if (safeWinX != currentWinX) {
                scope.launch {
                    animateWindowTo(safeWinX, windowState.position.y.value.toInt())
                }
            }
        }
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
     */
    fun onDragStart(cursorScreenX: Int, cursorScreenY: Int) {
        dragPending = true
        isDragging = false
        dragStartCursorX = cursorScreenX
        dragStartCursorY = cursorScreenY
        dragStartWindowX = windowState.position.x.value.toInt()
        dragStartWindowY = windowState.position.y.value.toInt()
    }

    fun onDragStart(screenX: Float, screenY: Float) {
        onDragStart(screenX.toInt(), screenY.toInt())
    }

    /**
     * Phase 2: Active drag tracking with 5px deadzone filter, high-DPI scaling, and 20px magnetic snapping.
     */
    fun onDragMove(cursorScreenX: Int, cursorScreenY: Int, currentDensity: Float = density) {
        val dxPhysical = cursorScreenX - dragStartCursorX
        val dyPhysical = cursorScreenY - dragStartCursorY

        // Phase 1 check: 5px Manhattan deadzone threshold
        if (dragPending && !isDragging) {
            if (abs(dxPhysical) + abs(dyPhysical) < DockCardPhysics.MANHATTAN_DEADZONE_PX) return
            dragPending = false
            isDragging = true
            hasBeenDragged = true
            preExpandX = null
            preExpandY = null
        }

        if (isDragging) {
            // High-DPI scaling: convert physical mouse deltas to Dp units
            val dpScale = if (currentDensity > 0f) currentDensity else 1.0f
            val dpDx = (dxPhysical / dpScale).toInt()
            val dpDy = (dyPhysical / dpScale).toInt()

            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val candidateX = dragStartWindowX + dpDx
            val candidateY = dragStartWindowY + dpDy

            val currentCardW = when (expandedPanel) {
                ExpandedPanel.Settings -> 675
                ExpandedPanel.Pairing -> 400
                ExpandedPanel.FileExplorer -> 1054
                null -> contractedCardWidth
            }
            val currentCardH = if (isExpanded) 625 else contractedCardHeight

            val isMacOS = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
            val contentLeft = candidateX + canvasWidth - cardMargin - currentCardW
            val contentTop = if (isMacOS) {
                candidateY + cardMargin
            } else {
                candidateY + canvasHeight - cardMargin - currentCardH
            }

            // 20px Magnetic Edge Snapping
            val (snappedLeft, snappedTop) = DockCardPhysics.evaluateMagneticSnap(
                candidateContentLeft = contentLeft,
                candidateContentTop = contentTop,
                cardWidth = currentCardW,
                cardHeight = currentCardH,
                workArea = workArea
            )

            val finalWinX = snappedLeft - canvasWidth + cardMargin + currentCardW
            val finalWinY = if (isMacOS) {
                snappedTop - cardMargin
            } else {
                snappedTop - canvasHeight + cardMargin + currentCardH
            }

            windowState.position = WindowPosition(finalWinX.dp, finalWinY.dp)
        }
    }

    /**
     * Direct delta drag helper.
     */
    fun onDragDelta(deltaX: Float, deltaY: Float) {
        val currentPos = windowState.position
        val newX = currentPos.x.value + deltaX
        val newY = currentPos.y.value + deltaY
        windowState.position = WindowPosition(newX.dp, newY.dp)
        hasBeenDragged = true
    }

    /**
     * Phase 3: Drag release, off-screen sanity clamping.
     */
    fun onDragEnd() {
        if (isDragging) {
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val winX = windowState.position.x.value.toInt()
            val winY = windowState.position.y.value.toInt()

            val currentCardW = when (expandedPanel) {
                ExpandedPanel.Settings -> 675
                ExpandedPanel.Pairing -> 400
                ExpandedPanel.FileExplorer -> 1054
                null -> contractedCardWidth
            }
            val currentCardH = if (isExpanded) 625 else contractedCardHeight

            val isMacOS = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
            val cLeft = winX + canvasWidth - cardMargin - currentCardW
            val cTop = if (isMacOS) {
                winY + cardMargin
            } else {
                winY + canvasHeight - cardMargin - currentCardH
            }

            val (clampedLeft, clampedTop) = DockCardPhysics.applySanityClamp(
                contentLeft = cLeft,
                contentTop = cTop,
                cardWidth = currentCardW,
                cardHeight = currentCardH,
                workArea = workArea
            )

            val finalWinX = clampedLeft - canvasWidth + cardMargin + currentCardW
            val finalWinY = if (isMacOS) {
                clampedTop - cardMargin
            } else {
                clampedTop - canvasHeight + cardMargin + currentCardH
            }

            windowState.position = WindowPosition(finalWinX.dp, finalWinY.dp)
        }
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
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val targetX = TaskbarWorkAreaProvider.calculateRestingX(workArea, canvasWidth)
            val targetY = TaskbarWorkAreaProvider.calculateRestingY(workArea, contractedCardHeight)

            scope.launch {
                animateWindowTo(targetX, targetY)
                hasBeenDragged = false
            }
        }
    }

    /**
     * 3-cycle shake animation (±5px over 50ms per cycle) when double-clicking while pinned.
     */
    fun triggerPinShake() {
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

    private suspend fun animateWindowTo(targetX: Int, targetY: Int) {
        if (coroutineContext[MonotonicFrameClock] != null) {
            val startX = windowState.position.x.value
            val startY = windowState.position.y.value
            val anim = Animatable(0f)

            // Single atomic 2D animation loop: eliminates concurrent coroutine race conditions and diagonal tearing
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
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
