package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Glare tokens carried over from the retired `LiquidGlassTokens` (Android-derived,
// matching liquid-glass-2026-08-17T18-35-42.json). These three are the only
// survivors now that the kyant backdrop-sampling glass stack is gone —
// shinyGlare never sampled backdrops, it only paints a directional rim.
private val DefaultGlareWidth = 1.dp
private const val DefaultGlareAngle = -52.82f
private const val DefaultGlareIntensity = 0.78f

/**
 * Applies a smooth vertical fading edge (alpha gradient mask) to scrollable containers,
 * seamlessly dissolving scrolling items before they reach viewport boundaries instead of
 * hitting an abrupt, sharp cut-off.
 *
 * @param topFadeHeight Height of the top fade region (default 40.dp). Set to 0.dp to disable top fade.
 * @param bottomFadeHeight Height of the bottom fade region (default 16.dp). Set to 0.dp to disable bottom fade.
 */
fun Modifier.verticalFadingEdge(topFadeHeight: Dp = 40.dp, bottomFadeHeight: Dp = 16.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val topFadePx = topFadeHeight.toPx()
        val bottomFadePx = bottomFadeHeight.toPx()
        val height = size.height

        if (height <= 0f) return@drawWithContent

        val colorStops = mutableListOf<Pair<Float, Color>>()

        if (topFadePx > 0f && topFadePx < height) {
            val topStop = (topFadePx / height).coerceIn(0f, 1f)
            colorStops.add(0f to Color.Transparent)
            colorStops.add(topStop to Color.Black)
        } else {
            colorStops.add(0f to Color.Black)
        }

        if (bottomFadePx > 0f && bottomFadePx < height) {
            val bottomStop = ((height - bottomFadePx) / height).coerceIn(0f, 1f)
            colorStops.add(bottomStop to Color.Black)
            colorStops.add(1f to Color.Transparent)
        } else {
            colorStops.add(1f to Color.Black)
        }

        val brush = Brush.verticalGradient(
            colorStops = colorStops.toTypedArray(),
            startY = 0f,
            endY = height,
        )

        drawRect(
            brush = brush,
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Applies a 100% native frosted surface styling:
 * 1. Clips to [shape].
 * 2. Draws the background with [opacity] (default 1.0f for 100% opaque).
 * 3. Adds the directional [shinyGlare] border highlight rim.
 *
 * @param shape Outline shape to clip and outline.
 * @param backgroundColor Base solid color (typically MaterialTheme.colorScheme.surfaceVariant).
 * @param opacity Opacity fraction for the solid base fill (defaults to 1.0f for completely opaque).
 * @param withGlare Whether to include the shiny glare border highlight (defaults to true).
 * @param glareIntensity Intensity multiplier for the glare rim (defaults to DefaultGlareIntensity = 0.78f).
 */
fun Modifier.frostedSurface(shape: Shape, backgroundColor: Color, opacity: Float = 1.0f, withGlare: Boolean = true, glareIntensity: Float = DefaultGlareIntensity): Modifier {
    var modifier = this
        .clip(shape)
        .background(backgroundColor.copy(alpha = opacity))

    if (withGlare) {
        modifier = modifier.shinyGlare(shape = shape, intensity = glareIntensity)
    }

    return modifier
}

/**
 * Applies a "shiny glare" border effect that simulates a directional light source.
 * This is a high-performance visual illusion that does not require a backdrop.
 *
 * @param shape The outline of the glare.
 * @param width Thickness of the shiny rim.
 * @param tint Optional color tint for the glare (defaults to white).
 * @param angle The direction of the light source in degrees.
 */
fun Modifier.shinyGlare(shape: Shape, width: Dp = DefaultGlareWidth, tint: Color = Color.White, angle: Float = DefaultGlareAngle, intensity: Float = DefaultGlareIntensity): Modifier =
    this then ShinyGlareElement(shape, width, tint, angle, intensity)

private data class ShinyGlareElement(val shape: Shape, val width: Dp, val tint: Color, val angle: Float, val intensity: Float) : ModifierNodeElement<ShinyGlareNode>() {
    override fun create(): ShinyGlareNode = ShinyGlareNode(shape, width, tint, angle, intensity)

    override fun update(node: ShinyGlareNode) {
        node.shape = shape
        node.width = width
        node.tint = tint
        node.angle = angle
        node.intensity = intensity
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shinyGlare"
        properties["shape"] = shape
        properties["width"] = width
        properties["tint"] = tint
        properties["angle"] = angle
        properties["intensity"] = intensity
    }
}

private class ShinyGlareNode(var shape: Shape, var width: Dp, var tint: Color, var angle: Float, var intensity: Float) :
    Modifier.Node(),
    DrawModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()

        val angleRad = angle * (PI / 180f).toFloat()
        val dx = cos(angleRad)
        val dy = sin(angleRad)

        // Map angle to start/end points of the linear gradient relative to the center.
        // We use the diagonal length as a span to ensure the gradient covers the entire shape.
        val radius = sqrt(size.width * size.width + size.height * size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        val start = center - Offset(dx * radius, dy * radius)
        val end = center + Offset(dx * radius, dy * radius)

        val brush = Brush.linearGradient(
            0.0f to tint.copy(alpha = intensity), // Secondary bounced highlight (bottom-left)
            0.2f to tint.copy(alpha = 0f), // Fade to transparent
            0.7f to tint.copy(alpha = 0f), // Keep transparent across the middle
            1.0f to tint.copy(alpha = intensity), // Tight primary highlight (top-right)
            start = start,
            end = end,
        )

        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            brush = brush,
            style = Stroke(width.toPx()),
        )
    }
}
