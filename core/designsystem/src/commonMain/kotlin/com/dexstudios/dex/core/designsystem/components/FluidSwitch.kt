package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.theme.DarkBackground

// Dedicated high-contrast switch color tokens for crisp visibility in both Light and Dark themes.
val SwitchActiveGreen = Color(0xFF34C759)
val SwitchInactiveLight = Color(0xFFD4D7E0)
val SwitchInactiveDark = Color(0xFF3D4048)

/**
 * FluidSwitch: A premium, spring-driven toggle switch for Desktop DeX.
 *
 * Features:
 * - Squash & stretch kinematics (thumb expands horizontally while pressed or dragged)
 * - Spring-damped physical translation
 * - Directional frosted glass glare rim on track
 * - Crisp ambient drop shadow on thumb
 * - Cursor hover affordance (hand cursor)
 * - Accessible toggleable semantics with Role.Switch
 */
@Composable
fun FluidSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeTrackColor: Color = SwitchActiveGreen,
    inactiveTrackColor: Color = if (MaterialTheme.colorScheme.background == DarkBackground) {
        SwitchInactiveDark
    } else {
        SwitchInactiveLight
    },
    thumbColor: Color = Color.White,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val thumbRestingSize = 20.dp
    val thumbPadding = 3.dp

    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Squash & stretch: thumb stretches horizontally during press
    val dynamicThumbWidth: Dp = if (isPressed) 24.dp else thumbRestingSize

    // End offset calculation based on dynamic thumb width
    val thumbTravelDistance = trackWidth - dynamicThumbWidth - thumbPadding
    val targetThumbOffset = if (checked) thumbTravelDistance else thumbPadding

    val animatedThumbOffset by animateDpAsState(
        targetValue = targetThumbOffset,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 420f,
        ),
        label = "fluidSwitchThumbOffset",
    )

    val animatedThumbWidth by animateDpAsState(
        targetValue = dynamicThumbWidth,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f,
        ),
        label = "fluidSwitchThumbWidth",
    )

    val animatedTrackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = tween(durationMillis = 240),
        label = "fluidSwitchTrackColor",
    )

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val targetBorderColor = if (checked) {
        activeTrackColor.copy(alpha = 0.5f)
    } else if (isDark) {
        Color(0xFF4F535C)
    } else {
        Color(0xFFB8BCC6)
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 240),
        label = "fluidSwitchBorderColor",
    )

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .then(toggleModifier)
            .clip(CircleShape)
            .background(if (enabled) animatedTrackColor else animatedTrackColor.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = if (enabled) animatedBorderColor else animatedBorderColor.copy(alpha = 0.3f),
                shape = CircleShape,
            )
            .shinyGlare(shape = CircleShape)
            .padding(vertical = thumbPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Sliding & Stretching Thumb
        Box(
            modifier = Modifier
                .offset(x = animatedThumbOffset)
                .size(width = animatedThumbWidth, height = thumbRestingSize)
                .shadow(
                    elevation = if (isHovered || isPressed) 4.dp else 2.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.35f),
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                )
                .clip(CircleShape)
                .background(if (enabled) thumbColor else thumbColor.copy(alpha = 0.6f)),
        )
    }
}
