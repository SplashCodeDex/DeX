package com.dexstudios.dex.window.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButton
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButtonDefaults
import com.dexstudios.dex.core.designsystem.components.buttons.DeXCloseButtonSize
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.window.DockedWindowStateController

/**
 * TopActionsPanel:
 * 1. Top row: DragPillHandle (draggable handle, pin button, double-click reset)
 * 2. Middle row: Tactile QuickActionBar (62x48dp pills + collapsible danger close)
 */
@Composable
fun TopActionsPanel(
    controller: DockedWindowStateController,
    isDndActive: Boolean,
    onToggleDnd: () -> Unit,
    isMirroringActive: Boolean,
    onToggleMirror: () -> Unit,
    isTransfersActive: Boolean,
    onToggleTransfers: () -> Unit,
    isClipboardActive: Boolean,
    onToggleClipboard: () -> Unit,
    clipboardBadgeCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Drag Pill & Pin Handle Row with Top-Right Close Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            DragPillHandle(
                controller = controller,
                modifier = Modifier.fillMaxWidth(),
            )

            // Centralized DeXCloseButton placed at top-right (red circle) when panel is expanded
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.isExpanded,
                enter = fadeIn(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = DynamicMotionConfig.Default.expandDampingRatio,
                        stiffness = DynamicMotionConfig.Default.stiffness,
                    ),
                ) + scaleIn(
                    initialScale = 0.80f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = DynamicMotionConfig.Default.expandDampingRatio,
                        stiffness = DynamicMotionConfig.Default.stiffness,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = DynamicMotionConfig.Default.collapseDampingRatio,
                        stiffness = DynamicMotionConfig.Default.stiffness,
                    ),
                ) + scaleOut(
                    targetScale = 0.80f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = DynamicMotionConfig.Default.collapseDampingRatio,
                        stiffness = DynamicMotionConfig.Default.stiffness,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            ) {
                DeXCloseButton(
                    size = DeXCloseButtonSize.Small,
                    colors = DeXCloseButtonDefaults.subtleColors(),
                    contentDescription = "Close Panel",
                    onClick = { controller.collapsePanel() },
                )
            }
        }

        // 2. Tactile Quick Actions Row (56x44dp Pills + Dynamic Danger Close)
        Box(
            modifier = Modifier.padding(bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            QuickActionBar(
                isDndActive = isDndActive,
                isMirroringActive = isMirroringActive,
                isTransfersActive = isTransfersActive,
                isClipboardActive = isClipboardActive,
                clipboardBadgeCount = clipboardBadgeCount,
                isPanelExpanded = controller.isExpanded,
                onToggleDnd = onToggleDnd,
                onToggleMirror = onToggleMirror,
                onToggleTransfers = onToggleTransfers,
                onToggleClipboard = onToggleClipboard,
                onCloseExpandedPanel = { controller.collapsePanel() },
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
