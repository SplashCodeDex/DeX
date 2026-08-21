package com.dexstudios.dex.platform

interface MouseInputProvider {
    fun isLeftMouseButtonDown(): Boolean
    fun getCursorPosition(): Pair<Int, Int>
}

object DesktopMouseInputProvider : MouseInputProvider {
    override fun isLeftMouseButtonDown(): Boolean {
        if (com.dexstudios.dex.platform.DesktopEnvironment.isWindows) {
            try {
                val lButton = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01).toInt()
                return (lButton and 0x8000) != 0
            } catch (e: Throwable) {
                return false
            }
        }
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
        return Pair(0, 0)
    }
}
