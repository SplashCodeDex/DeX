package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dexstudios.dex.core.designsystem.theme.SmokePink
import com.dexstudios.dex.core.designsystem.theme.SmokePurple
import com.dexstudios.dex.core.designsystem.theme.SmokeViolet
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A single smoke plume.
 *
 * [frequencyX]/[frequencyY] are integer multiples of the shared cycle so the
 * Lissajous pose math stays seamless as the clock advances; the golden-ratio
 * style pairing of frequencies keeps the path from looking elliptical.
 */
private data class SmokePlume(
    val color: Color,
    val anchorX: Float,
    val anchorY: Float,
    val radiusFraction: Float,
    val driftX: Float,
    val driftY: Float,
    val frequencyX: Int,
    val frequencyY: Int,
    val phaseX: Float,
    val phaseY: Float,
    val breatheFrequency: Int,
    val breathePhase: Float,
)

private val SmokePlumes = listOf(
    SmokePlume(
        color = SmokeViolet,
        anchorX = 0.16f, anchorY = 0.18f, radiusFraction = 0.58f,
        driftX = 0.10f, driftY = 0.07f,
        frequencyX = 1, frequencyY = 2, phaseX = 0.00f, phaseY = 0.31f,
        breatheFrequency = 2, breathePhase = 0.00f,
    ),
    SmokePlume(
        color = SmokePink,
        anchorX = 0.86f, anchorY = 0.40f, radiusFraction = 0.66f,
        driftX = 0.12f, driftY = 0.09f,
        frequencyX = 2, frequencyY = 1, phaseX = 0.47f, phaseY = 0.83f,
        breatheFrequency = 3, breathePhase = 0.41f,
    ),
    SmokePlume(
        color = SmokePurple,
        anchorX = 0.38f, anchorY = 0.92f, radiusFraction = 0.60f,
        driftX = 0.09f, driftY = 0.06f,
        frequencyX = 1, frequencyY = 2, phaseX = 0.71f, phaseY = 0.13f,
        breatheFrequency = 1, breathePhase = 0.67f,
    ),
)

// Canonical clock position — the pose every mood returns to. The plume math runs off a
// normalized cycle, so this constant picks the resting arrangement of the haze.
private const val StaticSmokePhase = 0.35f

// Peak center opacity per plume — reduced for desktop.
private const val DarkPlumeAlpha = 0.10f
private const val LightPlumeAlpha = 0.20f

// Radial falloff shaping: solid-ish core that dissolves well before the edge,
// which makes each plume read as pre-blurred smoke without any real blur pass.
private const val PlumeCoreStop = 0.40f
private const val PlumeCoreFade = 0.45f
private const val BreatheAmplitude = 0.07f

/** How the haze holds itself while a panel owns the stage. */
enum class AmbientSmokeMood {
    /** Canonical frozen pose; produces zero frames. */
    Resting,

    /** File explorer drawer: expansive, quicker drift, swells leftward, violet leads. */
    Explorer,

    /** Settings drawer: grounded, slowest drift, settles downward, purple leads. */
    Settings,

    /** Pairing stage: plumes converge toward center and dim so PIN/QR owns contrast. */
    Pairing,
}

/**
 * Per-mood choreography targets. Everything here modulates the existing parametric
 * plume math — no new colors are introduced, only position/scale/emphasis deltas.
 *
 * [driftCyclesPerSecond] advances the shared Lissajous clock while the mood holds.
 * [focusPull] blends every anchor toward the card center (pairing convergence).
 * [leanX]/[leanY] shift all anchors as a fraction of card size (directional yield).
 * [radiusScale] multiplies the breathing radius. [alphaScale] dims/brightens globally.
 * [emphasizedPlume] indexes [SmokePlumes]; it brightens while siblings recede.
 */
private data class SmokeMoodTuning(val driftCyclesPerSecond: Float, val focusPull: Float, val leanX: Float, val leanY: Float, val radiusScale: Float, val alphaScale: Float, val emphasizedPlume: Int?)

private fun moodTuning(mood: AmbientSmokeMood): SmokeMoodTuning = when (mood) {
    AmbientSmokeMood.Resting -> SmokeMoodTuning(0f, 0f, 0f, 0f, 1f, 1f, null)
    AmbientSmokeMood.Explorer -> SmokeMoodTuning(0.016f, 0f, -0.075f, 0.02f, 1.08f, 1.10f, 0)
    AmbientSmokeMood.Settings -> SmokeMoodTuning(0.011f, 0f, 0.02f, 0.055f, 0.94f, 1.06f, 2)
    AmbientSmokeMood.Pairing -> SmokeMoodTuning(0.009f, 0.35f, 0f, -0.03f, 0.88f, 0.82f, null)
}

// Engagement pacing: the haze never snaps attention to itself the instant a panel
// opens — it waits a beat, then glides. Release is slightly quicker than engagement
// so a dismissed panel does not feel like it drags its shadow behind it.
private const val EngagementDelayMs = 200L
private const val DisengageDelayMs = 140L

// Shared smoothing for every geometric response: critically damped and slow, so the
// haze reads as heavy fluid settling into place rather than a UI state change.
private val MoodResponseSpring = spring<Float>(dampingRatio = 1f, stiffness = 90f)

// Homecoming easing for the Lissajous clock when the last panel closes.
private val MoodSettleTween = tween<Float>(durationMillis = 1700, easing = FastOutSlowInEasing)

// Emphasis redistribution: the lead plume lifts, siblings step back slightly.
private const val EmphasisLeadFactor = 1.22f
private const val EmphasisSiblingFactor = 0.86f

