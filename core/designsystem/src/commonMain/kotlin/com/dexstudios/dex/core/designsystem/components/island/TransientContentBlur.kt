package com.dexstudios.dex.core.designsystem.components.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * Applies transient optical motion blur whenever [trigger] changes state.
 *
 * Simulates optical motion blur and depth-of-field dissipation on child content
 * as a container expands, collapses, toggles, or receives tactile interaction,
 * preventing jarring visual cuts during rapid geometric or structural morphs.
 *
 * @param trigger Any state value (e.g. isExpanded, isPressed, selectedMode, isChecked). Whenever
 *   this value changes, the blur rises rapidly to [config.maxBlur] and settles elastically to 0.dp.
 * @param config Configuration for peak blur radius, rise duration, and expand/collapse toggles.
 * @param motion Motion spring kinematics used for the elastic settling falloff.
 */
@Composable
fun Modifier.transientContentBlur(trigger: Any?, config: DynamicContentBlurConfig = DynamicContentBlurConfig.Default, motion: DynamicMotionConfig = DynamicMotionConfig.Default): Modifier {
    if (!config.enabled || config.maxBlur <= 0.dp) return this

    val contentBlurAnimatable = remember { Animatable(0f) }
    var isInitialComposition by remember { mutableStateOf(true) }

    LaunchedEffect(trigger) {
        if (isInitialComposition) {
            isInitialComposition = false
            return@LaunchedEffect
        }

        val shouldBlur = when (trigger) {
            is Boolean -> if (trigger) config.blurOnExpand else config.blurOnCollapse
            else -> true
        }

        if (shouldBlur) {
            contentBlurAnimatable.animateTo(
                targetValue = config.maxBlur.value,
                animationSpec = tween(
                    durationMillis = config.riseDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
            contentBlurAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = if (trigger == true) motion.expandDampingRatio else motion.collapseDampingRatio,
                    stiffness = motion.stiffness,
                ),
            )
        } else {
            contentBlurAnimatable.snapTo(0f)
        }
    }

    val currentBlur = contentBlurAnimatable.value
    return if (currentBlur > 0.5f) {
        this.blur(currentBlur.dp)
    } else {
        this
    }
}
