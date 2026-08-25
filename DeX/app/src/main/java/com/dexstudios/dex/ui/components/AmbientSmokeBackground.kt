package com.dexstudios.dex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.dexstudios.dex.ui.theme.SmokePink
import com.dexstudios.dex.ui.theme.SmokePurple
import com.dexstudios.dex.ui.theme.SmokeViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A single drifting smoke plume.
 *
 * [frequencyX]/[frequencyY] are integer multiples of the shared cycle so the
 * Lissajous drift loops seamlessly when the animation restarts; the golden-ratio
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
    val breathePhase: Float
)

private val SmokePlumes = listOf(
    SmokePlume(
        color = SmokeViolet,
        anchorX = 0.16f, anchorY = 0.18f, radiusFraction = 0.58f,
        driftX = 0.10f, driftY = 0.07f,
        frequencyX = 1, frequencyY = 2, phaseX = 0.00f, phaseY = 0.31f,
        breatheFrequency = 2, breathePhase = 0.00f
    ),
    SmokePlume(
        color = SmokePink,
        anchorX = 0.86f, anchorY = 0.40f, radiusFraction = 0.66f,
        driftX = 0.12f, driftY = 0.09f,
        frequencyX = 2, frequencyY = 1, phaseX = 0.47f, phaseY = 0.83f,
        breatheFrequency = 3, breathePhase = 0.41f
    ),
    SmokePlume(
        color = SmokePurple,
        anchorX = 0.38f, anchorY = 0.92f, radiusFraction = 0.60f,
        driftX = 0.09f, driftY = 0.06f,
        frequencyX = 1, frequencyY = 2, phaseX = 0.71f, phaseY = 0.13f,
        breatheFrequency = 1, breathePhase = 0.67f
    )
)

// Frozen clock position for the haze. The plume math still runs off a
// normalized cycle, so this single constant picks the pose the smoke holds —
// chosen away from phase zero so no two plumes stack at their drift extremes.
// Zero animation cost: the canvas paints once and never invalidates again.
private const val StaticSmokePhase = 0.35f

// Peak center opacity per plume — deliberately faint so content stays readable.
private const val DarkPlumeAlpha = 0.40f
private const val LightPlumeAlpha = 0.40f

// Radial falloff shaping: solid-ish core that dissolves well before the edge,
// which is what makes each plume read as pre-blurred smoke without a real
// blur pass (a full-screen RenderEffect blur would cost GPU for no visible gain).
private const val PlumeCoreStop = 0.40f
private const val PlumeCoreFade = 0.45f
private const val BreatheAmplitude = 0.07f

/**
 * Ambient background haze: a static composition of purple, violet and pink
 * smoke behind the main screen content. Nothing animates — the plumes hold
 * one pose ([StaticSmokePhase]), so the canvas draws once per size change and
 * costs nothing while the screen is idle.
 */
@Composable
fun AmbientSmokeBackground(modifier: Modifier = Modifier) {
    val plumeAlpha = if (isSystemInDarkTheme()) DarkPlumeAlpha else LightPlumeAlpha
    val cycle = StaticSmokePhase

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val baseRadius = minOf(width, height)
        val twoPi = (2.0 * PI).toFloat()

        SmokePlumes.forEach { plume ->
            val angleX = (cycle * plume.frequencyX + plume.phaseX) * twoPi
            val angleY = (cycle * plume.frequencyY + plume.phaseY) * twoPi
            val breathe =
                1f + BreatheAmplitude * sin((cycle * plume.breatheFrequency + plume.breathePhase) * twoPi)
            val radius = baseRadius * plume.radiusFraction * breathe
            val center = Offset(
                x = width * (plume.anchorX + plume.driftX * sin(angleX)),
                y = height * (plume.anchorY + plume.driftY * cos(angleY))
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to plume.color.copy(alpha = plumeAlpha),
                        PlumeCoreStop to plume.color.copy(alpha = plumeAlpha * PlumeCoreFade),
                        1f to plume.color.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}
