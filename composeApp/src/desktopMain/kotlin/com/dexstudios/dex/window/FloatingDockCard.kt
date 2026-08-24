package com.dexstudios.dex.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.platform.DisplayCoordinateSpace
import com.dexstudios.dex.platform.DockCardMetrics
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * Root Floating Docked Card Window Canvas (1420x760 dp).
 *
 * Provides:
 * - Fixed 1420x760dp transparent canvas with Alignment.TopEnd and 25dp padding
 * - Zero-flicker internal expansion (eliminates OS Direct3D swapchain recreation stutter)
 * - Dynamic Island entrance animation (hyper-fluid expansion from TopEnd)
 * - Continuous high-DPI display density synchronization
 * - Bound directly to DockedWindowStateController
 * - Dynamic Native AWT Hit-Test Shape configuration for click-through transparency
 */
@Composable
fun FloatingDockCard(
    window: java.awt.Window,
    controller: DockedWindowStateController,
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit,
    modifier: Modifier = Modifier,
    pairingEngine: PairingEngine = koinInject(),
) {
    // Synchronize current monitor display density for 1:1 tactile drag scaling
    val density = LocalDensity.current.density
    LaunchedEffect(density) {
        controller.density = density
    }

    val isMacOS = remember { com.dexstudios.dex.platform.DesktopEnvironment.isMacOS }

    // Last card rect sent to the OS hit-tester. onGloballyPositioned fires on EVERY layout
    // pass — i.e., every frame of the width/height spring animations — and each native
    // Window#shape assignment recomputes the OS window region. Updates are therefore
    // skipped unless the rect actually moved/resized beyond sub-pixel noise.
    var lastShapedBounds by remember { mutableStateOf<Rect?>(null) }

    // 1420x760 Transparent Bounding Canvas
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && !controller.isPairingActive) {
                    controller.hide()
                    true
                } else {
                    false
                }
            },
    ) {
        // The actual card container, anchored strictly to TopEnd with the metric margin
        DockCardContent(
            controller = controller,
            modifier = Modifier
                .align(if (isMacOS) Alignment.TopEnd else Alignment.BottomEnd)
                .padding(
                    top = if (isMacOS) DockCardMetrics.CARD_MARGIN.dp else 0.dp,
                    bottom = if (isMacOS) 0.dp else DockCardMetrics.CARD_MARGIN.dp,
                    end = DockCardMetrics.CARD_MARGIN.dp,
                )
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    val previous = lastShapedBounds
                    val unchanged = previous != null &&
                        abs(previous.left - bounds.left) < 0.5f &&
                        abs(previous.top - bounds.top) < 0.5f &&
                        abs(previous.width - bounds.width) < 0.5f &&
                        abs(previous.height - bounds.height) < 0.5f
                    if (unchanged) return@onGloballyPositioned
                    lastShapedBounds = bounds

                    // AWT window shape space: device pixels on Windows (compose px pass
                    // through), logical points on macOS (divide by the Retina scale).
                    val awtScale = DisplayCoordinateSpace.scaleFactor(density)
                    val shapeX = bounds.left / awtScale
                    val shapeY = bounds.top / awtScale
                    val shapeWidth = bounds.width / awtScale
                    val shapeHeight = bounds.height / awtScale

                    // Restrict native OS hit-testing to the card bounds for true click-through
                    val hitCorner = DockCardMetrics.AWT_HIT_SHAPE_CORNER_RADIUS
                    window.shape = java.awt.geom.RoundRectangle2D.Float(
                        shapeX,
                        shapeY,
                        shapeWidth,
                        shapeHeight,
                        hitCorner,
                        hitCorner,
                    )
                },
            onDismiss = onDismiss,
            onExitEngine = onExitEngine,
            pairingEngine = pairingEngine,
        )
    }
}
