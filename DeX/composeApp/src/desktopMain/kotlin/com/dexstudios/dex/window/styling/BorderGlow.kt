package com.dexstudios.dex.window.styling

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Subpixel antialiased inset border stroke with subtle ambient outer glow highlight.
 *
 * Geometry & DPI Alignment:
 * - Solves fuzzy borders on fractional DPI scaling (125%, 150%, 175%) by insetting
 *   the inner stroke by half-stroke width.
 * - Draws a 2-layer composite:
 *   1. Outer ambient glow: 2dp semi-transparent stroke creating a soft luminous edge.
 *   2. Inner crisp stroke: 1dp solid stroke (#2B2631) perfectly aligned to inner pixel grid.
 *
 * @param strokeWidth Width of the inner crisp stroke (default 1.dp).
 * @param borderColor Crisp inner border line color (default #2B2631).
 * @param glowColor Ambient outer specular highlight color (default Color.White @ 12% alpha).
 * @param cornerRadius Outer corner radius in Dp (default 34.dp).
 */
fun Modifier.subpixelBorderGlow(
    strokeWidth: Dp = 1.dp,
    borderColor: Color = Color(0xFF2B2631),
    glowColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.12f),
    cornerRadius: Dp = 34.dp
): Modifier = this.drawWithContent {
    // 1. Draw inner composable content first
    drawContent()

    val strokePx = strokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val radiusPx = cornerRadius.toPx()

    // 2. Outer subtle ambient glow stroke (drawn outward by halfStroke)
    if (glowColor.alpha > 0f) {
        drawRoundRect(
            color = glowColor,
            topLeft = Offset(-halfStroke, -halfStroke),
            size = Size(size.width + strokePx, size.height + strokePx),
            cornerRadius = CornerRadius(radiusPx + halfStroke, radiusPx + halfStroke),
            style = Stroke(width = strokePx * 2f)
        )
    }

    // 3. Crisp inset inner border stroke (drawn strictly inset to avoid clipping)
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(halfStroke, halfStroke),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(
            (radiusPx - halfStroke).coerceAtLeast(0f),
            (radiusPx - halfStroke).coerceAtLeast(0f)
        ),
        style = Stroke(width = strokePx)
    )
}
