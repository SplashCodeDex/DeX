package com.dexstudios.dex.platform

import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle

/**
 * Platform helper for querying multi-monitor screen bounds, graphics devices, and taskbar insets.
 */
object ScreenBoundsHelper {

    /**
     * Resolves the active work area bounds for the display currently containing the cursor.
     */
    fun getWorkAreaBounds(): WorkAreaBounds = TaskbarWorkAreaProvider.getActiveScreenWorkArea()

    /**
     * Retrieves taskbar insets for the active display.
     */
    fun getTaskbarInsets(): Insets = TaskbarWorkAreaProvider.getActiveScreenInsets()

    /**
     * Retrieves full physical screen bounds for the active display.
     */
    fun getScreenBounds(): Rectangle = TaskbarWorkAreaProvider.getActiveScreenBounds()

    /**
     * Retrieves all connected graphics screen devices.
     */
    fun getAllScreenDevices(): Array<GraphicsDevice> {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        return ge.screenDevices ?: emptyArray()
    }

    /**
     * Checks whether multiple monitors are present.
     */
    fun isMultiMonitor(): Boolean {
        return getAllScreenDevices().size > 1
    }
}
