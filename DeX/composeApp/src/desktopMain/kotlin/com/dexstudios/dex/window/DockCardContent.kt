package com.dexstudios.dex.window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.core.designsystem.components.glass.DeXGlassPresets
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPanel
import com.dexstudios.dex.core.designsystem.theme.DeXColors
import com.dexstudios.dex.core.designsystem.theme.LocalBackdrop
import com.dexstudios.dex.window.components.FileExplorerPanel
import com.dexstudios.dex.window.components.PinPairingPanel
import com.dexstudios.dex.window.components.SettingsPanel
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import com.dexstudios.dex.window.styling.skiaDropShadow
import com.dexstudios.dex.window.styling.subpixelBorderGlow
import com.kyant.backdrop.Backdrop

/**
 * Main Content Surface for the DeX Floating Dock Card.
 *
 * Responsibilities:
 * - Animates width (300dp contracted <-> 1054dp expanded / 675dp settings / 400dp pairing)
 * - Animates height (430dp contracted <-> 625dp expanded) with spring(0.65f, 300f)
 * - Container with RoundedCornerShape(34.dp), LiquidGlassPanel styling, and Row layout
 * - GPU Gaussian Drop Shadow via Skia + Subpixel Border Glow
 * - Left animated drawer (FileExplorer, Settings, Pairing)
 * - Right 300dp MainMenuColumn
 */
@Composable
fun DockCardContent(
    controller: DockedWindowStateController,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current,
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit,
    pairingEngine: PairingEngine
) {
    // Animated card width (matches exact WPF ElasticEase spring physics)
    val cardWidth by animateDpAsState(
        targetValue = when {
            !controller.isExpanded -> DockCardAnimations.CARD_WIDTH_CONTRACTED
            controller.expandedPanel == ExpandedPanel.Settings -> DockCardAnimations.SETTINGS_WIDTH_EXPANDED
            controller.expandedPanel == ExpandedPanel.Pairing -> DockCardAnimations.PAIRING_WIDTH_EXPANDED
            else -> DockCardAnimations.CARD_WIDTH_EXPANDED
        },
        animationSpec = DockCardPhysics.ElasticDpSpec,
        label = "cardWidth"
    )

    // Animated card height (430dp contracted <-> 625dp expanded)
    val cardHeight by animateDpAsState(
        targetValue = if (controller.isExpanded) DockCardAnimations.CARD_HEIGHT_EXPANDED else DockCardAnimations.CARD_HEIGHT_CONTRACTED,
        animationSpec = DockCardPhysics.ElasticDpSpec,
        label = "cardHeight"
    )

    val cardShape = RoundedCornerShape(34.dp)
    val glassPreset = DeXGlassPresets.DockCardDark

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .skiaDropShadow(
                color = glassPreset.shadowColor,
                blurRadius = glassPreset.shadowRadius,
                borderRadius = 34.dp,
                offsetX = 0.dp,
                offsetY = 8.dp
            )
            .subpixelBorderGlow(
                strokeWidth = 1.dp,
                borderColor = DeXColors.Dark.Accent,
                glowColor = DeXColors.Dark.InsetGlowColor,
                cornerRadius = 34.dp
            )
            .clip(cardShape)
    ) {
        LiquidGlassPanel(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            shape = cardShape,
            config = glassPreset
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Drawer Panel (Animated Visibility with spring slide + smooth fade)
                AnimatedVisibility(
                    visible = controller.isExpanded,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = DockCardPhysics.ElasticIntOffsetSpec
                    ) + fadeIn(animationSpec = DockCardAnimations.SmoothEase),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = DockCardPhysics.ElasticIntOffsetSpec
                    ) + fadeOut(animationSpec = DockCardAnimations.SmoothEase),
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (controller.expandedPanel) {
                            ExpandedPanel.FileExplorer -> FileExplorerPanel(
                                controller = controller,
                                onClose = { controller.collapsePanel() }
                            )
                            ExpandedPanel.Settings -> SettingsPanel(
                                controller = controller,
                                onClose = { controller.collapsePanel() }
                            )
                            ExpandedPanel.Pairing -> PinPairingPanel(
                                pairingEngine = pairingEngine,
                                onClose = { controller.collapsePanel() }
                            )
                            else -> {}
                        }
                    }
                }

                // Right Column: Always-visible Main Menu Column (300dp)
                MainMenuColumn(
                    controller = controller,
                    onExpandFileExplorer = { controller.expandPanel(ExpandedPanel.FileExplorer) },
                    onExpandSettings = { controller.expandPanel(ExpandedPanel.Settings) },
                    onContract = { controller.collapsePanel() },
                    onPairDevice = { device ->
                        pairingEngine.initiatePairing(device)
                        controller.expandPanel(ExpandedPanel.Pairing)
                    },
                    onExitEngine = onExitEngine,
                    onDismiss = onDismiss,
                    modifier = Modifier.width(300.dp)
                )
            }
        }
    }
}
