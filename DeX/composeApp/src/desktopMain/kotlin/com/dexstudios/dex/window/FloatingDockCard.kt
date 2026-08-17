package com.dexstudios.dex.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.core.designsystem.theme.LocalBackdrop
import com.dexstudios.dex.window.kinematics.popInTransition
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.koin.compose.koinInject

/**
 * Root Floating Docked Card Window Canvas (1420x760 dp).
 *
 * Provides:
 * - Fixed 1420x760dp transparent canvas with Alignment.TopEnd and 25dp padding
 * - Zero-flicker internal expansion (eliminates OS Direct3D swapchain recreation stutter)
 * - Pop-in entrance animation (scale 0.85 -> 1.0, translateY 15 -> 0 dp, alpha 0 -> 1 over 500ms)
 * - Continuous high-DPI display density synchronization
 * - Bound directly to DockedWindowStateController
 * - CompositionLocalProvider for LocalBackdrop
 */
@Composable
fun FloatingDockCard(
    controller: DockedWindowStateController,
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit,
    modifier: Modifier = Modifier,
    pairingEngine: PairingEngine = koinInject()
) {
    // Synchronize current monitor display density for 1:1 tactile drag scaling
    val density = LocalDensity.current.density
    LaunchedEffect(density) {
        controller.density = density
    }

    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        // 1420x760 Transparent Bounding Canvas
        Box(modifier = modifier.fillMaxSize()) {
            // The actual card container, anchored strictly to TopEnd with 25dp padding
            DockCardContent(
                controller = controller,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 25.dp, end = 25.dp)
                    .popInTransition(visible = controller.isVisible),
                backdrop = backdrop,
                onDismiss = onDismiss,
                onExitEngine = onExitEngine,
                pairingEngine = pairingEngine
            )
        }
    }
}
