package com.dexstudios.dex.window.kinematics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Complete Animation Specifications and Presets for the DeX Floating Dock Card.
 */
object DockCardAnimations {

    // === Standard Dimensions ===
    val CARD_WIDTH_CONTRACTED = 300.dp
    val CARD_WIDTH_EXPANDED = 1054.dp      // File Explorer: 300 + 754
    val SETTINGS_WIDTH_EXPANDED = 675.dp   // Settings: 300 + 375
    val PAIRING_WIDTH_EXPANDED = 400.dp    // PIN/QR: 300 + 100
    val CARD_HEIGHT_CONTRACTED = 430.dp
    val CARD_HEIGHT_EXPANDED = 625.dp      // 430 + 195

    // === Spring Specs (WPF ElasticEase Oscillations=1, Springiness=7 Equivalent) ===
    val BouncyEase = DockCardPhysics.ElasticExpansionSpec
    val BouncyEaseDp = DockCardPhysics.ElasticDpSpec
    val BouncyEaseIntOffset = DockCardPhysics.ElasticIntOffsetSpec

    // === Pop-In Entrance Transition Specs (500ms feel) ===
    val PopInScaleSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 300f)
    val PopInTranslateYSpec = spring<Dp>(dampingRatio = 0.65f, stiffness = 300f)
    val PopInAlphaSpec = tween<Float>(durationMillis = 150, easing = LinearEasing)

    // Staggered parallax for menu contents during entrance
    val PopInMenuTranslateYSpec = spring<Dp>(dampingRatio = 0.65f, stiffness = 300f)
    val PopInMenuContentTranslateYSpec = spring<Dp>(dampingRatio = 0.65f, stiffness = 300f)

    // === Hover & Sink Specs ===
    val HoverEase = DockCardPhysics.HoverEase
    val HoverSpec = tween<Float>(durationMillis = 300, easing = HoverEase)
    val HoverDpSpec = tween<Dp>(durationMillis = 300, easing = HoverEase)
    val PressSinkSpec = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)
    val PressSinkDpSpec = tween<Dp>(durationMillis = 100, easing = FastOutSlowInEasing)

    // === Smooth Transitions ===
    val SmoothEase = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    val SmoothEaseDp = tween<Dp>(durationMillis = 300, easing = FastOutSlowInEasing)
}

/**
 * State holder for the pop-in entrance animation.
 */
data class PopInTransitionState(
    val scale: State<Float>,
    val translateY: State<Dp>,
    val alpha: State<Float>
)

/**
 * Reusable composable to animate pop-in entrance transition:
 * Scale: 0.85 -> 1.0
 * TranslateY: 15.dp -> 0.dp
 * Alpha: 0.0 -> 1.0
 */
@Composable
fun rememberPopInTransition(visible: Boolean): PopInTransitionState {
    val scale = animateFloatAsState(
        targetValue = if (visible) 1.0f else 0.85f,
        animationSpec = DockCardAnimations.PopInScaleSpec,
        label = "popInScale"
    )
    val translateY = animateDpAsState(
        targetValue = if (visible) 0.dp else 15.dp,
        animationSpec = DockCardAnimations.PopInTranslateYSpec,
        label = "popInTranslateY"
    )
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1.0f else 0.0f,
        animationSpec = DockCardAnimations.PopInAlphaSpec,
        label = "popInAlpha"
    )
    return PopInTransitionState(scale, translateY, alpha)
}

/**
 * Modifier extension applying the pop-in graphics layer transformations.
 */
@Composable
fun Modifier.popInTransition(visible: Boolean): Modifier {
    val transition = rememberPopInTransition(visible)
    val scale by transition.scale
    val translateY by transition.translateY
    val alpha by transition.alpha

    return this.graphicsLayer {
        this.scaleX = scale
        this.scaleY = scale
        this.translationY = translateY.toPx()
        this.alpha = alpha
    }
}
