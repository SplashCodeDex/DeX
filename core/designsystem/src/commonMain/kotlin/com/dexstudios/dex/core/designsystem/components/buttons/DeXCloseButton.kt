package com.dexstudios.dex.core.designsystem.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import org.jetbrains.compose.resources.painterResource

/**
 * Standardized sizing presets for [DeXCloseButton].
 */
enum class DeXCloseButtonSize(val buttonSize: Dp, val iconSize: Dp) {
    Micro(buttonSize = 18.dp, iconSize = 10.dp),
    Small(buttonSize = 24.dp, iconSize = 12.dp),
    Medium(buttonSize = 28.dp, iconSize = 14.dp),
    Large(buttonSize = 32.dp, iconSize = 16.dp),
    ExtraLarge(buttonSize = 40.dp, iconSize = 20.dp),
}

/**
 * Color configuration for [DeXCloseButton].
 */
@Immutable
data class DeXCloseButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val hoverContainerColor: Color = containerColor,
    val disabledContainerColor: Color = containerColor.copy(alpha = 0.04f),
    val disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
)

/**
 * Design defaults and preset factories for [DeXCloseButton].
 */
object DeXCloseButtonDefaults {
    val DefaultSize = DeXCloseButtonSize.Medium

    /**
     * Tactile bubble fluidity physics tuned for dismiss and cancel touch targets.
     */
    val Fluidity = DynamicFluidityConfig(
        enabled = true,
        pressScale = 0.88f,
        pullFactor = 0.08f,
        elasticity = 0.12f,
        scalePressSpeed = 350f,
        scalePressDamping = 0.40f,
        scaleSettleSpeed = 380f,
    )

    /**
     * Subdued pill/circle colors adapting to light/dark themes.
     */
    @Composable
    fun subtleColors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        hoverContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ): DeXCloseButtonColors = DeXCloseButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        hoverContainerColor = hoverContainerColor,
    )

    /**
     * Translucent ghost colors for cards, headers, and toolbars (transparent at rest, tinted on hover).
     */
    @Composable
    fun ghostColors(contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, hoverContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant): DeXCloseButtonColors =
        DeXCloseButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            hoverContainerColor = hoverContainerColor,
        )

    /**
     * High-contrast glass colors for dark overlays, media previews, and modal sheets.
     */
    @Composable
    fun glassColors(containerColor: Color = Color.White.copy(alpha = 0.14f), contentColor: Color = Color.White, hoverContainerColor: Color = Color.White.copy(alpha = 0.22f)): DeXCloseButtonColors =
        DeXCloseButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            hoverContainerColor = hoverContainerColor,
        )

    /**
     * Error/danger colors for destructive dismiss or transfer cancel actions.
     */
    @Composable
    fun dangerColors(
        containerColor: Color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        contentColor: Color = MaterialTheme.colorScheme.error,
        hoverContainerColor: Color = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
    ): DeXCloseButtonColors = DeXCloseButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        hoverContainerColor = hoverContainerColor,
    )
}

/**
 * Dedicated, centralized Close / Dismiss ('X') button.
 *
 * Implements:
 * 1. Standardized circular geometry across 5 explicit size variants ([DeXCloseButtonSize]).
 * 2. Organic [bubbleFluidity] anisotropic press squish and viscoelastic snap-back.
 * 3. Optical specular glare flaring on hover and press ([shinyGlare]).
 * 4. Hand hover cursor ([PointerIcon.Hand]) and accessible semantics.
 * 5. Zero liquid-glass / backdrop overhead, safely usable anywhere in the application.
 */
@Composable
fun DeXCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DeXCloseButtonSize = DeXCloseButtonDefaults.DefaultSize,
    colors: DeXCloseButtonColors = DeXCloseButtonDefaults.ghostColors(),
    enabled: Boolean = true,
    contentDescription: String = "Close",
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressedRaw by interactionSource.collectIsPressedAsState()
    var isFluidityPressed by remember { mutableStateOf(false) }
    val isPressed = (isPressedRaw || isFluidityPressed) && enabled

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = DynamicMotionConfig.Default.springSpec(isPressed),
        label = "closeButtonPressProgress",
    )

    val currentContainerColor = when {
        !enabled -> colors.disabledContainerColor
        isHovered || isPressed -> colors.hoverContainerColor
        else -> colors.containerColor
    }

    val currentContentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    Box(
        modifier = modifier
            .size(size.buttonSize)
            .bubbleFluidity(
                config = DeXCloseButtonDefaults.Fluidity.copy(enabled = enabled),
                onPressedChanged = { isFluidityPressed = it },
            )
            .clip(CircleShape)
            .background(currentContainerColor)
            .shinyGlare(
                shape = CircleShape,
                intensity = if ((isHovered || isPressed) && enabled) {
                    DefaultGlareIntensity * (1f + 0.60f * pressProgress)
                } else {
                    0f
                },
            )
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(DeXIcons.Close),
            contentDescription = contentDescription,
            tint = currentContentColor,
            modifier = Modifier.size(size.iconSize),
        )
    }
}
