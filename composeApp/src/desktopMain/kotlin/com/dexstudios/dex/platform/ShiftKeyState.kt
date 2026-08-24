package com.dexstudios.dex.platform

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Global Shift modifier state, focus-independent.
 *
 * Mirrors [DesktopMouseInputProvider]'s polling architecture: JNA against Win32
 * GetAsyncKeyState on Windows (VK_SHIFT covers both left and right Shift) and
 * CoreGraphics CGEventSourceFlagsState on macOS (combined session state, so the
 * report is identical whether or not the dock window has keyboard focus).
 */
object ShiftKeyState {

    private interface CoreGraphicsLib : Library {
        @Suppress("FunctionName")
        fun CGEventSourceFlagsState(stateID: Int): Long
    }

    private const val kCGEventSourceStateCombinedSessionState = 0

    /** kCGEventFlagMaskShift including its device-dependent bit (0x2). */
    private const val kCGEventFlagMaskShift = 0x20002L

    private const val VK_SHIFT = 0x10
    private const val KEY_PRESSED = 0x8000

    private val coreGraphics: CoreGraphicsLib? by lazy {
        if (!DesktopEnvironment.isMacOS) {
            null
        } else {
            try {
                Native.load("CoreGraphics", CoreGraphicsLib::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun isShiftHeldNow(): Boolean {
        if (DesktopEnvironment.isWindows) {
            return try {
                (com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(VK_SHIFT).toInt() and KEY_PRESSED) != 0
            } catch (_: Throwable) {
                false
            }
        }
        if (DesktopEnvironment.isMacOS) {
            val cg = coreGraphics ?: return false
            return try {
                (cg.CGEventSourceFlagsState(kCGEventSourceStateCombinedSessionState) and kCGEventFlagMaskShift) != 0L
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }
}
