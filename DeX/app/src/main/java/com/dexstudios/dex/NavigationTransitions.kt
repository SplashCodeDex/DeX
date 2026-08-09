package com.dexstudios.dex

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Centralized iOS-style motion for every screen transition in the app,
 * mirroring UIKit's navigation stack:
 *
 * - **Tab switch** — quick crossfade with a subtle scale, since tabs share a
 *   level; a horizontal slide there would read as a pager, not iOS.
 * - **Push** (future detail screens) — the incoming screen slides in from the
 *   right; the screen behind recedes with a 1/3-width parallax, shrinks to 96%
 *   scale and dims to 70% alpha (the classic iOS back-screen treatment).
 * - **Pop** (future back navigation) — the receded screen returns while the top
 *   screen slides out the way it came.
 *
 * All tuning values live in this one object so the whole app shares a single
 * motion language — tweak once, everywhere.
 */
object NavigationTransitions {

    // ----- iOS timing curves (UIKit's "default" cubic-bezier approximations) -----
    /** UIKit push curve: cubic-bezier(0.32, 0.72, 0, 1) — fast start, long settle. */
    val PushEase: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** UIKit pop curve: cubic-bezier(0.22, 1, 0.36, 1) — eased return. */
    val PopEase: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    // ----- Durations (ms) -----
    const val PUSH_DURATION_MS = 400
    const val POP_DURATION_MS = 350
    const val TAB_DURATION_MS = 250

    // ----- Push / pop geometry -----
    /** Outgoing screen parallax: slides `width / 3` during a push. */
    const val PARALLAX_DIVISOR = 3

    /** Receded screen scale during a push / at pop start. */
    const val BEHIND_SCALE = 0.96f

    /** Receded screen alpha (dim) during a push / at pop start. */
    const val BEHIND_ALPHA = 0.7f

    /** Tab-switch crossfade scale. */
    const val TAB_SCALE = 0.98f

    /** Same-level tab switch: crossfade + subtle scale (iOS tab-bar style). */
    fun tabSwitch(): ContentTransform =
        (
            fadeIn(animationSpec = tween(TAB_DURATION_MS, easing = PushEase)) +
                scaleIn(
                    initialScale = TAB_SCALE,
                    animationSpec = tween(TAB_DURATION_MS, easing = PushEase)
                )
            ) togetherWith (
            fadeOut(animationSpec = tween(TAB_DURATION_MS, easing = PushEase)) +
                scaleOut(
                    targetScale = TAB_SCALE,
                    animationSpec = tween(TAB_DURATION_MS, easing = PushEase)
                )
            )

    /** iOS push: incoming from the right, outgoing recedes with parallax. */
    fun pushSlide(): ContentTransform =
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(PUSH_DURATION_MS, easing = PushEase)
        ) togetherWith (
            slideOutHorizontally(
                targetOffsetX = { -it / PARALLAX_DIVISOR },
                animationSpec = tween(PUSH_DURATION_MS, easing = PushEase)
            ) +
                scaleOut(
                    targetScale = BEHIND_SCALE,
                    animationSpec = tween(PUSH_DURATION_MS, easing = PushEase)
                ) +
                fadeOut(
                    targetAlpha = BEHIND_ALPHA,
                    animationSpec = tween(PUSH_DURATION_MS, easing = PushEase)
                )
            )

    /**
     * iOS pop: the receded screen returns while the top screen leaves the way it
     * came. `exitingToRight = true` slides the top screen out the right edge
     * (standard back); `false` mirrors it for right-edge gestures.
     */
    fun popSlide(exitingToRight: Boolean = true): ContentTransform {
        val exitOffset: (Int) -> Int = { width -> if (exitingToRight) width else -width }
        val enterOffset: (Int) -> Int = { width ->
            if (exitingToRight) -width / PARALLAX_DIVISOR else width / PARALLAX_DIVISOR
        }
        return (
            slideInHorizontally(
                initialOffsetX = enterOffset,
                animationSpec = tween(POP_DURATION_MS, easing = PopEase)
            ) +
                scaleIn(
                    initialScale = BEHIND_SCALE,
                    animationSpec = tween(POP_DURATION_MS, easing = PopEase)
                ) +
                fadeIn(
                    initialAlpha = BEHIND_ALPHA,
                    animationSpec = tween(POP_DURATION_MS, easing = PopEase)
                )
            ) togetherWith
            slideOutHorizontally(
                targetOffsetX = exitOffset,
                animationSpec = tween(POP_DURATION_MS, easing = PopEase)
            )
    }
}
