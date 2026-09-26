package com.dexstudios.dex.window.components
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.island.DynamicContentBlurConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.core.designsystem.components.island.transientContentBlur
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_history
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.icons.AnimatedClipboardIcon
import com.dexstudios.dex.core.designsystem.icons.AnimatedDndBell
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import org.jetbrains.compose.resources.painterResource

/**
 * Centered row of 4 flat 62x48dp pill buttons (DND, Mirror, Transfers, Clipboard).
 *
 * Flat surface treatment (no liquid glass): state-morphing background
 * (checked = primary, danger hover = error, idle = surfaceVariant), soft ink
 * hover wash, contrast-inverted badge counter, bubbleFluidity press feel and
 * the hover scale 1.08x / translateY -3dp lift (500ms HoverEase).
 */
@Composable
fun QuickActionBar(
    isDndActive: Boolean,
    isMirroringActive: Boolean,
    isTransfersActive: Boolean,
    isClipboardActive: Boolean,
    clipboardBadgeCount: Int = 0,
    isPanelExpanded: Boolean = false,
    onToggleDnd: () -> Unit = {},
    onToggleMirror: () -> Unit = {},
    onToggleTransfers: () -> Unit = {},
    onToggleClipboard: () -> Unit = {},
    onCloseExpandedPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Do Not Disturb Pill with Animated Lottie Bell
        DeXQuickActionButton(
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

        val isMirrorPaused = com.dexstudios.dex.core.network.PausedFeatures.isPaused(
            com.dexstudios.dex.core.network.PausedFeatures.SCREEN_MIRROR,
        )

        // 2. Screen Mirror Pill (Muted while feature is paused)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_smartphone),
            tooltip = if (isMirrorPaused) "Screen Mirroring (Paused)" else "Mirror Phone",
            isChecked = isMirroringActive && !isMirrorPaused,
            enabled = !isMirrorPaused,
            onClick = {
                if (!isMirrorPaused) onToggleMirror()
            },
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 3. History Pill (opens FileExplorerPanel in History mode)
        DeXQuickActionButton(
            icon = painterResource(Res.drawable.ic_fluent_history),
            tooltip = "History",
            isChecked = isTransfersActive,
            onClick = onToggleTransfers,
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))

        // 4. Clipboard Sync Pill
        DeXQuickActionButton(
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
    }
}

@Composable
fun DeXQuickActionButton(icon: Painter, tooltip: String, isChecked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, badgeCount: Int = 0, enabled: Boolean = true) {
    DeXQuickActionButton(
        tooltip = tooltip,
        isChecked = isChecked,
        onClick = onClick,
        modifier = modifier,
        badgeCount = badgeCount,
        enabled = enabled,
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeXQuickActionButton(
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    enabled: Boolean = true,
    iconContent: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHoveredRaw by interactionSource.collectIsHoveredAsState()
    val isPressedRaw by interactionSource.collectIsPressedAsState()
    var isFluidityPressed by remember { mutableStateOf(false) }
    val isHovered = isHoveredRaw && enabled
    val isPressed = (isPressedRaw || isFluidityPressed) && enabled
    val motionConfig = DynamicMotionConfig.Default
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = motionConfig.springSpec(isPressed),
        label = "btnPressProgress",
    )
    val shadowElevation = (4.dp * (1f - 0.20f * pressProgress)).coerceAtLeast(0.dp)

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

    // State Morphing Background Color — semi-transparent for glassmorphism;
    // the ambient smoke haze bleeds through, creating a frosted look without blur.
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.primary
            else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "btnBgColor",
    )

    val hoverOverlayColor by animateColorAsState(
        targetValue = if (isHovered && !isChecked) androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(200),
        label = "btnHoverOverlay",
    )

    // Icon Color Morphing
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            isChecked -> androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
            else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "btnIconColor",
    )

    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = tooltip,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        delayMillis = 400,
    ) {
        Box(
            modifier = modifier
                .zIndex(if (isHovered || isPressed) 1f else 0f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationY = translateY.toPx()
                }
                .size(width = 62.dp, height = 48.dp)
                .then(
                    if (enabled) {
                        Modifier.bubbleFluidity(
                            config = DynamicFluidityConfig.Default,
                            onPressedChanged = { isFluidityPressed = it },
                        )
                    } else {
                        Modifier.alpha(0.6f)
                    },
                )
                .shadow(
                    elevation = if (enabled) shadowElevation else 0.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.2f),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                )
                .clip(CircleShape)
                .background(backgroundColor, CircleShape)
                .background(hoverOverlayColor, CircleShape)
                .then(
                    if (enabled) {
                        Modifier.shinyGlare(
                            shape = CircleShape,
                            intensity = DefaultGlareIntensity * (1f + 0.60f * pressProgress),
                        )
                    } else {
                        Modifier
                    },
                )
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.transientContentBlur(
                    trigger = isChecked,
                    config = DynamicContentBlurConfig.Default,
                    motion = DynamicMotionConfig.Default,
                ),
                contentAlignment = Alignment.Center,
            ) {
                iconContent(iconColor)
            }

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
