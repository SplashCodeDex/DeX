package com.dexstudios.dex.core.designsystem.components.lottie

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.dexstudios.dex.core.designsystem.assets.LottiePaths
import kotlinx.coroutines.delay

// DevicesMorph playback tuning, carried over verbatim from the reference browser player the
// desktop dock was originally tuned against. Frame counts, not durations: the curve below is
// expressed against the animation's own timeline and converted to wall time by ANIMATION_FPS.
private const val TOTAL_FRAMES = 456f // animation "op"
private const val SETTLE_FRAME = 455f // last frame with the fully settled DeX
private const val SETTLE_PROGRESS = SETTLE_FRAME / TOTAL_FRAMES
private const val RAMP_START_FRAME = 415f // frame where the slow-down begins
private const val MIN_SPEED = 0.12f // final playback speed (gentle drift to a stop)
private const val ANIMATION_FPS = 60f
private const val HOLD_ON_DEX_MS = 4_000L // hold on DeX before the transition
private const val FADE_OUT_MS = 600 // DeX fades out completely (to blank)
private const val FADE_IN_MS = 600 // monitor (start frame) fades in

/**
 * The "scanning for devices" loop: DevicesMorph playing a ritardando settle onto the finished
 * DeX, holding there, fading to blank, then fading the start frame back in — forever.
 *
 * This exact sequence used to exist twice, as hand-kept copies: the desktop dock's empty state
 * and the Android carousel's. The copies had already drifted — the Android one decelerated
 * linearly where the desktop one decelerates quadratically, mixed `System.nanoTime()` deltas
 * with the frame clock, and drove its fades from `System.currentTimeMillis()` instead of the
 * animation clock — so the two apps showed visibly different easing while their comments both
 * claimed to be "exact desktop playback tuning". This is the desktop version, which is the
 * reference implementation; both apps now run it.
 *
 * The animation occupies its caller's [modifier] from the first frame, so a surface that
 * reserves space for it does not jump when the composition finishes parsing.
 *
 * @param enabled when `false` the loop does not run and the animation is not even loaded. The
 * dock keeps its empty state composed behind `contentAlpha = 0f` while collapsed, and a
 * `withFrameNanos` loop left running behind an invisible card forces frame production at
 * refresh rate for nothing. The composed view stays in place either way, so toggling this never
 * moves anything.
 * @param colorFilter lets a caller tint the animation to a theme role; the desktop dock draws it
 * in `onSurfaceVariant`.
 */
@Composable
fun DevicesMorphAnimation(modifier: Modifier = Modifier, enabled: Boolean = true, contentScale: ContentScale = ContentScale.Fit, colorFilter: ColorFilter? = null) {
    val composition by rememberDeXLottieComposition(LottiePaths.DEVICES_MORPH, enabled)

    // Progress and alpha are driven by hand rather than by an iteration count, because the
    // choreography is not "play the clip once": it is a deceleration ramp, a hold, and two
    // timed fades, all on the animation's own frame timeline.
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(composition, enabled) {
        if (composition == null || !enabled) return@LaunchedEffect
        progress.snapTo(0f)
        alpha.snapTo(1f)
        while (true) {
            playToSettleFrame(progress)
            delay(HOLD_ON_DEX_MS)
            alpha.animateTo(0f, tween(FADE_OUT_MS))
            // Back to the monitor while invisible: the jump cannot be seen, and it is what makes
            // the fade below read as a transition rather than a cut.
            progress.snapTo(0f)
            alpha.animateTo(1f, tween(FADE_IN_MS))
        }
    }

    DeXLottie(
        composition = composition,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        progress = { progress.value },
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}

/**
 * Frames 0..455 at full speed until [RAMP_START_FRAME], then decelerating on a quadratic curve
 * down to [MIN_SPEED] as the DeX pops into place — the "settle" the clip was authored around,
 * after which the clip's own remaining frame is deliberately never reached.
 */
private suspend fun playToSettleFrame(progress: Animatable<Float, *>) {
    var lastNanos = withFrameNanos { it }
    while (progress.value < SETTLE_PROGRESS) {
        val nanos = withFrameNanos { it }
        val dtSeconds = (nanos - lastNanos) / 1_000_000_000f
        lastNanos = nanos

        val currentFrame = progress.value * TOTAL_FRAMES
        val speed = if (currentFrame > RAMP_START_FRAME && currentFrame < SETTLE_FRAME) {
            val intoRamp = (currentFrame - RAMP_START_FRAME) / (SETTLE_FRAME - RAMP_START_FRAME)
            1f - (1f - MIN_SPEED) * intoRamp * intoRamp
        } else {
            1f
        }

        progress.snapTo(
            (progress.value + speed * ANIMATION_FPS * dtSeconds / TOTAL_FRAMES)
                .coerceAtMost(SETTLE_PROGRESS),
        )
    }
}
