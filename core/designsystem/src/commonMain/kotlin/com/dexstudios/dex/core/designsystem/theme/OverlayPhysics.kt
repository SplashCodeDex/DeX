package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Mathematical physics specifications and layout metrics for the DeX Fluid Overlay &
 * Notification System (Dynamic Island Banners, Alerts, Popups, Toasts, and Stacked Screens).
 *
 * Provides:
 * - Fluid spring kinematics with subtle overshoot
 * - Apple-style overlapping card stack metrics (8dp peek, 0.96f scale, 12dp hover fan-out)
 * - Elastic drag pull resistance & fling thresholds
 * - Dynamic size morphing springs
 */
object OverlayPhysics {

    // === Visual Identity & Surface Geometry ===
    val CORNER_RADIUS = 48.dp
    const val SURFACE_ALPHA = 0.96f
    val BORDER_WIDTH = 1.dp
    val SCREEN_EDGE_MARGIN = 16.dp

    // === Card Stack Geometry (Apple Shelf Peek & Fan-Out) ===
    val STACK_PEEK_OFFSET_DP = 8.dp
    const val STACK_PEEK_SCALE_STEP = 0.04f // Level 0: 1.0f, Level 1: 0.96f, Level 2: 0.92f
    const val STACK_PEEK_ALPHA_STEP = 0.12f // Depth falloff: 1.0f -> 0.88f -> 0.76f
    val STACK_FAN_GAP_DP = 12.dp
    const val MAX_VISIBLE_PEEKS = 2 // 1 primary card in front + 2 peeking shelves behind = 3 cards visible

    // === Drag & Gesture Physics ===
    const val DRAG_RESISTANCE_MULTIPLIER = 0.50f // Elastic resistance while dragging
    val DRAG_DISMISS_THRESHOLD_DP = 80.dp
    const val FLING_DISMISS_VELOCITY = 600f

    // === Dynamic Island Morph Dimensions ===
    val BANNER_COMPACT_WIDTH = 340.dp
    val BANNER_COMPACT_HEIGHT = 48.dp

    val BANNER_EXPANDED_WIDTH = 420.dp
    val BANNER_EXPANDED_HEIGHT = 164.dp

    val ALERT_DIALOG_WIDTH = 360.dp
    val CONFIRMATION_POPUP_WIDTH = 300.dp

    val TOAST_WIDTH = 320.dp
    val TOAST_MIN_WIDTH = 200.dp
    val TOAST_MAX_WIDTH = 380.dp
    val TOAST_HEIGHT = 48.dp

    val STACKED_SCREEN_MAX_WIDTH = 480.dp
    val STACKED_SCREEN_MAX_HEIGHT = 560.dp

    // === Easing Curves ===
    val OverlayBackEaseOut: Easing = createBackEaseOut(2.80f)
    val OverlayGentleBackEaseOut: Easing = createBackEaseOut(1.40f)

    // === Spring Kinematics ===
    // Entrance: lively pop with subtle overshoot
    const val ENTER_DAMPING = 0.60f
    const val ENTER_STIFFNESS = 350f

    val EnterFloatSpring = spring<Float>(
        dampingRatio = ENTER_DAMPING,
        stiffness = ENTER_STIFFNESS,
    )

    val EnterDpSpring = spring<Dp>(
        dampingRatio = ENTER_DAMPING,
        stiffness = ENTER_STIFFNESS,
    )

    val EnterOffsetSpring = spring<IntOffset>(
        dampingRatio = ENTER_DAMPING,
        stiffness = ENTER_STIFFNESS,
    )

    // Size Morphing: fluid, continuous shape morphing between states
    const val MORPH_DAMPING = 0.70f
    const val MORPH_STIFFNESS = 280f

    val SizeMorphDpSpring = spring<Dp>(
        dampingRatio = MORPH_DAMPING,
        stiffness = MORPH_STIFFNESS,
    )

    val SizeMorphFloatSpring = spring<Float>(
        dampingRatio = MORPH_DAMPING,
        stiffness = MORPH_STIFFNESS,
    )

    // Hover Fan-Out / Collapse: snappy transition with gentle settling
    const val FAN_DAMPING = 0.70f
    const val FAN_STIFFNESS = 350f

    val StackFanDpSpring = spring<Dp>(
        dampingRatio = FAN_DAMPING,
        stiffness = FAN_STIFFNESS,
    )

    val StackFanFloatSpring = spring<Float>(
        dampingRatio = FAN_DAMPING,
        stiffness = FAN_STIFFNESS,
    )

    // Snap-Back after drag release: quick elastic snap
    val DragSnapBackFloatSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 500f,
    )

    // Dismiss Exit: quick, decisive exit
    val ExitTween = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val ExitDpTween = tween<Dp>(durationMillis = 220, easing = FastOutSlowInEasing)
    val ExitOffsetTween = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

    // Auto-dismiss countdown linear tick
    val ProgressLinearTween = tween<Float>(durationMillis = 100, easing = LinearEasing)
}
