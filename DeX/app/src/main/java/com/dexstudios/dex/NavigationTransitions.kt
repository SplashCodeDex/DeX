package com.dexstudios.dex

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * Centralized iOS-style motion for every screen transition in the app,
 * mirroring UIKit's navigation stack:
 *
 * - **Tab switch** — quick crossfade with a subtle scale, since tabs share a
 *   level; a horizontal slide there would read as a pager, not iOS.
 *
 * All tuning values live in this one object so the whole app shares a single
 * motion language — tweak once, everywhere.
 */
object NavigationTransitions {

    // ----- iOS timing curves (UIKit's "default" cubic-bezier approximations) -----
    /** UIKit push curve: cubic-bezier(0.32, 0.72, 0, 1) — fast start, long settle. */
    val PushEase: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // ----- Durations (ms) -----
    const val TAB_DURATION_MS = 250

    // ----- Push / pop geometry -----
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
}
