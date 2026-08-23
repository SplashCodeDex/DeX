package com.dexstudios.dex.platform

import kotlin.math.roundToInt

/**
 * Converts between the native AWT coordinate space and Compose dp space.
 *
 * - On Windows (and Linux/X11) the AWT GraphicsConfiguration bounds, screen insets and
 *   MouseInfo coordinates are expressed in device pixels, while Compose window geometry
 *   (`WindowState.position`, layout dimensions) is expressed in dp. The conversion factor
 *   is the display density.
 * - On macOS the AWT space is already logical points, which map 1:1 onto dp, so the
 *   factor stays 1f regardless of Retina scale.
 *
 * Every comparison that mixes AWT-derived work areas or cursor positions with dp-based
 * card geometry must normalize through this object first.
 */
object DisplayCoordinateSpace {

    /** Native AWT reports device pixels on these platforms. */
    val awtUsesDevicePixels: Boolean =
        com.dexstudios.dex.platform.DesktopEnvironment.isWindows ||
            com.dexstudios.dex.platform.DesktopEnvironment.isLinux

    /**
     * Native units per dp for the given display density.
     * Degenerate densities (0, negative, NaN, Infinity) safely fall back to 1f.
     */
    fun scaleFactor(density: Float): Float {
        if (!awtUsesDevicePixels) return 1f
        return if (density > 0f && density.isFinite()) density else 1f
    }

    fun nativeToDp(value: Int, density: Float): Int = (value / scaleFactor(density)).roundToInt()

    fun nativeToDp(value: Float, density: Float): Float = value / scaleFactor(density)

    fun dpToNative(value: Int, density: Float): Int = (value * scaleFactor(density)).roundToInt()

    fun dpToNative(value: Float, density: Float): Float = value * scaleFactor(density)
}

/**
 * Returns a copy of these bounds converted from native AWT space into dp space.
 * Identity when the platform's AWT space already matches dp (macOS, density 1f).
 */
fun WorkAreaBounds.toDpSpace(density: Float): WorkAreaBounds {
    val f = DisplayCoordinateSpace.scaleFactor(density)
    if (f == 1f) return this

    val left = DisplayCoordinateSpace.nativeToDp(left, density)
    val top = DisplayCoordinateSpace.nativeToDp(top, density)
    val right = DisplayCoordinateSpace.nativeToDp(right, density)
    val bottom = DisplayCoordinateSpace.nativeToDp(bottom, density)
    val sb = screenBounds
    val scaledScreenBounds = java.awt.Rectangle(
        DisplayCoordinateSpace.nativeToDp(sb.x, density),
        DisplayCoordinateSpace.nativeToDp(sb.y, density),
        DisplayCoordinateSpace.nativeToDp(sb.width, density),
        DisplayCoordinateSpace.nativeToDp(sb.height, density)
    )
    return WorkAreaBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        insets = insets,
        screenBounds = scaledScreenBounds
    )
}
