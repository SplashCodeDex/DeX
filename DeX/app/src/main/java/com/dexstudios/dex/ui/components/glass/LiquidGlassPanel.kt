package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * A liquid glass panel — the glass counterpart to [com.dexstudios.dex.ui.components.DeXPanel].
 *
 * Samples the supplied [backdrop] (the real content behind the panel) and draws
 * a frosted glass surface with optional refraction, highlight, shadow and tint,
 * all driven by [config]. Reusable for nav bars, sheets, cards, dialogs, etc.
 *
 * Note: the caller's `modifier` is applied before [drawBackdrop], so sizing
 * (fillMaxWidth/height) must be present in it — matching `DeXPanel` usage.
 */
@Composable
fun LiquidGlassPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = LiquidGlassPresets.Frosted.shape,
    config: LiquidGlassConfig = LiquidGlassPresets.Frosted,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
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
        ),
        content = content
    )
}
