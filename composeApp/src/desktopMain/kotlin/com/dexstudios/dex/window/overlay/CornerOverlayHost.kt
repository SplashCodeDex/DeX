package com.dexstudios.dex.window.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dexstudios.dex.core.designsystem.components.overlay.AlertDialog
import com.dexstudios.dex.core.designsystem.components.overlay.FluidNotificationStack
import com.dexstudios.dex.core.designsystem.components.overlay.MessageToast
import com.dexstudios.dex.core.designsystem.components.overlay.NotificationBanner
import com.dexstudios.dex.core.designsystem.components.overlay.StackItem
import com.dexstudios.dex.core.designsystem.components.overlay.StackedScreen
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import com.dexstudios.dex.overlay.AlertNotification
import com.dexstudios.dex.overlay.BannerNotification
import com.dexstudios.dex.overlay.OverlayManager
import com.dexstudios.dex.overlay.StackedScreenNotification
import com.dexstudios.dex.overlay.ToastNotification
import com.dexstudios.dex.platform.DesktopEnvironment
import com.dexstudios.dex.platform.TaskbarWorkAreaProvider
import com.dexstudios.dex.platform.toDpSpace
import kotlinx.coroutines.delay

/**
 * Unified Desktop Corner Overlay Host Window.
 *
 * Consolidates all overlay surfaces (Banners, Modal Alerts, Confirmations, Stacked Screens,
 * and Status Toasts) into a single AWT UTILITY window with:
 * - Adaptive Platform Placement: Bottom-Right on Windows, Top-Right on macOS
 * - Dual-Tier Grouping: Action Alerts (Tier 1) stacked above Status Toasts (Tier 2)
 * - Apple-grade fluid spring kinematics with 2 downward resting peek shelves and upward hover fan-out
 * - Zero click-blocking transparent canvas
 */
