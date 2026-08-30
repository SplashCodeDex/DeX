package com.dexstudios.dex.ui.share

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import timber.log.Timber
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Minimal lifecycle machinery that lets Compose run inside a system overlay
 * window. Compose and `collectAsStateWithLifecycle` refuse to work without a
 * [LifecycleOwner], [ViewModelStoreOwner] and [SavedStateRegistryOwner] reachable
 * through the view tree — none of which exist above an activity — so this host
 * fabricates and drives all three.
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun performCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun performStart() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun performResume() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun performPause() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun performStop() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

    fun performDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/**
 * Hosts the share panel in a real `TYPE_APPLICATION_OVERLAY` window so it floats
 * above the sharing app (and everything else) — the system-level equivalent of
 * AirDrop's floating picker. Requires the user-granted `SYSTEM_ALERT_WINDOW`
 * permission; callers must check [canShowOverlay] first and fall back to the
 * in-activity sheet otherwise.
 *
 * Window behavior: `FLAG_NOT_TOUCH_MODAL` lets touches outside the panel reach
 * whatever is below (the source app, since the trampoline activity is
 * non-touchable), while `FLAG_WATCH_OUTSIDE_TOUCH` converts an outside tap into
 * [onDismissRequest] so the panel never traps the user.
 */
class ShareOverlayWindow(
    private val context: Context,
    private val onDismissRequest: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var container: ViewGroup? = null
    private var owner: OverlayLifecycleOwner? = null
    private var dimScrim: ShareDimScrim? = null

    val isShowing: Boolean get() = container != null

    fun show(content: @Composable () -> Unit) {
        if (container != null) return
        check(canShowOverlay(context)) { "SYSTEM_ALERT_WINDOW permission is not granted" }

        val lifecycleOwner = OverlayLifecycleOwner().apply { performCreate() }
        val frame = android.widget.FrameLayout(context).apply {
            // Wire the fake owners before composition starts — Compose resolves
            // them through the view tree when the ComposeView attaches.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            }

            addView(
                ComposeView(context).apply {
                    setContent {
                        MaterialTheme {
                            ShareOverlaySurface(content = content)
                        }
                    }
                },
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.FILL_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // Backdrop first so it sits below the panel in same-type window z-order
        dimScrim = ShareDimScrim(context).also { it.show() }

        windowManager.addView(frame, params)
        lifecycleOwner.performStart()
        lifecycleOwner.performResume()

        container = frame
        owner = lifecycleOwner
    }

    fun dismiss() {
        val frame = container ?: return
        container = null
        val lifecycleOwner = owner
        owner = null

        dimScrim?.dismiss()
        dimScrim = null

        try {
            windowManager.removeView(frame)
        } catch (_: Exception) {
            // Window already detached by the system (e.g. app teardown mid-dismiss)
        }
        lifecycleOwner?.performPause()
        lifecycleOwner?.performStop()
        lifecycleOwner?.performDestroy()
    }

    companion object {
        fun canShowOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}

/**
 * Sheet-like visual scaffold for overlay content — matches the ModalBottomSheet
 * look (surface color, 32dp top corners, tonal elevation) used by the in-activity
 * fallback so both presentations stay visually consistent.
 */
@Composable
private fun ShareOverlaySurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        content()
    }
}

/**
 * Full-screen, fully touch-transparent dim backdrop beneath the overlay panel —
 * the same 0.5-black contrast the activity path gets from its theme dim. Every
 * touch outside the panel passes straight through to whatever is physically
 * beneath; the scrim is pure backdrop and lives and dies with the panel.
 */
class ShareDimScrim(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.FILL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val scrimView = View(context).apply {
            setBackgroundColor(BACKDROP_TINT)
        }
        windowManager.addView(scrimView, params)
        view = scrimView
        Timber.i("ShareDimScrim: dim backdrop attached (%dx%d)", params.width, params.height)
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
            // Window already detached by the system
        }
    }

    private companion object {
        // 0.5 black — same contrast as the activity theme's backgroundDimAmount
        const val BACKDROP_TINT = 0x80000000.toInt()
    }
}