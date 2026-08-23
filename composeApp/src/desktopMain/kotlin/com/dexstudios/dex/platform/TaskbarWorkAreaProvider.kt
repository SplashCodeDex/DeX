package com.dexstudios.dex.platform

import androidx.compose.ui.unit.IntOffset
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/**
 * Encapsulates the usable display area after subtracting OS taskbar / dock insets.
 */
data class WorkAreaBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val width: Int = right - left,
    val height: Int = bottom - top,
    val insets: Insets = Insets(0, 0, 0, 0),
    val screenBounds: Rectangle = Rectangle(left, top, right - left, bottom - top)
)

/**
 * Multi-monitor DPI-aware Taskbar Work Area Provider.
 *
 * Interoperates with Java AWT GraphicsEnvironment, Toolkit screen insets, and MouseInfo
 * to resolve active display bounds and compute exact resting dock coordinates.
 */
object TaskbarWorkAreaProvider {

    /**
     * Resolves the active screen work area based on the cursor position,
     * falling back to the primary display if cursor tracking is unavailable.
     */
    fun getActiveScreenWorkArea(): WorkAreaBounds {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultDevice = ge.defaultScreenDevice

        val mouseLocation: Point? = try {
            MouseInfo.getPointerInfo()?.location
        } catch (_: Exception) {
            null
        }

        val targetDevice: GraphicsDevice = if (mouseLocation != null) {
            ge.screenDevices?.firstOrNull { device ->
                device.defaultConfiguration.bounds.contains(mouseLocation)
            } ?: defaultDevice
        } else {
            defaultDevice
        }

        return getWorkAreaForDevice(targetDevice)
    }

    /**
     * Resolves the work area for the display containing the cursor.
     */
    fun getWorkAreaForCursor(): WorkAreaBounds = getActiveScreenWorkArea()

    /**
     * Resolves the work area for the display containing the given native (AWT) point,
     * falling back to the cursor-based active screen when the point lies on no device.
     * Anchors spatial decisions to the window's own location instead of the cursor.
     */
    fun getWorkAreaForPoint(x: Int, y: Int): WorkAreaBounds {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device: GraphicsDevice? = try {
            ge.screenDevices?.firstOrNull { it.defaultConfiguration.bounds.contains(x, y) }
        } catch (_: Exception) {
            null
        }
        return if (device != null) getWorkAreaForDevice(device) else getActiveScreenWorkArea()
    }

    /**
     * Computes the taskbar-subtracted work area for a specific GraphicsDevice.
     */
    fun getWorkAreaForDevice(device: GraphicsDevice): WorkAreaBounds {
        val gc = device.defaultConfiguration
        val screenBounds = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)

        val left = screenBounds.x + insets.left
        val top = screenBounds.y + insets.top
        val right = screenBounds.x + screenBounds.width - insets.right
        val bottom = screenBounds.y + screenBounds.height - insets.bottom
        val width = right - left
        val height = bottom - top

        return WorkAreaBounds(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            width = width,
            height = height,
            insets = insets,
            screenBounds = screenBounds
        )
    }

    /**
     * Retrieves taskbar insets for the active monitor.
     */
    fun getActiveScreenInsets(): Insets {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultDevice = ge.defaultScreenDevice
        val mouseLocation: Point? = try { MouseInfo.getPointerInfo()?.location } catch (_: Exception) { null }
        val targetDevice: GraphicsDevice = if (mouseLocation != null) {
            ge.screenDevices?.firstOrNull { it.defaultConfiguration.bounds.contains(mouseLocation) } ?: defaultDevice
        } else {
            defaultDevice
        }
        return Toolkit.getDefaultToolkit().getScreenInsets(targetDevice.defaultConfiguration)
    }

    /**
     * Retrieves full physical screen bounds for the active monitor.
     */
    fun getActiveScreenBounds(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultDevice = ge.defaultScreenDevice
        val mouseLocation: Point? = try { MouseInfo.getPointerInfo()?.location } catch (_: Exception) { null }
        val targetDevice: GraphicsDevice = if (mouseLocation != null) {
            ge.screenDevices?.firstOrNull { it.defaultConfiguration.bounds.contains(mouseLocation) } ?: defaultDevice
        } else {
            defaultDevice
        }
        return targetDevice.defaultConfiguration.bounds
    }

    /**
     * Computes the exact initial/resting window origin (X, Y) on the specified work area.
     *
     * Exact Resting Formula:
     *   X = workArea.right - canvasWidth + RESTING_CANVAS_OVERHANG
     *   Y (Windows/Linux) = workArea.bottom - CANVAS_HEIGHT + RESTING_CANVAS_OVERHANG
     *   Y (macOS) = workArea.top + 10
     *
     * The transparent 1420x760dp canvas intentionally overhangs the work-area edge by
     * RESTING_CANVAS_OVERHANG dp so the card's visible right gap equals
     * CARD_MARGIN - RESTING_CANVAS_OVERHANG.
     */
    fun calculateInitialWindowPosition(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        canvasWidth: Int = DockCardMetrics.CANVAS_WIDTH,
        cardCollapsedHeight: Int = DockCardMetrics.CARD_HEIGHT_CONTRACTED
    ): IntOffset {
        val x = calculateRestingX(workArea, canvasWidth)
        val y = calculateRestingY(workArea, cardCollapsedHeight)
        return IntOffset(x, y)
    }

    private val isMacOS: Boolean by lazy {
        com.dexstudios.dex.platform.DesktopEnvironment.isMacOS
    }

    /**
     * Computes the resting X origin coordinate.
     */
    fun calculateRestingX(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        canvasWidth: Int = DockCardMetrics.CANVAS_WIDTH
    ): Int {
        return workArea.right - canvasWidth + DockCardMetrics.RESTING_CANVAS_OVERHANG
    }

    /**
     * Computes the resting Y origin coordinate.
     * On Windows/Linux: docks above the bottom taskbar via canvas-bottom overhang.
     * On macOS: docks right below the top menu bar (Top_work + 10).
     */
    fun calculateRestingY(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        cardCollapsedHeight: Int = DockCardMetrics.CARD_HEIGHT_CONTRACTED
    ): Int {
        return if (isMacOS) {
            workArea.top + 10
        } else {
            // Anchor the Native OS Window so the UI rests exactly
            // CARD_MARGIN - RESTING_CANVAS_OVERHANG dp above the taskbar:
            // Canvas top = workArea.bottom - CANVAS_HEIGHT + RESTING_CANVAS_OVERHANG.
            workArea.bottom - DockCardMetrics.CANVAS_HEIGHT + DockCardMetrics.RESTING_CANVAS_OVERHANG
        }
    }
}
