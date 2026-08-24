package com.dexstudios.dex.desktop.jna

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.*

object GlobalShortcutService {
    private var job: Job? = null
    private var hook: WinUser.HHOOK? = null
    private var hookProc: WinUser.LowLevelKeyboardProc? = null

    fun start(onToggle: () -> Unit) {
        if (!com.dexstudios.dex.platform.DesktopEnvironment.isWindows) return

        job = CoroutineScope(Dispatchers.IO).launch {
            val module = Kernel32.INSTANCE.GetModuleHandle(null)

            hookProc = object : WinUser.LowLevelKeyboardProc {
                override fun callback(nCode: Int, wParam: WinDef.WPARAM, lParam: WinUser.KBDLLHOOKSTRUCT): WinDef.LRESULT {
                    if (nCode >= 0 && wParam.toInt() == WinUser.WM_KEYDOWN) {
                        if (lParam.vkCode == 0x44) { // VK_D
                            val lWin = User32.INSTANCE.GetAsyncKeyState(0x5B).toInt()
                            val rWin = User32.INSTANCE.GetAsyncKeyState(0x5C).toInt()
                            val shift = User32.INSTANCE.GetAsyncKeyState(0x10).toInt()

                            val isWinDown = (lWin and 0x8000) != 0 || (rWin and 0x8000) != 0
                            val isShiftDown = (shift and 0x8000) != 0

                            if (isWinDown && isShiftDown) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    onToggle()
                                }
                                return WinDef.LRESULT(1) // Consume key
                            }
                        }
                    }
                    return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, WinDef.LPARAM(com.sun.jna.Pointer.nativeValue(lParam.pointer)))
                }
            }

            hook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, hookProc, module, 0)

            val msg = WinUser.MSG()
            while (isActive) {
                // GetMessage blocks until a message is received
                val result = User32.INSTANCE.GetMessage(msg, null, 0, 0)
                if (result <= 0) break
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }

            // Cleanup on exit
            stop()
        }
    }

    fun stop() {
        hook?.let {
            User32.INSTANCE.UnhookWindowsHookEx(it)
            hook = null
        }
        hookProc = null
        job?.cancel()
    }
}
