package com.dexstudios.dex.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

/**
 * High-performance GPU Gaussian drop shadow using Skia MaskFilter.makeBlur.
 *
 * Performance Architecture:
 * - Reuses native Skia Paint and MaskFilter instances across frames via [remember].
 * - Eliminates all JNI/C++ allocations during 60-120 FPS spring animations and dragging.
 * - Enforces mathematical Gaussian kernel standard deviation: sigma = blurRadius / 2.0f.
 *
 * @param color Shadow color including alpha channel (e.g. Color.Black.copy(alpha = 0.55f)).
 * @param blurRadius Total visual blur radius in Dp (sigma = blurRadius.toPx() * 0.5f).
 * @param borderRadius Rounded corner radius matching the target composable (e.g. 34.dp).
 * @param offsetX Horizontal shadow offset (default 0.dp).
 * @param offsetY Vertical shadow offset (default 8.dp for resting elevation).
 * @param spread Optional expansion of the shadow bounds before blur decay (default 0.dp).
 */
@Composable
fun Modifier.skiaDropShadow(
    color: Color = Color.Black.copy(alpha = 0.55f),
    blurRadius: Dp = 32.dp,
    borderRadius: Dp = 34.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 8.dp,
    spread: Dp = 0.dp
): Modifier {
    val density = LocalDensity.current

    // Hoist native Skia Paint and MaskFilter allocations out of the 60fps draw path.
    // Reconstructs ONLY when color, blurRadius, or display density change.
    val paint = remember(color, blurRadius, density) {
        Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            val blurPx = with(density) { blurRadius.toPx() }
            val sigma = blurPx * 0.5f // Gaussian kernel standard deviation: sigma = R / 2.0f
            if (sigma > 0f) {
                this.maskFilter = MaskFilter.makeBlur(
                    mode = FilterBlurMode.NORMAL,
                    sigma = sigma,
                    respectCTM = true
                )
            }
        }
    }

    return this.drawBehind {
        val dx = offsetX.toPx()
        val dy = offsetY.toPx()
        val sp = spread.toPx()
        val rPx = borderRadius.toPx()

        drawIntoCanvas { canvas ->
            val rrect = RRect.makeLTRB(
                dx - sp,
                dy - sp,
                size.width + dx + sp,
                size.height + dy + sp,
                rPx
            )
            canvas.nativeCanvas.drawRRect(rrect, paint)
        }
    }
}
