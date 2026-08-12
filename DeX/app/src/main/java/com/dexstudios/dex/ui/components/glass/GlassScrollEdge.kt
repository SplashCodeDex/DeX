package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/** Which scroll edge a [GlassScrollEdge] fades. */
enum class GlassEdge { Top, Bottom }

/**
 * Frosted edge fade for scrolling content — the content progressively blurs as
 * it approaches the edge (top: under the floating header, bottom: above the
 * floating navbar). Samples the supplied [backdrop]; the caller sizes and aligns
 * it (e.g. `Modifier.align(TopCenter).fillMaxWidth().height(64.dp)`).
 *
 * Implemented with an alpha-masked blur shader (the library's documented
 * progressive-blur technique): a blur + AGSL shader that fades the blurred
 * content's alpha from opaque at the edge to transparent away from it.
 */
@Composable
fun GlassScrollEdge(
    backdrop: Backdrop,
    edge: GlassEdge,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 6.dp,
    // Dark tint matching the theme background so the frosted edge blends in
    // instead of glowing white.
    tint: Color = MaterialTheme.colorScheme.background,
    tintIntensity: Float = 0.5f,
) {
    Box(
        modifier = modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                blur(blurRadius.toPx())
                runtimeShaderEffect(
                    key = "glassScrollEdge",
                    shaderString = GlassEdgeShader,
                    uniformShaderName = "content",
                    block = {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tint)
                        setFloatUniform("tintIntensity", tintIntensity)
                        setFloatUniform("topOpaque", if (edge == GlassEdge.Top) 1f else 0f)
                    }
                )
            }
        )
    )
}

private const val GlassEdgeShader = """
    uniform shader content;

    uniform float2 size;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;
    uniform float topOpaque;

    half4 main(float2 coord) {
        float progress = smoothstep(size.y, size.y * 0.5, coord.y);
        float alpha = mix(1.0 - progress, progress, topOpaque);
        return mix(content.eval(coord) * alpha, tint * alpha, tintIntensity);
    }
"""
