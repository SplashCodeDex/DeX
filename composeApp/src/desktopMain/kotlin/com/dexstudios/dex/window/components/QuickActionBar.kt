package com.dexstudios.dex.window.components
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPresets
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_clipboard
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_history
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_notifications
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.icons.AnimatedClipboardIcon
import com.dexstudios.dex.core.designsystem.icons.AnimatedDndBell
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.HighlightStyle
import org.jetbrains.compose.resources.painterResource

/**
 * Centered row of 4 primary liquid-glass pill buttons + 1 dynamic Danger Close pill.
 *
 * Micro-interactions:
 * - Hover scale 1.08x / translateY -3dp (300ms HoverEase)
 * - Press blur softening inside the glass (spring 0.6 / 600); no refraction
 * - State fill drawn over the glass: checked = primary, danger hover = error,
 *   idle hover = soft ink wash (200ms morphs)
 * - Contrast-inverted badge counter for notifications/sync items
 * - Collapsible Danger Close button (0 to 62dp when expanded)
 */
@Composable
fun QuickActionBar(
    cardBackdrop: Backdrop,
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Do Not Disturb Pill with Animated Lottie Bell
        DeXQuickActionButton(
            cardBackdrop = cardBackdrop,
            tooltip = "Do Not Disturb",
            isChecked = isDndActive,
            onClick = onToggleDnd,
            iconContent = { tint ->
                AnimatedDndBell(
                    isDndActive = isDndActive,
                    size = 22.dp,
                    tint = tint,
                    contentDescription = "Do Not Disturb",
                )
            },
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 2. Screen Mirror Pill
        DeXQuickActionButton(
            cardBackdrop = cardBackdrop,
            icon = painterResource(Res.drawable.ic_fluent_smartphone),
            tooltip = "Mirror Phone",
            isChecked = isMirroringActive,
            onClick = onToggleMirror,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 3. History Pill (opens FileExplorerPanel in History mode)
        DeXQuickActionButton(
            cardBackdrop = cardBackdrop,
            icon = painterResource(Res.drawable.ic_fluent_history),
            tooltip = "History",
            isChecked = isTransfersActive,
            onClick = onToggleTransfers,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 4. Clipboard Sync Pill
        DeXQuickActionButton(
            cardBackdrop = cardBackdrop,
            tooltip = "Clipboard",
            isChecked = isClipboardActive,
            badgeCount = clipboardBadgeCount,
            onClick = onToggleClipboard,
            iconContent = { tint ->
                AnimatedClipboardIcon(
                    isClipboardActive = isClipboardActive,
                    size = 22.dp,
                    tint = tint,
                    contentDescription = "Clipboard",
                )
            },
        )

        // 5. Dynamic Collapsible Danger Close Pill (0dp <-> 62dp)
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter =
            expandHorizontally(
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS),
            ) + fadeIn(),
            exit =
            shrinkHorizontally(animationSpec = androidx.compose.animation.core.spring(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS)) +
                fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                DeXQuickActionButton(
                    cardBackdrop = cardBackdrop,
                    icon = painterResource(Res.drawable.ic_fluent_close),
                    tooltip = "Close",
                    isChecked = false,
                    isDanger = true,
                    onClick = onCloseExpandedPanel,
                )
            }
        }
    }
}

@Composable
fun DeXQuickActionButton(
    cardBackdrop: Backdrop,
    icon: Painter,
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    badgeCount: Int = 0,
) {
    DeXQuickActionButton(
        cardBackdrop = cardBackdrop,
        tooltip = tooltip,
        isChecked = isChecked,
        onClick = onClick,
        modifier = modifier,
        isDanger = isDanger,
        badgeCount = badgeCount,
        iconContent = { tint ->
            Icon(
                painter = icon,
                contentDescription = tooltip,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        },
    )
}

@Composable
fun DeXQuickActionButton(
    cardBackdrop: Backdrop,
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    badgeCount: Int = 0,
    iconContent: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile Scale: 1.0 -> 1.08 (hover)
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
        label = "btnScale",
    )

    // Tactile Translation: 0 -> -3dp (lift)
    val translateY by animateDpAsState(
        targetValue = if (isHovered) (-3).dp else 0.dp,
        animationSpec = tween(500, easing = DockCardPhysics.HoverEase),
        label = "btnTransY",
    )

    // State fill drawn OVER the glass surface, under the icon:
    // checked -> primary wash, danger hover -> error, idle hover -> soft ink wash.
    val surfaceOverlayColor by animateColorAsState(
        targetValue = when {
            isDanger && (isHovered || isPressed) -> androidx.compose.material3.MaterialTheme.colorScheme.error
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.primary
            isHovered -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "btnStateOverlay",
    )

    // Icon Color Morphing
    val iconColor by animateColorAsState(
        targetValue = when {
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            isDanger && (isHovered || isPressed) -> androidx.compose.material3.MaterialTheme.colorScheme.onError
            else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "btnIconColor",
    )

    // Exact Android SearchButton glass (LiquidGlassPresets.SearchIconButton),
    // with one desktop-density compensation: at ~1x display density the glare
    // stroke ceils to a 2px rim, fattening the mirrored bottom-left bounce
    // lobe. Raising the falloff pinches both arcs into thin crisp lines while
    // the top-right lobe keeps full brightness. Shape squared to the pill —
    // geometry (62x48dp, 22dp corners) stays desktop.
    val searchGlass = LiquidGlassPresets.SearchIconButton
    val glassConfig = searchGlass.copy(
        shape = RoundedCornerShape(22.dp),
        highlight = searchGlass.highlight.copy(
            style = (searchGlass.highlight.style as HighlightStyle.Default).copy(falloff = 5f),
        ),
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationY = translateY.toPx()
        },
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassIconButton(
            onClick = onClick,
            width = 62.dp,
            height = 48.dp,
            config = glassConfig,
            backdrop = cardBackdrop,
            surfaceOverlay = surfaceOverlayColor,
            interactionSource = interactionSource,
        ) {
            iconContent(iconColor)

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
                            if (badgeBorder != null) {
                                Modifier.border(badgeBorder, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            },
                        )
                        .background(badgeBgColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
