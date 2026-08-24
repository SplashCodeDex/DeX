package com.dexstudios.dex.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Process-wide Windows low-level mouse hook (WH_MOUSE_LL) that emits an event the instant
 * the left mouse button is released ANYWHERE on the desktop - including drags that started
 * in other applications (Explorer -> dock drop flows), which window-scoped AWT listeners
 * never see and GetAsyncKeyState polling can only discover by busy-waiting.
 *
 * Lifecycle: [ensureInstalled] lazily spawns one daemon thread that installs the hook and
 * pumps its message loop for the lifetime of the process. There is intentionally no public
 * teardown - the hook costs nothing while idle and uninstalling mid-gesture would reintroduce
 * the polling fallback exactly when it hurts most.
 *
 * macOS: WH_MOUSE_LL has no equivalent without accessibility permissions, so [isSupported]
 * reports false and callers keep their polling fallback.
 */
object GlobalMouseButtonHook {

    private const val WH_MOUSE_LL = 14
    private const val WM_LBUTTONUP = 0x0202
    private const val WM_QUIT = 0x0012

    private interface HookProc : Callback {
        fun callback(nCode: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT
    }

    // Native method names MUST match the Win32 ABI exactly (PascalCase) for JNA resolution.
    @Suppress("FunctionName")
    private interface User32HookLib : Library {
        fun SetWindowsHookExW(idHook: Int, lpfn: HookProc, hMod: Pointer?, dwThreadId: Int): Pointer?
        fun UnhookWindowsHookEx(hhk: Pointer): Boolean
        fun CallNextHookEx(hhk: Pointer?, nCode: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT
        fun GetMessageW(lpMsg: WinUser.MSG?, hWnd: Pointer?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
        fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Pointer?, lParam: Pointer?): Boolean
        fun GetCurrentThreadId(): Int
    }

    private val user32: User32HookLib? by lazy {
        if (!DesktopEnvironment.isWindows) {
            null
        } else {
            try {
                Native.load("user32", User32HookLib::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private val _leftButtonUp = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits once for every left-button release on the desktop. Never blocks emitters. */
    val leftButtonUpEvents: SharedFlow<Unit> = _leftButtonUp

    @Volatile
    var installed: Boolean = false
        private set

    @Volatile
    private var hookHandle: Pointer? = null

    @Volatile
    private var hookThreadId: Int = 0

    // Strong reference: JNA callbacks are GC-visible through the native side only weakly.
    private var hookProcRef: HookProc? = null

    /**
     * Installs the hook on first call (subsequent calls are no-ops). Returns false when
     * unsupported (non-Windows) or installation failed; callers must fall back to polling.
     */
    @Synchronized
    fun ensureInstalled(): Boolean {
        val lib = user32 ?: return false
        if (installed) return true

        val installLatch = CountDownLatch(1)
        val pump = Thread({
            val proc = object : HookProc {
                override fun callback(nCode: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT {
                    if (nCode >= 0 && wParam.toInt() == WM_LBUTTONUP) {
                        _leftButtonUp.tryEmit(Unit)
                    }
                    return lib.CallNextHookEx(hookHandle, nCode, wParam, lParam)
                }
            }
            // Keep the callback strongly reachable for the process lifetime.
            hookProcRef = proc

            val handle = try {
                lib.SetWindowsHookExW(WH_MOUSE_LL, proc, null, 0)
            } catch (_: Throwable) {
                null
            }
            if (handle == null) {
                installLatch.countDown()
                return@Thread
            }
            hookHandle = handle
            hookThreadId = lib.GetCurrentThreadId()
            installed = true
            installLatch.countDown()

            // Message pump: required for low-level hooks to receive callbacks. Runs until
            // WM_QUIT is posted (never in practice - see lifecycle note above).
            val msg = WinUser.MSG()
            while (lib.GetMessageW(msg, null, 0, 0) > 0) {
                // No translation/dispatch needed: WH_MOUSE_LL is notification-only.
            }
            lib.UnhookWindowsHookEx(handle)
            installed = false
        }, "dex-global-mouse-hook")
        pump.isDaemon = true
        pump.start()

        return installLatch.await(2, TimeUnit.SECONDS) && installed
    }
}
