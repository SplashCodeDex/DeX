package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit

/**
 * Direction-aware analog rolling odometer text that rolls digits vertically
 * using centralized [DynamicMotionConfig] spring kinematics.
 *
 * - When [count] increases: digits roll vertically upward (slides in from bottom, slides out to top).
 * - When [count] decreases: digits roll vertically downward (slides in from top, slides out to bottom).
 * - Clipped to the bounds of the text baseline so numbers smoothly appear and disappear into the seam.
 */
@Composable
fun RollingOdometerText(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxCount: Int = 99,
    motion: DynamicMotionConfig = DynamicMotionConfig.Default,
) {
    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val isIncrement = targetState >= initialState
                val enterOffsetY: (Int) -> Int = if (isIncrement) { height -> height } else { height -> -height }
                val exitOffsetY: (Int) -> Int = if (isIncrement) { height -> -height } else { height -> height }
                val damping = if (isIncrement) motion.expandDampingRatio else motion.collapseDampingRatio

                val enterSpec = spring<IntOffset>(dampingRatio = damping, stiffness = 420f)
                val exitSpec = spring<IntOffset>(dampingRatio = damping, stiffness = 420f)

                (slideInVertically(animationSpec = enterSpec, initialOffsetY = enterOffsetY) + fadeIn(tween(90)))
                    .togetherWith(slideOutVertically(animationSpec = exitSpec, targetOffsetY = exitOffsetY) + fadeOut(tween(70)))
            },
            label = "odometerRoll"
        ) { targetCount ->
            Text(
                text = if (targetCount > maxCount) "$maxCount+" else targetCount.toString(),
                color = color,
                style = style,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
    }
}
