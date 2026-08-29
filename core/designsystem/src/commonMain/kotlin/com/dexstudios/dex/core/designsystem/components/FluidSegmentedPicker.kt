package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.theme.DarkBackground

data class SegmentOption<T>(val value: T, val label: String, val icon: Painter? = null)

/**
 * FluidSegmentedPicker:
 * A modern, spring-sliding segmented pill control for Desktop DeX.
 *
 * Features:
 * - Fluid spring-animated indicator pill sliding across segment slots
 * - Soft elevated active indicator with subtle shadow and glare
 * - Accessible click targets and hover affordance (hand cursor)
 * - Harmonious light & dark contrast tokens
 */
@Composable
fun <T> FluidSegmentedPicker(options: List<SegmentOption<T>>, selectedOption: T, onOptionSelected: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (options.isEmpty()) return

    val selectedIndex = options.indexOfFirst { it.value == selectedOption }.coerceAtLeast(0)
    val cornerRadius = 10.dp
    val outerPadding = 3.dp

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val containerBg = if (isDark) {
        Color(0xFF22242A)
    } else {
        Color(0xFFE4E7F0)
    }
    val containerBorder = if (isDark) {
        Color(0xFF33363F)
    } else {
        Color(0xFFD0D4E0)
    }

    val activePillBg = if (isDark) {
        Color(0xFF363942)
    } else {
        Color(0xFFFFFFFF)
    }
    val activePillBorder = if (isDark) {
        Color(0xFF4A4E5A)
    } else {
        Color(0xFFCBD0DC)
    }

    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(cornerRadius))
            .shinyGlare(shape = RoundedCornerShape(cornerRadius))
            .padding(outerPadding),
    ) {
        val availableWidth = maxWidth
        val segmentCount = options.size
        val segmentWidth = availableWidth / segmentCount
        val targetIndicatorOffset = segmentWidth * selectedIndex

        val animatedIndicatorOffset by animateDpAsState(
            targetValue = targetIndicatorOffset,
            animationSpec = spring(
                dampingRatio = 0.76f,
                stiffness = 450f,
            ),
            label = "segmentedPickerOffset",
        )

        // Sliding Active Indicator Pill
        Box(
            modifier = Modifier
                .offset(x = animatedIndicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(cornerRadius - 2.dp),
                    spotColor = Color.Black.copy(alpha = 0.25f),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                )
                .clip(RoundedCornerShape(cornerRadius - 2.dp))
                .background(activePillBg)
                .border(1.dp, activePillBorder, RoundedCornerShape(cornerRadius - 2.dp))
                .shinyGlare(shape = RoundedCornerShape(cornerRadius - 2.dp)),
        )

        // Segment Options Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    animationSpec = tween(180),
                    label = "segmentedText_$index",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(cornerRadius - 2.dp))
                        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                            onClick = { onOptionSelected(option.value) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
