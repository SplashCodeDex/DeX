package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies a "shiny glare" border effect that simulates a directional light source.
 * This is a high-performance visual illusion that does not require a backdrop.
 *
 * @param shape The outline of the glare.
 * @param width Thickness of the shiny rim.
 * @param tint Optional color tint for the glare (defaults to white).
 * @param angle The direction of the light source in degrees.
 */
fun Modifier.shinyGlare(
    shape: Shape,
    width: Dp = 1.dp,
    tint: Color = Color.White,
    angle: Float = LiquidGlassTokens.GlareAngle,
    intensity: Float = LiquidGlassTokens.GlareFactor
): Modifier = composed {
    val brush = remember(tint, angle, intensity) {
        val angleRad = angle * (PI / 180f).toFloat()

        // Direction vector from the angle
        val dx = cos(angleRad)
        val dy = sin(angleRad)

        // Map angle to start/end points of the linear gradient
        // We use a large span (1000f) to ensure the linear gradient "sweep"
        // feels consistent across different component sizes.
        val start = Offset(500f - dx * 500f, 500f - dy * 500f)
        val end = Offset(500f + dx * 500f, 500f + dy * 500f)

        Brush.linearGradient(
            0.0f to tint.copy(alpha = 0f),
            0.45f to tint.copy(alpha = 0.05f),
            0.5f to tint.copy(alpha = intensity),
            0.55f to tint.copy(alpha = 0.05f),
            1.0f to tint.copy(alpha = 0f),
            start = start,
            end = end
        )
    }

    this.border(width = width, brush = brush, shape = shape)
}
