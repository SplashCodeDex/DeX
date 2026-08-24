package com.dexstudios.dex.window.kinematics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.platform.DockCardMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Complete Animation Specifications and Presets for the DeX Floating Dock Card.
 * Dimensional values derive exclusively from [DockCardMetrics] so rendered Compose
 * geometry and window-placement math share one source of truth.
 */
object DockCardAnimations {

    // === Standard Dimensions ===
    val CARD_WIDTH_CONTRACTED = DockCardMetrics.CARD_WIDTH_CONTRACTED.dp
    val CARD_WIDTH_EXPANDED = DockCardMetrics.FILE_EXPLORER_WIDTH_EXPANDED.dp
    val SETTINGS_WIDTH_EXPANDED = DockCardMetrics.SETTINGS_WIDTH_EXPANDED.dp
    val PAIRING_WIDTH_EXPANDED = DockCardMetrics.PAIRING_WIDTH_EXPANDED.dp
    val CARD_HEIGHT_CONTRACTED = DockCardMetrics.CARD_HEIGHT_CONTRACTED.dp
    val CARD_HEIGHT_EXPANDED = DockCardMetrics.CARD_HEIGHT_EXPANDED.dp

    // === Spring Specs (WPF ElasticEase Oscillations=1, Springiness=7 Equivalent) ===
    val BouncyEase = DockCardPhysics.ElasticExpansionSpec

    // === Pop-In Entrance Alpha Spec ===
    val PopInAlphaSpec = tween<Float>(durationMillis = 150, easing = LinearEasing)

    // Staggered parallax for menu contents during entrance
    val PopInMenuTranslateYSpec = spring<Dp>(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS)
    val PopInMenuContentTranslateYSpec = spring<Dp>(dampingRatio = DockCardPhysics.SPRING_DAMPING_RATIO, stiffness = DockCardPhysics.SPRING_STIFFNESS)

    // === Hover & Sink Specs ===
    val HoverEase = DockCardPhysics.HoverEase
    val HoverSpec = tween<Float>(durationMillis = 500, easing = HoverEase)
    val HoverDpSpec = tween<Dp>(durationMillis = 500, easing = HoverEase)
    val PressSinkSpec = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)
    val PressSinkDpSpec = tween<Dp>(durationMillis = 100, easing = FastOutSlowInEasing)

    // === Smooth Transitions ===
    val SmoothEase = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    val SmoothEaseDp = tween<Dp>(durationMillis = 300, easing = FastOutSlowInEasing)
}
