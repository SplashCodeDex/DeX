package com.dexstudios.dex.ui.components.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
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
    width: Dp = LiquidGlassTokens.GlareWidth,
    tint: Color = Color.White,
    angle: Float = LiquidGlassTokens.GlareAngle,
    intensity: Float = LiquidGlassTokens.GlareFactor
): Modifier = this then ShinyGlareElement(shape, width, tint, angle, intensity)

private data class ShinyGlareElement(
    val shape: Shape,
    val width: Dp,
    val tint: Color,
    val angle: Float,
    val intensity: Float
) : ModifierNodeElement<ShinyGlareNode>() {
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

private class ShinyGlareNode(
    var shape: Shape,
    var width: Dp,
    var tint: Color,
    var angle: Float,
    var intensity: Float
) : Modifier.Node(), DrawModifierNode {

    override fun ContentDrawScope.draw() {
        drawContent()

        val angleRad = angle * (PI / 180f).toFloat()
        val dx = cos(angleRad)
        val dy = sin(angleRad)

        // Map angle to start/end points of the linear gradient relative to the center.
        // We use the diagonal length as a span to ensure the gradient covers the entire shape.
        val radius = kotlin.math.sqrt(size.width * size.width + size.height * size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        val start = center - Offset(dx * radius, dy * radius)
        val end = center + Offset(dx * radius, dy * radius)

        val brush = Brush.linearGradient(
            0.0f to tint.copy(alpha = 0f),
            0.3f to tint.copy(alpha = 0.05f),
            1.0f to tint.copy(alpha = intensity),
            start = start,
            end = end
        )

        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            brush = brush,
            style = Stroke(width.toPx())
        )
    }
}
