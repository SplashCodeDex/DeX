package com.dexstudios.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.components.island.DynamicFluidityConfig
import com.dexstudios.dex.ui.icons.MaterialSymbols

/**
 * Standardized sizing presets for [DynamicDismissButton].
 */
enum class DynamicDismissButtonSize(
    val buttonSize: Dp,
    val iconSize: Dp
) {
    Small(buttonSize = 22.dp, iconSize = 12.dp),
    Medium(buttonSize = 28.dp, iconSize = 16.dp),
    Large(buttonSize = 36.dp, iconSize = 20.dp)
}

/**
 * Color configuration for [DynamicDismissButton].
 */
@Immutable
data class DynamicDismissButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color = containerColor.copy(alpha = 0.04f),
    val disabledContentColor: Color = contentColor.copy(alpha = 0.38f)
)

/**
 * Default design tokens and presets for [DynamicDismissButton].
 */
object DynamicDismissButtonDefaults {
    val DefaultSize = DynamicDismissButtonSize.Medium

    /**
     * Tactile bubble fluidity physics tuned for small dismiss/clear touch targets.
     * Provides a crisp, responsive squish on press with elastic spring snap-back.
     */
    val Fluidity = DynamicFluidityConfig(
        enabled = true,
        pressScale = 0.88f,
        pullFactor = 0.08f,
        elasticity = 0.12f,
        scalePressSpeed = 350f,
        scalePressDamping = 0.40f,
        scaleSettleSpeed = 380f
    )

    @Composable
    fun colors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = LocalContentColor.current
    ): DynamicDismissButtonColors {
        val resolvedContainer = if (containerColor.isSpecified) {
            containerColor
        } else {
            contentColor.copy(alpha = 0.10f)
        }
        return DynamicDismissButtonColors(
            containerColor = resolvedContainer,
            contentColor = contentColor
        )
    }
}

/**
 * Centralized, standardized Dismiss/Close button with organic bubble fluidity physics
 * and ZERO liquid-glass/backdrop dependency.
 *
 * Designed to be dropped into any UI surface (search bars, selection counter pills,
 * dialogs, modal sheets, dynamic island capsules, or chips) without shader overhead.
 *
 * Encapsulates:
 * 1. Tactile [bubbleFluidity] physics with anisotropic squish and viscoelastic snap-back.
 * 2. Mechanical haptic click feedback on tap ([HapticFeedbackType.TextHandleMove]).
 * 3. Consistent circular boundary clipping and accessible semantics.
 * 4. Flexible sizing presets ([DynamicDismissButtonSize]) and color tokens.
 */
@Composable
fun DynamicDismissButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: DynamicDismissButtonSize = DynamicDismissButtonDefaults.DefaultSize,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: DynamicDismissButtonColors = DynamicDismissButtonDefaults.colors(),
    fluidity: DynamicFluidityConfig = DynamicDismissButtonDefaults.Fluidity,
    icon: ImageVector = MaterialSymbols.Close,
    contentDescription: String? = "Dismiss",
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    DynamicDismissButton(
        onClick = onClick,
        modifier = modifier,
        buttonSize = size.buttonSize,
        iconSize = size.iconSize,
        enabled = enabled,
        shape = shape,
        colors = colors,
        fluidity = fluidity,
        icon = icon,
        contentDescription = contentDescription,
        hapticFeedbackType = hapticFeedbackType,
        interactionSource = interactionSource
    )
}

/**
 * Granular overload of [DynamicDismissButton] supporting explicit [Dp] dimensions.
 */
@Composable
fun DynamicDismissButton(
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: DynamicDismissButtonColors = DynamicDismissButtonDefaults.colors(),
    fluidity: DynamicFluidityConfig = DynamicDismissButtonDefaults.Fluidity,
    icon: ImageVector = MaterialSymbols.Close,
    contentDescription: String? = "Dismiss",
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.TextHandleMove,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val haptic = LocalHapticFeedback.current

    val currentContainerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val currentContentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    Box(
        modifier = modifier
            .size(buttonSize)
            .bubbleFluidity(config = if (enabled) fluidity else DynamicFluidityConfig.Disabled)
            .clip(shape)
            .background(currentContainerColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (hapticFeedbackType != null) {
                        haptic.performHapticFeedback(hapticFeedbackType)
                    }
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = currentContentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
