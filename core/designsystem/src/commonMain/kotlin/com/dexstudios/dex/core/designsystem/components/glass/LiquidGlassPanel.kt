package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * A liquid glass panel — the glass counterpart to [com.dexstudios.dex.core.designsystem.components.DeXPanel].
 *
 * When [backdrop] is provided, samples the backdrop and draws a frosted glass surface
 * with real-time SkSL GPU shaders (blur, lens, vibrancy, highlight, shadow).
 * When [backdrop] is null, falls back cleanly to a clipped translucent tinted surface.
 */
@Composable
fun LiquidGlassPanel(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = DeXGlassPresets.DockCardDark.shape,
    config: LiquidGlassConfig = DeXGlassPresets.DockCardDark,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassModifier = if (backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (config.vibrancyEnabled) vibrancy()
                blur(config.blurRadius.toPx())
                if ((config.lensHeight > 0.dp) && (config.lensAmount > 0.dp)) {
                    lens(
                        refractionHeight = config.lensHeight.toPx(),
                        refractionAmount = config.lensAmount.toPx(),
                        depthEffect = config.depthEffect,
                        chromaticAberration = config.chromaticAberration,
                    )
                }
            },
            highlight = { config.highlight },
            shadow = { Shadow(radius = config.shadowRadius, color = config.shadowColor) },
            innerShadow = { config.innerShadow },
            onDrawSurface = {
                if (config.surfaceTint.isSpecified && config.surfaceTintAlpha > 0f) {
                    drawRect(config.surfaceTint.copy(alpha = config.surfaceTintAlpha))
                }
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(
                if (config.surfaceTint.isSpecified && config.surfaceTintAlpha > 0f)
                    config.surfaceTint.copy(alpha = config.surfaceTintAlpha)
                else Color(0xFF16121A).copy(alpha = 0.82f)
            )
    }

    Box(
        modifier = glassModifier,
        content = content
    )
}