@Composable
fun CornerOverlayHost(overlayManager: OverlayManager) {
    val alerts by overlayManager.actionAlerts.collectAsState()
    val toasts by overlayManager.statusToasts.collectAsState()
    val isVisible = alerts.isNotEmpty() || toasts.isNotEmpty()

    // Anti-flash visibility delay for smooth exit animations
    var isWindowReallyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            isWindowReallyVisible = true
        } else {
            delay(250)
            isWindowReallyVisible = false
        }
    }

    val isMacOS = remember { DesktopEnvironment.isMacOS }
    val canvasWidth = 600.dp
    val canvasHeight = 900.dp

    // Calculate active monitor placement
    val density = LocalDensity.current.density
    val workArea = remember(density) { TaskbarWorkAreaProvider.getActiveScreenWorkArea() }
    val dpWorkArea = remember(workArea, density) { workArea.toDpSpace(density) }

    val cornerX = dpWorkArea.right - canvasWidth.value.toInt() - 16
    val cornerY = if (isMacOS) {
        dpWorkArea.top + 16
    } else {
        dpWorkArea.bottom - canvasHeight.value.toInt()
    }

    val windowState = rememberWindowState(
        position = WindowPosition(cornerX.dp, cornerY.dp),
        size = DpSize(canvasWidth, canvasHeight),
    )

    Window(
        onCloseRequest = { overlayManager.dismissAll() },
        visible = isWindowReallyVisible,
        state = windowState,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        title = "DeX Overlays",
    ) {
        LaunchedEffect(window) {
            try {
                if (!window.isDisplayable) {
                    window.type = java.awt.Window.Type.UTILITY
                }
                window.focusableWindowState = false
            } catch (_: Throwable) {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (isMacOS) 24.dp else 16.dp,
                    bottom = if (isMacOS) 16.dp else 24.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            contentAlignment = if (isMacOS) Alignment.TopEnd else Alignment.BottomEnd,
        ) {
            Column(
                modifier = Modifier.wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Tier 1: Action Alerts, Banners, Confirmations, StackedScreens
                if (alerts.isNotEmpty()) {
                    val alertStackItems = alerts.map { payload ->
                        StackItem(
                            id = payload.id,
                            data = payload,
                        ) { data, isTop, onDismiss ->
                            when (data) {
                                is BannerNotification -> {
                                    NotificationBanner(
                                        title = data.title,
                                        subtitle = data.subtitle,
                                        badgeText = data.badgeText,
                                        iconResource = data.iconResource,
                                        iconPainter = data.iconPainter,
                                        iconTint = data.iconTint,
                                        iconBackgroundColor = data.iconBackgroundColor ?: androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                        progress = data.progress,
                                        initialMorphState = data.initialMorphState,
                                        allowInteractiveMorph = data.allowInteractiveMorph,
                                        trailingPreview = data.trailingPreview,
                                        expandedContent = data.expandedContent,
                                        onActionClick = data.onActionClick,
                                        onDismiss = onDismiss,
                                        onHoverChanged = { hovered ->
                                            overlayManager.setAlertsHovered(hovered)
                                        },
                                    )
                                }

                                is AlertNotification -> {
                                    AlertDialog(
                                        title = data.title,
                                        message = data.message,
                                        iconResource = data.iconResource,
                                        iconPainter = data.iconPainter,
                                        iconTint = data.iconTint ?: androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        badgeResource = data.badgeResource,
                                        badgePainter = data.badgePainter,
                                        previewContent = data.previewContent,
                                        negativeButtonText = data.negativeButtonText,
                                        positiveButtonText = data.positiveButtonText,
                                        isPositiveActionLoading = data.isPositiveActionLoading,
                                        isNegativeActionLoading = data.isNegativeActionLoading,
                                        onNegativeAction = data.onNegativeAction,
                                        onPositiveAction = data.onPositiveAction,
                                        onDismiss = onDismiss,
                                        onHoverChanged = { hovered ->
                                            overlayManager.setAlertsHovered(hovered)
                                        },
                                    )
                                }

                                is StackedScreenNotification -> {
                                    StackedScreen(
                                        title = data.title,
                                        subtitle = data.subtitle,
                                        width = data.width ?: OverlayPhysics.STACKED_SCREEN_MAX_WIDTH,
                                        height = data.height ?: OverlayPhysics.STACKED_SCREEN_MAX_HEIGHT,
                                        showBackButton = data.showBackButton,
                                        trailingHeaderAction = data.trailingHeaderAction,
                                        onBack = data.onBack,
                                        onDismiss = onDismiss,
                                        onHoverChanged = { hovered ->
                                            overlayManager.setAlertsHovered(hovered)
                                        },
                                        content = data.content,
                                    )
                                }

                                else -> {}
                            }
                        }
                    }

                    FluidNotificationStack(
                        items = alertStackItems,
                        fanUpwards = !isMacOS,
                        alignRight = true,
                        maxVisibleCards = 3,
                        onDismissItem = { id -> overlayManager.dismiss(id, wasUserAction = true) },
                        onClearAll = { overlayManager.dismissAlerts() },
                        onStackHoverChanged = { hovered -> overlayManager.setAlertsHovered(hovered) },
                    )
                }

                // Tier 2: Status Toasts & Progress Pills
                if (toasts.isNotEmpty()) {
                    val toastStackItems = toasts.map { toast ->
                        StackItem(
                            id = toast.id,
                            data = toast,
                        ) { data, isTop, onDismiss ->
                            MessageToast(
                                message = data.message,
                                variant = data.variant,
                                iconResource = data.iconResource,
                                iconPainter = data.iconPainter,
                                actionText = data.actionText,
                                onActionClick = data.onActionClick,
                                showCloseButton = data.showCloseButton ?: (data.actionText == null),
                                progress = data.progress,
                                onDismiss = onDismiss,
                                onHoverChanged = { hovered ->
                                    overlayManager.setToastsHovered(hovered)
                                },
                                onClick = data.onClick,
                            )
                        }
                    }

                    FluidNotificationStack(
                        items = toastStackItems,
                        fanUpwards = !isMacOS,
                        alignRight = true,
                        maxVisibleCards = if (alerts.isNotEmpty()) 3 else 5,
                        onDismissItem = { id -> overlayManager.dismiss(id, wasUserAction = true) },
                        onClearAll = { overlayManager.dismissToasts() },
                        onStackHoverChanged = { hovered -> overlayManager.setToastsHovered(hovered) },
                    )
                }
            }
        }
    }
}
