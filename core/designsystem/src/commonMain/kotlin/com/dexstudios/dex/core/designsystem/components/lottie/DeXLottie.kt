package com.dexstudios.dex.core.designsystem.components.lottie

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import com.dexstudios.dex.core.designsystem.assets.rememberLottieJson
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieAnimatable
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieAnimatable
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/*
 * THE Lottie pipeline for both apps: one loader, one renderer, one playback vocabulary.
 *
 * Seven surfaces used to each assemble their own chain — read bytes, remember a composition,
 * drive progress, remember a painter, draw an image — and the chains drifted: two of them kept
 * private byte caches (one of which bypassed the shared cache entirely), the DevicesMorph
 * choreography existed twice with different deceleration curves, and Android drew through
 * airbnb's engine while the design system drew through Compottie, so one APK carried two
 * animation engines that no code review could keep in step. Everything below is the single
 * place that pipeline now lives; a site that needs a *different* choreography (the empty-state
 * ritardando, the carousel's tap-to-replay) keeps that choreography and calls these primitives.
 *
 * The engine is Compottie because it is the only engine that already runs on both targets: the
 * design system's `commonMain` draws with it on Android and on desktop, and Compottie's Android
 * runtime was already packaged in the APK.
 */

/**
 * "Repeat without end" sentinel for the `iterations` parameter of [DeXLottie].
 *
 * Re-exported so a call site can loop an animation without importing the rendering engine: the
 * design system owns the choice of engine, the apps own the choreography.
 */
const val DeXLottieForever: Int = Compottie.IterateForever

/**
 * The parsed composition behind [path], `null` until its bytes are in memory *and* parsed.
 *
 * Both halves of that pipeline are cached, at different levels and for different reasons:
 * `LottieAssetCache` keeps the JSON string process-wide, because `Res.readBytes` is suspend IO
 * over files that reach 8 MB and re-reading one every time a card scrolls back into view stalls
 * the frame that is drawing it; Compottie caches the parsed composition keyed by the JSON's
 * content hash, so ten carousel cards referencing four animations still parse four times.
 *
 * @param enabled when `false` the bytes are not read at all — not merely hidden. The dock's
 * empty state stays composed behind `contentAlpha = 0f` for as long as the card is collapsed,
 * and deferring the load to the moment the card is actually visible keeps a collapsed dock free
 * of megabyte-scale IO. Flipping it to `true` starts the load; flipping back stops a load that
 * has not finished yet.
 */
@Composable
fun rememberDeXLottieComposition(path: String, enabled: Boolean = true): State<LottieComposition?> {
    val json = rememberLottieJson(path, enabled).value
    return if (json == null) {
        remember { mutableStateOf<LottieComposition?>(null) }
    } else {
        // Keyed on the path rather than the spec object: Compottie's JsonString spec is created
        // fresh on every call and compares by identity, so keying on it would re-key (and
        // re-load) on every recomposition. A resource path is the stable identity of its bytes.
        rememberLottieComposition(path) { LottieCompositionSpec.JsonString(json) }
    }
}

/**
 * Draws [composition] at [progress] — the one place a Lottie frame reaches the screen.
 *
 * The caller supplies the choreography, not the pipeline: [progress] is read during the draw
 * phase, so a progress source that ticks with the frame clock redraws without recomposing its
 * surroundings.
 *
 * @param modifier sized by the caller. The animation draws inside a [Box] that always occupies
 * that size, so a surface's footprint is identical before the composition lands and after —
 * which is what keeps the dock's empty state from jumping the moment its animation appears.
 */
@Composable
fun DeXLottie(
    composition: LottieComposition?,
    modifier: Modifier = Modifier,
    progress: () -> Float,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilter: ColorFilter? = null,
) {
    Box(modifier = modifier) {
        if (composition != null) {
            Image(
                painter = rememberLottiePainter(
                    composition = composition,
                    progress = progress,
                ),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                colorFilter = colorFilter,
            )
        }
    }
}

