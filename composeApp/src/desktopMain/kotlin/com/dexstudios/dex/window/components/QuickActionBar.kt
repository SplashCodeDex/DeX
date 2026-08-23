package com.dexstudios.dex.window.components
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_notifications
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_clipboard
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close

import com.dexstudios.dex.core.designsystem.generated.resources.Res

import org.jetbrains.compose.resources.painterResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.kinematics.DockCardPhysics

/**
 * Centered row of 4 primary 56x44dp pill buttons + 1 dynamic Danger Close pill.
 *
 * Micro-interactions:
 * - Hover scale 1.08x / translateY -3dp (300ms HoverEase)
 * - Press scale 0.85x / translateY +3dp (100ms FastOutSlowInEasing)
 * - Emerald state morphing (#0AE66D active background with #000000 icon)
 * - Contrast-inverted badge counter for notifications/sync items
 * - Collapsible Danger Close button (0 to 56dp when expanded)
 */
@Composable
fun QuickActionBar(
    isDndActive: Boolean,
    isMirroringActive: Boolean,
    isTransfersActive: Boolean,
    isClipboardActive: Boolean,
    clipboardBadgeCount: Int = 0,
    isPanelExpanded: Boolean,
    onToggleDnd: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleTransfers: () -> Unit,
    onToggleClipboard: () -> Unit,
    onCloseExpandedPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Do Not Disturb Pill (56x44dp)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_notifications),
            tooltip = "Do Not Disturb",
            isChecked = isDndActive,
            onClick = onToggleDnd
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 2. Screen Mirror Pill (56x44dp)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_smartphone),
            tooltip = "Mirror Phone",
            isChecked = isMirroringActive,
            onClick = onToggleMirror
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 3. Transfers / File Explorer Pill (56x44dp)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_folder),
            tooltip = "Transfers",
            isChecked = isTransfersActive,
            onClick = onToggleTransfers
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 4. Clipboard Sync Pill (56x44dp)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_clipboard),
            tooltip = "Clipboard",
            isChecked = isClipboardActive,
            badgeCount = clipboardBadgeCount,
            onClick = onToggleClipboard
        )

        // 5. Dynamic Collapsible Danger Close Pill (0dp <-> 56dp)
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter = expandHorizontally(animationSpec = androidx.compose.animation.core.spring(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS)) + fadeIn(),
            exit = shrinkHorizontally(animationSpec = androidx.compose.animation.core.spring(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS)) + fadeOut()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                DeXQuickActionButton(
                    icon = painterResource(Res.drawable.ic_fluent_close),
                    tooltip = "Close",
                    isChecked = false,
                    isDanger = true,
                    onClick = onCloseExpandedPanel
                )
            }
        }
    }
}

@Composable
fun DeXQuickActionButton(
    icon: Painter,
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    badgeCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    // Tactile Scale: 1.0 -> 1.08 (hover)
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
        label = "btnScale"
    )

    // Tactile Translation: 0 -> -3dp (lift)
    val translateY by animateDpAsState(
        targetValue = if (isHovered) (-3).dp else 0.dp,
        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
        label = "btnTransY"
    )

    // Emerald State Morphing Background Color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDanger && (isHovered || isPressed) -> androidx.compose.material3.MaterialTheme.colorScheme.error
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.primary
            else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "btnBgColor"
    )

    val hoverOverlayColor by animateColorAsState(
        targetValue = if (isHovered && !isChecked && !isDanger) androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(200),
        label = "btnHoverOverlay"
    )

    // Icon Color Morphing
    val iconColor by animateColorAsState(
        targetValue = when {
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            isDanger && (isHovered || isPressed) -> androidx.compose.material3.MaterialTheme.colorScheme.onError
            else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "btnIconColor"
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.translationY = translateY.toPx()
            }
            .bubbleFluidity()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .background(hoverOverlayColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = tooltip,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        if (badgeCount > 0) {
            // Contrast Inversion for Badge Counter
            val badgeBgColor = if (isChecked) androidx.compose.material3.MaterialTheme.colorScheme.surface else androidx.compose.material3.MaterialTheme.colorScheme.primary
            val badgeTextColor = if (isChecked) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            val badgeBorder = if (isChecked) BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary) else null

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .then(
                        if (badgeBorder != null) Modifier.border(badgeBorder, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .background(badgeBgColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = badgeTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
