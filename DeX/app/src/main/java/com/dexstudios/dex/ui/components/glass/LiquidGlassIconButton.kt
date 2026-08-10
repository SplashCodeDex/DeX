package com.dexstudios.dex.ui.components.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * A reusable circular liquid-glass icon button.
 *
 * Samples a [backdrop] supplied by the caller (the real content behind the
 * button) and draws the liquid glass effect on press/interaction. No gradient
 * or wallpaper is injected into the button — it reflects whatever it samples.
 * When no [backdrop] is provided it falls back to a plain translucent surface
 * so it never draws "empty" glass.
 *
 * @param onClick invoked when the button is tapped.
 * @param width the bounding width of the button.
 * @param height the bounding height of the button.
 * @param config all glass styling knobs (shape, blur, lens, tint, highlights).
 * @param backdrop the content the glass samples; null renders a plain surface.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 56.dp,
    config: LiquidGlassConfig = LiquidGlassConfig(),
    backdrop: Backdrop? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "liquidPress"
    )

    val glassModifier = if (backdrop != null) {
        Modifier
            .size(width, height)
            .bubbleFluidity()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { config.shape },
                effects = {
                    if (config.vibrancyEnabled) vibrancy()
                    blur((config.blurRadius * (1f - 0.5f * pressProgress)).toPx())
                    if (config.lensHeight > 0.dp && config.lensAmount > 0.dp) {
                        // Resting refraction base + press boost
                        val refraction = config.restRefraction + (1f - config.restRefraction) * pressProgress
                        lens(
                            refractionHeight = (config.lensHeight * refraction).toPx(),
                            refractionAmount = (config.lensAmount * refraction).toPx(),
                            depthEffect = config.depthEffect,
                            chromaticAberration = config.chromaticAberration
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
        Modifier
            .size(width, height)
            .bubbleFluidity()
            .clip(config.shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), config.shape)
    }

    Box(
        modifier = glassModifier
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