/**
 * Single-call playback for the animations that only need "play this, at this speed, this many
 * times": the onboarding welcome looper and the one-shot connected badge, the status panel's
 * per-category idle loop.
 *
 * A site that needs to *observe* progress (the DnD bell hands off to a static glyph at 95%) or
 * drive it from its own clock (the empty-state ritardando) uses [rememberDeXLottieComposition]
 * with [DeXLottie] and keeps that logic where the choreography lives.
 *
 * @param iterations how many times to play; [DeXLottieForever] loops without end.
 * @param speed 1.0 is the animation's authored pace. Values between 0 and 1 slow it down, which
 * is how the welcome looper plays its whole 456-frame clip instead of truncating it.
 * @param placeholder drawn in place of the animation until its bytes are read and parsed. The
 * status panel shows a static device glyph for those few hundred milliseconds on a cold parse,
 * so opening a device's panel never shows an empty frame.
 */
@Composable
fun DeXLottie(
    path: String,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    speed: Float = 1f,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val composition by rememberDeXLottieComposition(path)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed,
    )

    if (composition == null && placeholder != null) {
        placeholder()
    } else {
        DeXLottie(
            composition = composition,
            modifier = modifier,
            progress = { progress },
            contentDescription = contentDescription,
            contentScale = contentScale,
        )
    }
}

/**
 * Imperative playback for a site whose *timing* is its own feature — today the carousel card,
 * which plays once on focus, finishes the remainder if the user swipes away mid-play, and
 * replays on tap but only after it has settled.
 *
 * The handle exists so that choreography can live in app code without app code touching the
 * rendering engine: the state it reads ([progress], [isPlaying], [isSettled]) is Compottie's
 * animation state, but the types named at the call site stay in the design system. Mutual
 * exclusion is the engine's: starting a new run cancels one already in flight, which is exactly
 * what the focus-change path relies on.
 */
@Stable
class DeXLottiePlayback internal constructor(private val animatable: LottieAnimatable) {

    /** Current position, 0f..1f. */
    val progress: Float get() = animatable.progress

    /** True while a run is in flight, including one that is finishing after losing focus. */
    val isPlaying: Boolean get() = animatable.isPlaying

    /**
     * True once a one-shot run has reached its end and stopped — the idle state the carousel's
     * tap-to-replay gate waits for, so a tap lands as a replay only after the entry animation
     * has finished, never as a restart in the middle of it.
     */
    val isSettled: Boolean get() = !animatable.isPlaying && animatable.progress >= SETTLED_PROGRESS

    /**
     * Plays from [fromProgress] — pass the current [progress] to resume a run that lost its
     * composable, which is how a card swiped away mid-play finishes smoothly instead of snapping
     * to its end frame.
     */
    suspend fun play(composition: LottieComposition?, iterations: Int = 1, speed: Float = 1f, fromProgress: Float = 0f) {
        if (composition == null) return
        animatable.animate(
            composition = composition,
            iterations = iterations,
            speed = speed,
            initialProgress = fromProgress,
        )
    }

    /** Jumps to the settled end frame without playing through the frames in between. */
    suspend fun snapToEnd(composition: LottieComposition?) {
        if (composition == null) return
        animatable.snapTo(composition = composition, progress = 1f)
    }

    private companion object {
        /**
         * A run that ended normally parks at exactly 1f; the epsilon absorbs a progress value
         * rounded while the frame clock was catching up, so "settled" cannot miss by one frame.
         */
        const val SETTLED_PROGRESS = 0.99f
    }
}

/** A [DeXLottiePlayback] remembered for this composition. Create one per animated card. */
@Composable
fun rememberDeXLottiePlayback(): DeXLottiePlayback {
    val animatable = rememberLottieAnimatable()
    return remember(animatable) { DeXLottiePlayback(animatable) }
}