private fun emphasisTargetFor(lead: Int?, plumeIndex: Int): Float = when (lead) {
    null -> 1f
    plumeIndex -> EmphasisLeadFactor
    else -> EmphasisSiblingFactor
}

/**
 * Ambient background haze: a constellation of purple, violet and pink smoke behind the
 * dock card face that live-choreographs itself around whichever panel is expanded.
 *
 * Motion discipline mirrors the rest of the app's animation surfaces:
 *  - Reactions engage on a short delay and glide on one critically damped slow spring,
 *    so the haze reads as heavy fluid settling rather than a UI state change; quick
 *    panel flicks never churn it.
 *  - At [AmbientSmokeMood.Resting] the haze settles back to the exact user-tuned static
 *    pose and produces zero frames (the clock coroutine has exited).
 *  - While any panel is expanded, the shared clock drifts at that mood's pace and every
 *    geometric response (lean/pull/swell/dim/emphasis) glides on the shared spring.
 *  - Animated values are read exclusively inside the draw scope, so frames invalidate
 *    redraw-only — the composition tree never recomposes per frame.
 */
@Composable
fun AmbientSmokeBackground(modifier: Modifier = Modifier, mood: AmbientSmokeMood = AmbientSmokeMood.Resting) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val plumeAlpha = if (isDark) DarkPlumeAlpha else LightPlumeAlpha

    // Delayed engagement: the requested mood is only "armed" after a short beat, so
    // quick panel flicks never churn the haze and every response reads as deliberate.
    var armedMood by remember { mutableStateOf(AmbientSmokeMood.Resting) }
    LaunchedEffect(mood) {
        delay(if (mood == AmbientSmokeMood.Resting) DisengageDelayMs else EngagementDelayMs)
        armedMood = mood
    }
    val tuning = moodTuning(armedMood)

    // Shared Lissajous clock. Drifts forward only while an armed mood owns the stage,
    // ramping with the same spring that carries the geometry; on Rest it tweens home
    // along the shortest modular arc and the driver exits (zero frames at rest).
    val phase = remember { Animatable(StaticSmokePhase) }
    val driftRamp by animateFloatAsState(
        targetValue = if (armedMood == AmbientSmokeMood.Resting) 0f else 1f,
        animationSpec = MoodResponseSpring,
        label = "smokeDriftRamp",
    )
    LaunchedEffect(armedMood) {
        if (armedMood == AmbientSmokeMood.Resting) {
            // Return along the shortest modular arc so the clock never visibly unwinds
            // multiple full cycles after a long session with a panel open.
            val nearestHome = StaticSmokePhase + (phase.value - StaticSmokePhase).roundToInt()
            if (phase.value != nearestHome) {
                phase.animateTo(nearestHome, MoodSettleTween)
            }
        } else {
            val speed = moodTuning(armedMood).driftCyclesPerSecond
            var lastNanos = withFrameNanos { it }
            while (true) {
                val nanos = withFrameNanos { it }
                val dt = (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                phase.snapTo(phase.value + dt * speed * driftRamp)
            }
        }
    }

    val leanX by animateFloatAsState(tuning.leanX, MoodResponseSpring, label = "smokeLeanX")
    val leanY by animateFloatAsState(tuning.leanY, MoodResponseSpring, label = "smokeLeanY")
    val radiusScale by animateFloatAsState(tuning.radiusScale, MoodResponseSpring, label = "smokeRadius")
    val alphaScale by animateFloatAsState(tuning.alphaScale, MoodResponseSpring, label = "smokeAlpha")
    val focusPull by animateFloatAsState(tuning.focusPull, MoodResponseSpring, label = "smokePull")
    val emphasis0 by animateFloatAsState(emphasisTargetFor(tuning.emphasizedPlume, 0), MoodResponseSpring, label = "smokeEmph0")
    val emphasis1 by animateFloatAsState(emphasisTargetFor(tuning.emphasizedPlume, 1), MoodResponseSpring, label = "smokeEmph1")
    val emphasis2 by animateFloatAsState(emphasisTargetFor(tuning.emphasizedPlume, 2), MoodResponseSpring, label = "smokeEmph2")
    val emphasisByIndex = floatArrayOf(emphasis0, emphasis1, emphasis2)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val baseRadius = minOf(width, height)
        val twoPi = (2.0 * PI).toFloat()
        val clock = phase.value

        SmokePlumes.forEachIndexed { index, plume ->
            val angleX = (clock * plume.frequencyX + plume.phaseX) * twoPi
            val angleY = (clock * plume.frequencyY + plume.phaseY) * twoPi
            val breathe =
                1f + BreatheAmplitude * sin((clock * plume.breatheFrequency + plume.breathePhase) * twoPi)
            val radius = baseRadius * plume.radiusFraction * breathe * radiusScale

            // Convergence blends the anchor toward the card center before the mood lean.
            val anchorX = plume.anchorX + (0.5f - plume.anchorX) * focusPull + leanX
            val anchorY = plume.anchorY + (0.5f - plume.anchorY) * focusPull + leanY
            val center = Offset(
                x = width * (anchorX + plume.driftX * sin(angleX)),
                y = height * (anchorY + plume.driftY * cos(angleY)),
            )

            val plumeAlphaScaled = plumeAlpha * alphaScale * emphasisByIndex[index]

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to plume.color.copy(alpha = plumeAlphaScaled),
                        PlumeCoreStop to plume.color.copy(alpha = plumeAlphaScaled * PlumeCoreFade),
                        1f to plume.color.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
