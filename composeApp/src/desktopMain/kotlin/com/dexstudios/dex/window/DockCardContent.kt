package com.dexstudios.dex.window

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.core.designsystem.components.AmbientSmokeBackground
import com.dexstudios.dex.core.designsystem.components.AmbientSmokeMood
import com.dexstudios.dex.core.designsystem.components.glass.frostedSurface
import com.dexstudios.dex.core.designsystem.components.glass.verticalFadingEdge
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.platform.DockCardMetrics
import com.dexstudios.dex.window.components.DeviceStatusPanel
import com.dexstudios.dex.window.components.ExternalDragState
import com.dexstudios.dex.window.components.FileExplorerPanel
import com.dexstudios.dex.window.components.InboundPairingDialogOverlay
import com.dexstudios.dex.window.components.LocalExternalDragState
import com.dexstudios.dex.window.components.PinPairingPanel
import com.dexstudios.dex.window.components.SettingsPanel
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.DataFlavor

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
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DockCardContent(controller: DockedWindowStateController, modifier: Modifier = Modifier, onDismiss: () -> Unit, onExitEngine: () -> Unit, pairingEngine: PairingEngine) {
    val pairingState by pairingEngine.state.collectAsState()

    // Session lifecycle mirroring the legacy panel timers: active phases surface the
    // pairing view AND hold the window against focus-loss auto-hide (WPF kept the window
    // while any pairing session was live); Success/Error hold ~800ms so the success flash /
    // red shake read before the slide-out, then close and clear exactly like the WPF
    // succTimer / errTimer did.
    LaunchedEffect(pairingState) {
        when (val state = pairingState) {
            is com.dexstudios.dex.auth.PairingState.QrPhase, is com.dexstudios.dex.auth.PairingState.PinPhase -> {
                controller.isPairingActive = true
                // Legacy WPF parity: a phone-initiated pair-request SURFACED the hidden
                // window from the tray so the PIN was readable immediately (the focus-loss
                // guard alone kept it visible, never brought it back). No-op when already shown.
                if (!controller.isVisible) {
                    controller.show()
                }
                controller.expandPanel(ExpandedPanel.Pairing)
            }

            is com.dexstudios.dex.auth.PairingState.Success -> {
                kotlinx.coroutines.delay(1200)
                controller.isPairingActive = false
                controller.expandPanel(ExpandedPanel.DeviceStatus)
                pairingEngine.reset()
            }

            is com.dexstudios.dex.auth.PairingState.Error -> {
                kotlinx.coroutines.delay(800)
                controller.collapsePanel()
                pairingEngine.reset()
            }

            com.dexstudios.dex.auth.PairingState.Idle -> controller.isPairingActive = false
        }
    }

    // Auto-surface device connected status screen when a device connects (when not in an active pairing flow)
    LaunchedEffect(Unit) {
        com.dexstudios.dex.core.network.server.WebSocketConnectionManager.events.collect { event ->
            if (event is com.dexstudios.dex.core.network.server.ConnectionEvent.Connected) {
                if (!controller.isPairingActive && pairingEngine.state.value == com.dexstudios.dex.auth.PairingState.Idle) {
                    if (!controller.isVisible) {
                        controller.show()
                    }
                    controller.expandPanel(ExpandedPanel.DeviceStatus)
                }
            }
        }
    }

    // Animated card width (matches exact WPF ElasticEase spring physics)
    val cardWidth by animateDpAsState(
        targetValue = when {
            !controller.isExpanded || controller.expandedPanel?.isContractedHeight == true -> DockCardAnimations.CARD_WIDTH_CONTRACTED
            controller.expandedPanel == ExpandedPanel.Settings -> DockCardAnimations.SETTINGS_WIDTH_EXPANDED
            else -> DockCardAnimations.CARD_WIDTH_EXPANDED
        },
        animationSpec = DockCardPhysics.ElasticDpSpec,
        label = "cardWidth",
    )

    // Animated card height. WPF parity: the legacy window was fixed 1420x760 NoResize and
    // the PIN and DeviceStatus views slide in WITHOUT any height change, so they keep the card
    // at its contracted height — only FileExplorer/Settings grow it.
    val cardHeight by animateDpAsState(
        targetValue = if (controller.isExpanded && controller.expandedPanel?.isContractedHeight != true) {
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

    val externalDragState = remember { ExternalDragState() }

    val dragTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                externalDragState.isExternalDragActive = true
            }

            override fun onEntered(event: DragAndDropEvent) {
                externalDragState.isExternalDragActive = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                externalDragState.isExternalDragActive = false
                externalDragState.isDeviceSectionHovered = false
            }

            override fun onExited(event: DragAndDropEvent) {
                // Drag left window
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                externalDragState.isExternalDragActive = false
                externalDragState.isDeviceSectionHovered = false
                return false
            }
        }
    }

    CompositionLocalProvider(LocalExternalDragState provides externalDragState) {
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
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(34.dp),
                )
                .graphicsLayer {
                    shape = cardShape
                    clip = true
                }
                .background(
                    color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                        lerp(MaterialTheme.colorScheme.background, Color.Black, 0.06f)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    shape = cardShape,
                )
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    },
                    target = dragTarget,
                ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
                AmbientSmokeBackground(
                    modifier = Modifier.fillMaxSize(),
                    mood = when (controller.expandedPanel) {
                        ExpandedPanel.FileExplorer -> AmbientSmokeMood.Explorer

                        ExpandedPanel.Settings -> AmbientSmokeMood.Settings

                        ExpandedPanel.Pairing,
                        ExpandedPanel.DeviceStatus,
                        ExpandedPanel.DeviceStatusTablet,
                        ExpandedPanel.DeviceStatusWatch,
                        ExpandedPanel.DeviceStatusLaptop,
                        -> AmbientSmokeMood.Pairing

                        null -> AmbientSmokeMood.Resting
                    },
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val isHistoryExpanded = controller.isExpanded && controller.expandedPanel == ExpandedPanel.FileExplorer
                    val isBlurActive = externalDragState.isExternalDragActive && isHistoryExpanded
                    val blurRadius by animateDpAsState(
                        targetValue = if (isBlurActive) 20.dp else 0.dp,
                        animationSpec = tween(300),
                        label = "historyBlurRadius",
                    )

                    // Left Drawer Panel (Clean stationary fade in sync with card spring expansion)
                    AnimatedVisibility(
                        visible = controller.isExpanded && !controller.expandedPanel!!.isContractedHeight,
                        enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            AnimatedContent(
                                targetState = controller.expandedPanel,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) togetherWith
                                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                                },
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier.fillMaxSize(),
                                label = "LeftDrawerPanelContent",
                            ) { panel ->
                                when (panel) {
                                    ExpandedPanel.FileExplorer -> {
                                        Box(
                                            modifier = Modifier
                                                .width((DockCardMetrics.FILE_EXPLORER_WIDTH_EXPANDED - DockCardMetrics.CARD_WIDTH_CONTRACTED).dp)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(
                                                        if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier,
                                                    ),
                                            ) {
                                                FileExplorerPanel(
                                                    controller = controller,
                                                    onClose = { controller.collapsePanel() },
                                                )
                                            }

                                            // Frosted Glass Blur Dim & "Drop on a device to send" center badge
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isBlurActive,
                                                enter = fadeIn(tween(250)),
                                                exit = fadeOut(tween(200)),
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(24.dp))
                                                        .background(
                                                            if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                                                                Color.White.copy(alpha = 0.45f)
                                                            } else {
                                                                Color.Black.copy(alpha = 0.45f)
                                                            },
                                                        )
                                                        .verticalFadingEdge(topFadeHeight = 24.dp, bottomFadeHeight = 24.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .frostedSurface(
                                                                shape = RoundedCornerShape(18.dp),
                                                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                                                opacity = 0.95f,
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                                shape = RoundedCornerShape(18.dp),
                                                            )
                                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(Res.drawable.ic_fluent_smartphone),
                                                            contentDescription = "Drop on device",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(36.dp),
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Drop on a device to send",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Drag files to the device column on the right",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    ExpandedPanel.Settings -> {
                                        Box(
                                            modifier = Modifier
                                                .width((DockCardMetrics.SETTINGS_WIDTH_EXPANDED - DockCardMetrics.CARD_WIDTH_CONTRACTED).dp)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            SettingsPanel(
                                                controller = controller,
                                                onClose = { controller.collapsePanel() },
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }

                    // Right Column: Wrapper of the contracted card width to prevent layout jumps,
                    // centering the main-menu content width.
                    // Only sub-panels that replace the right column (Pairing, DeviceStatus) trigger the slide.
                    // Expanding/contracting History or Settings keeps MainMenuColumn steady on the right.
                    val rightColumnPanel = if (controller.expandedPanel?.isContractedHeight == true) {
                        controller.expandedPanel
                    } else {
                        null
                    }

                    AnimatedContent(
                        targetState = rightColumnPanel,
                        transitionSpec = {
                            if (targetState != null) {
                                // Slide sub-panel in from right, MainMenuColumn out to left
                                (
                                    slideInHorizontally(initialOffsetX = { DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                        fadeIn(DockCardAnimations.PanelSlideSpec)
                                    ) togetherWith
                                    (
                                        slideOutHorizontally(targetOffsetX = { -DockCardMetrics.MAIN_MENU_WIDTH }, animationSpec = DockCardAnimations.PanelSlideOffsetSpec) +
                                            fadeOut(DockCardAnimations.PanelSlideSpec)
                                        )
                            } else {
                                // Slide MainMenuColumn in from left, sub-panel out to right
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
                        label = "RightColumnSlide",
                    ) { panel ->
                        when (panel) {
                            ExpandedPanel.Pairing -> {
                                PinPairingPanel(
                                    pairingEngine = pairingEngine,
                                    onClose = { controller.collapsePanel() },
                                    modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                                )
                            }

                            ExpandedPanel.DeviceStatus -> {
                                DeviceStatusPanel(
                                    controller = controller,
                                    onClose = { controller.collapsePanel() },
                                    onBrowseFiles = {
                                        controller.expandPanel(ExpandedPanel.FileExplorer)
                                    },
                                    modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                                )
                            }

                            ExpandedPanel.DeviceStatusTablet -> {
                                DeviceStatusPanel(
                                    controller = controller,
                                    onClose = { controller.collapsePanel() },
                                    overrideCategory = com.dexstudios.dex.window.components.ConnectedDeviceType.Tablet,
                                    onBrowseFiles = {
                                        controller.expandPanel(ExpandedPanel.FileExplorer)
                                    },
                                    modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                                )
                            }

                            ExpandedPanel.DeviceStatusWatch -> {
                                DeviceStatusPanel(
                                    controller = controller,
                                    onClose = { controller.collapsePanel() },
                                    overrideCategory = com.dexstudios.dex.window.components.ConnectedDeviceType.Watch,
                                    onBrowseFiles = {
                                        controller.expandPanel(ExpandedPanel.FileExplorer)
                                    },
                                    modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                                )
                            }

                            ExpandedPanel.DeviceStatusLaptop -> {
                                DeviceStatusPanel(
                                    controller = controller,
                                    onClose = { controller.collapsePanel() },
                                    overrideCategory = com.dexstudios.dex.window.components.ConnectedDeviceType.Laptop,
                                    onBrowseFiles = {
                                        controller.expandPanel(ExpandedPanel.FileExplorer)
                                    },
                                    modifier = Modifier.width(DockCardMetrics.MAIN_MENU_WIDTH.dp).fillMaxHeight(),
                                )
                            }

                            else -> {
                                MainMenuColumn(
                                    controller = controller,
                                    onExpandFileExplorer = { controller.expandPanel(ExpandedPanel.FileExplorer) },
                                    onExpandSettings = { controller.expandPanel(ExpandedPanel.Settings) },
                                    onContract = { controller.collapsePanel() },
                                    onPairDevice = { device ->
                                        val active = pairingEngine.state.value
                                        val activeFingerprint = (active as? com.dexstudios.dex.auth.PairingState.QrPhase)?.fingerprint
                                            ?: (active as? com.dexstudios.dex.auth.PairingState.PinPhase)?.fingerprint
                                        val isSameSession =
                                            activeFingerprint != null &&
                                                activeFingerprint == device.info.fingerprint &&
                                                controller.expandedPanel == ExpandedPanel.Pairing
                                        if (!isSameSession) {
                                            if (active !is com.dexstudios.dex.auth.PairingState.Idle) {
                                                pairingEngine.reset()
                                            }
                                            pairingEngine.initiatePairing(device)
                                        }
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
            }

            InboundPairingDialogOverlay()
        }
    }
}
