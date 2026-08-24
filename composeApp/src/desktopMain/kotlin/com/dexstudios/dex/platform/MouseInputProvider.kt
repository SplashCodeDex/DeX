package com.dexstudios.dex.platform

import com.sun.jna.Library
import com.sun.jna.Native

interface MouseInputProvider {
    fun isLeftMouseButtonDown(): Boolean
    fun getCursorPosition(): Pair<Int, Int>
}

object DesktopMouseInputProvider : MouseInputProvider {

    /**
     * Minimal CoreGraphics binding for macOS left-button state. CGEventSourceButtonState
     * reports the physical button regardless of window focus, mirroring the semantics of
     * GetAsyncKeyState on Windows. Coordinates on macOS are logical points, which matches
     * DisplayCoordinateSpace's identity conversion.
     */
    private interface CoreGraphicsLib : Library {
        @Suppress("FunctionName")
        fun CGEventSourceButtonState(stateID: Int, button: Int): Boolean
    }

    private const val kCGEventSourceStateCombinedSessionState = 0
    private const val kCGMouseButtonLeft = 0

    private val coreGraphics: CoreGraphicsLib? by lazy {
        if (!com.dexstudios.dex.platform.DesktopEnvironment.isMacOS) {
            null
        } else {
            try {
                Native.load("CoreGraphics", CoreGraphicsLib::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }

    override fun isLeftMouseButtonDown(): Boolean {
        if (com.dexstudios.dex.platform.DesktopEnvironment.isWindows) {
            try {
                val lButton = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01).toInt()
                return (lButton and 0x8000) != 0
            } catch (e: Throwable) {
                return false
            }
        }
        if (com.dexstudios.dex.platform.DesktopEnvironment.isMacOS) {
            val cg = coreGraphics ?: return false
            return try {
                cg.CGEventSourceButtonState(kCGEventSourceStateCombinedSessionState, kCGMouseButtonLeft)
            } catch (_: Throwable) {
                false
            }
        }
        // Other platforms: AWT cannot report global button state reliably
        return false
    }

    override fun getCursorPosition(): Pair<Int, Int> {
        if (com.dexstudios.dex.platform.DesktopEnvironment.isWindows) {
            try {
                val point = com.sun.jna.platform.win32.WinDef.POINT()
                com.sun.jna.platform.win32.User32.INSTANCE.GetCursorPos(point)
                return Pair(point.x, point.y)
            } catch (e: Throwable) {
                return Pair(0, 0)
            }
        }
        // macOS/Linux: AWT reports coordinates in the platform's native space
        return try {
            val info = java.awt.MouseInfo.getPointerInfo() ?: return Pair(0, 0)
            val loc = info.location
            Pair(loc.x, loc.y)
        } catch (_: Throwable) {
            Pair(0, 0)
        }
    }
}
