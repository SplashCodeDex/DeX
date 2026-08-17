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

    const val DEFAULT_CANVAS_WIDTH = 1420
    const val DEFAULT_CANVAS_HEIGHT = 760
    const val DEFAULT_CARD_CONTRACTED_WIDTH = 300
    const val DEFAULT_CARD_CONTRACTED_HEIGHT = 430
    const val CARD_MARGIN = 25
    const val RESTING_RIGHT_GAP = 13
    const val RESTING_TASKBAR_GAP = 38

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
     *   X = workArea.right - canvasWidth + 12 (i.e. Right_work - 1420 + 12)
     *   Y = workArea.bottom - cardCollapsedHeight - 38 (i.e. Bottom_work - 430 - 38)
     */
    fun calculateInitialWindowPosition(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        canvasWidth: Int = DEFAULT_CANVAS_WIDTH,
        cardCollapsedHeight: Int = DEFAULT_CARD_CONTRACTED_HEIGHT
    ): IntOffset {
        val x = calculateRestingX(workArea, canvasWidth)
        val y = calculateRestingY(workArea, cardCollapsedHeight)
        return IntOffset(x, y)
    }

    private val isMacOS: Boolean by lazy {
        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
    }

    /**
     * Computes the resting X origin coordinate.
     */
    fun calculateRestingX(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        canvasWidth: Int = DEFAULT_CANVAS_WIDTH
    ): Int {
        return workArea.right - canvasWidth + 12
    }

    /**
     * Computes the resting Y origin coordinate.
     * On Windows/Linux: Docks above the bottom taskbar (Bottom_work - 430 - 38).
     * On macOS: Docks right below the top menu bar (Top_work + 10).
     */
    fun calculateRestingY(
        workArea: WorkAreaBounds = getActiveScreenWorkArea(),
        cardCollapsedHeight: Int = DEFAULT_CARD_CONTRACTED_HEIGHT
    ): Int {
        return if (isMacOS) {
            workArea.top + 10
        } else {
            workArea.bottom - cardCollapsedHeight - 38
        }
    }
}
