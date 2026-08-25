package com.dexstudios.dex.window

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.core.designsystem.components.AmbientSmokeBackground
import com.dexstudios.dex.platform.DockCardMetrics
import com.dexstudios.dex.window.components.FileExplorerPanel
import com.dexstudios.dex.window.components.InboundPairingDialogOverlay
import com.dexstudios.dex.window.components.PinPairingPanel
import com.dexstudios.dex.window.components.SettingsPanel
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics

/**
 * Main Content Surface for the DeX Floating Dock Card.
 *
 * Responsibilities:
 * - Animates width (320dp contracted <-> 1054dp expanded / 675dp settings / 400dp pairing)
 * - Animates height (430dp contracted <-> 625dp expanded) with spring(0.65f, 300f)
 * - Container with RoundedCornerShape(34.dp), themed surface background, and Row layout
 * - Left animated drawer (FileExplorer, Settings, Pairing)
 * - Right 310dp MainMenuColumn
 */
@Composable
fun DockCardContent(controller: DockedWindowStateController, modifier: Modifier = Modifier, onDismiss: () -> Unit, onExitEngine: () -> Unit, pairingEngine: PairingEngine) {
    val pairingState by pairingEngine.state.collectAsState()

    LaunchedEffect(pairingState) {
        if (pairingState is com.dexstudios.dex.auth.PairingState.PinPhase || pairingState is com.dexstudios.dex.auth.PairingState.QrPhase) {
            controller.expandPanel(ExpandedPanel.Pairing)
        }
    }

    // Animated card width (matches exact WPF ElasticEase spring physics)
    val cardWidth by animateDpAsState(
        targetValue = when {
            !controller.isExpanded || controller.expandedPanel == ExpandedPanel.Pairing -> DockCardAnimations.CARD_WIDTH_CONTRACTED
            controller.expandedPanel == ExpandedPanel.Settings -> DockCardAnimations.SETTINGS_WIDTH_EXPANDED
            else -> DockCardAnimations.CARD_WIDTH_EXPANDED
        },
        animationSpec = DockCardPhysics.ElasticDpSpec,
        label = "cardWidth",
    )

    // Animated card height. WPF parity: the legacy window was fixed 1420x760 NoResize and
    // the PIN view slid in WITHOUT any height change, so the Pairing panel keeps the card
    // at its contracted height — only FileExplorer/Settings grow it.
    val cardHeight by animateDpAsState(
        targetValue = if (controller.isExpanded && controller.expandedPanel != ExpandedPanel.Pairing) {
            DockCardAnimations.CARD_HEIGHT_EXPANDED
        } else {
            DockCardAnimations.CARD_HEIGHT_CONTRACTED
        },
        animationSpec = DockCardPhysics.ElasticDpSpec,
        label = "cardHeight",
    )

    val cardShape = RoundedCornerShape(34.dp)

    val cardAlpha by animateFloatAsState(
        targetValue = if (controller.isVisible) 1f else 0f,
        animationSpec = if (controller.isVisible) DockCardAnimations.PopInAlphaSpec else DockCardAnimations.HideEase,
        label = "cardAlpha",
    )

    val isMacOS = com.dexstudios.dex.platform.DesktopEnvironment.isMacOS

    val cardTranslationY by animateDpAsState(
        targetValue = if (controller.isVisible) {
            0.dp
        } else if (isMacOS) {
            (-15).dp
        } else {
            15.dp
        },
        animationSpec = if (controller.isVisible) spring(dampingRatio = 0.65f, stiffness = 300f) else DockCardAnimations.HideEaseDp,
        label = "cardTranslationY",
    )

    // Uniform scale bounce from 0.85 to 1.0
    val cardScale by animateFloatAsState(
        targetValue = if (controller.isVisible) 1f else 0.85f,
        animationSpec = if (controller.isVisible) DockCardPhysics.PopInSpringSpec else DockCardAnimations.HideEase,
        label = "cardScale",
    )

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                transformOrigin = if (isMacOS) TransformOrigin(0.5f, 0f) else TransformOrigin(0.5f, 1f)
                alpha = cardAlpha
                translationY = cardTranslationY.toPx()
                scaleX = cardScale
                scaleY = cardScale
            }
            .border(
                width = 1.dp,
                // Hairline uses the outline role so the floating card keeps definition
                // over any wallpaper in both themes (surfaceVariant would vanish on the
                // light porcelain canvas).
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(34.dp),
            )
            .graphicsLayer {
                shape = cardShape
                clip = true
            }
            // Card face paints the `background` role — 1:1 with the Android
            // main screen canvas (#DAD9DD light / #111318 dark).
            .background(MaterialTheme.colorScheme.background, cardShape),
    ) {
        // Ambient purple/violet/pink haze behind all card content; the card's
        // rounded-corner graphicsLayer clip keeps it inside the face.
        AmbientSmokeBackground(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Drawer Panel (Animated Visibility with spring slide + smooth fade)
                AnimatedVisibility(
                    visible = controller.isExpanded && controller.expandedPanel != ExpandedPanel.Pairing,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = DockCardPhysics.ElasticIntOffsetSpec,
                    ) + fadeIn(animationSpec = DockCardAnimations.SmoothEase),
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = DockCardPhysics.ElasticIntOffsetSpec,
                    ) + fadeOut(animationSpec = DockCardAnimations.SmoothEase),
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (controller.expandedPanel) {
                            ExpandedPanel.FileExplorer -> FileExplorerPanel(
                                controller = controller,
                                onClose = { controller.collapsePanel() },
                            )

                            ExpandedPanel.Settings -> SettingsPanel(
                                controller = controller,
                                onClose = { controller.collapsePanel() },
                            )

                            else -> {}
                        }
                    }
                }

                // Right Column: Wrapper of the contracted card width to prevent layout jumps,
                // centering the main-menu content width
                AnimatedContent(
                    targetState = controller.expandedPanel == ExpandedPanel.Pairing,
                    transitionSpec = {
                        if (targetState) {
                            // Slide PinPairingPanel in from right, MainMenuColumn out to left
                            (
                                slideInHorizontally(initialOffsetX = { DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                    fadeIn(DockCardAnimations.PanelSlideSpec)
                                ) togetherWith
                                (
                                    slideOutHorizontally(targetOffsetX = { -DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                        fadeOut(DockCardAnimations.PanelSlideSpec)
                                    )
                        } else {
                            // Slide MainMenuColumn in from left, PinPairingPanel out to right
                            (
                                slideInHorizontally(initialOffsetX = { -DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                    fadeIn(DockCardAnimations.PanelSlideSpec)
                                ) togetherWith
                                (
                                    slideOutHorizontally(targetOffsetX = { DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                        fadeOut(DockCardAnimations.PanelSlideSpec)
                                    )
                        }
                    },
                    modifier = Modifier.width(DockCardMetrics.CARD_WIDTH_CONTRACTED.dp),
                    contentAlignment = Alignment.Center,
                    label = "PairingSlide",
                ) { isPairing ->
                    if (isPairing) {
                        PinPairingPanel(
                            pairingEngine = pairingEngine,
                            onClose = { controller.collapsePanel() },
                            modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                        )
                    } else {
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
                            modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        InboundPairingDialogOverlay()
    }
}
