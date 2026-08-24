package com.dexstudios.dex.core.designsystem.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shift arrow glyph (Fluent keyboard-shift shape). Renders the outline at all times and
 * crossfades a filled copy on top when [isFilled] is true, so the regular-to-filled
 * transition reads as the key being physically pressed.
 */
@Composable
fun KeyboardShiftGlyph(isFilled: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val fillAlpha by animateFloatAsState(
        targetValue = if (isFilled) 1f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "shiftFillAlpha",
    )

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val arrow = Path().apply {
            moveTo(12f * s, 2.8f * s)
            lineTo(19.1f * s, 10.1f * s)
            lineTo(15.1f * s, 10.1f * s)
            lineTo(15.1f * s, 16.6f * s)
            lineTo(8.9f * s, 16.6f * s)
            lineTo(8.9f * s, 10.1f * s)
            lineTo(4.9f * s, 10.1f * s)
            close()
        }
        if (fillAlpha > 0f) {
            drawPath(arrow, tint, alpha = fillAlpha)
        }
        drawPath(
            arrow,
            tint,
            style = Stroke(width = 1.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Mouse glyph matching the DeX reference mark: capsule body, two top buttons split by a
 * center gap and a horizontal divider. When [buttonActive] is true the LEFT (primary)
 * button pulses and emits a soft ripple ring clipped to the body — the affordance for
 * "hold Shift, then click". No glow or gradient; alpha and geometry only.
 */
@Composable
fun MouseGlyph(
    buttonActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeTint: Color = MaterialTheme.colorScheme.primary,
) {
    val pulse = rememberInfiniteTransition(label = "mousePulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mousePulseAlpha",
    )
    val ripple = rememberInfiniteTransition(label = "mouseRipple").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
        ),
        label = "mouseRippleProgress",
    )
    val buttonColor by animateColorAsState(
        targetValue = if (buttonActive) activeTint else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
        label = "mouseButtonColor",
    )

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = 1.4f * s
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 7f * s,
                    top = 3.2f * s,
                    right = 17f * s,
                    bottom = 20.8f * s,
                    topLeftCornerRadius = CornerRadius(4.5f * s),
                    topRightCornerRadius = CornerRadius(4.5f * s),
                    bottomRightCornerRadius = CornerRadius(8f * s),
                    bottomLeftCornerRadius = CornerRadius(8f * s),
                ),
            )
        }
        val leftButton = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 7f * s,
                    top = 3.2f * s,
                    right = 11.5f * s,
                    bottom = 10.4f * s,
                    topLeftCornerRadius = CornerRadius(4.5f * s),
                ),
            )
        }
        val rightButton = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 12.5f * s,
                    top = 3.2f * s,
                    right = 17f * s,
                    bottom = 10.4f * s,
                    topRightCornerRadius = CornerRadius(4.5f * s),
                ),
            )
        }

        if (buttonActive) {
            val expand = 2.4f * s * ripple.value
            val rippleAlpha = (1f - ripple.value) * 0.55f
            clipPath(body) {
                drawRoundRect(
                    color = activeTint.copy(alpha = rippleAlpha),
                    topLeft = Offset(7f * s - expand, 3.2f * s - expand),
                    size = Size(4.5f * s + 2 * expand, 7.2f * s + 2 * expand),
                    cornerRadius = CornerRadius(4.5f * s + expand),
                    style = Stroke(width = stroke),
                )
            }
        }

        drawPath(body, tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(7f * s, 10.4f * s), Offset(17f * s, 10.4f * s), strokeWidth = stroke, cap = StrokeCap.Round)
        drawPath(rightButton, tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawPath(leftButton, tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        if (buttonActive) {
            drawPath(leftButton, buttonColor.copy(alpha = buttonColor.alpha * pulse.value))
        }
    }
}
