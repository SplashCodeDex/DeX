package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Core optical and physics tokens derived from iterative tuning
 * (Matching liquid-glass-2026-08-17T18-35-42.json)
 */
object LiquidGlassTokens {
    val LensHeight = 30.dp
    val LensAmount = 35.dp
    val BlurRadius = 1.dp
    val RestRefraction = 0.5f
    val GlareAngle = -52.82f
    val GlareHardness = 20f
    val GlareFactor = 0.78f
    val GlareWidth = 1.dp
    val GlareRestAlpha = 0.35f
    val SurfaceTint = Color.Black
    val DarkTintAlpha = 0.23f
    val ChromaticAberration = false
    val DepthEffect = true

    // Advanced spec fields
    val FresnelRange = 64.56f
    val FresnelHardness = 21.58f
    val FresnelFactor = 7.5f
}

/**
 * Central, reusable configuration for a liquid glass surface.
 */
data class LiquidGlassConfig(
    val shape: Shape = CircleShape,
    val blurRadius: Dp = LiquidGlassTokens.BlurRadius,
    val lensHeight: Dp = LiquidGlassTokens.LensHeight,
    val lensAmount: Dp = LiquidGlassTokens.LensAmount,
    val vibrancyEnabled: Boolean = false,
    val chromaticAberration: Boolean = LiquidGlassTokens.ChromaticAberration,
    val depthEffect: Boolean = LiquidGlassTokens.DepthEffect,
    val restRefraction: Float = LiquidGlassTokens.RestRefraction,
    val surfaceTint: Color = LiquidGlassTokens.SurfaceTint,
    val surfaceTintAlpha: Float = LiquidGlassTokens.DarkTintAlpha,
    val highlight: Highlight = Highlight(
        style = HighlightStyle.Default(
            angle = LiquidGlassTokens.GlareAngle,
            falloff = LiquidGlassTokens.GlareHardness / 10f
        ),
        alpha = LiquidGlassTokens.GlareFactor
    ),
    val shadowRadius: Dp = 0.dp,
    val shadowColor: Color = Color.Black.copy(alpha = 0.2f),
    val shadowOffset: DpOffset = DpOffset.Zero,
    val innerShadow: InnerShadow? = null,

    // Advanced shader parameters
    val refFresnelRange: Float = LiquidGlassTokens.FresnelRange,
    val refFresnelHardness: Float = LiquidGlassTokens.FresnelHardness,
    val refFresnelFactor: Float = LiquidGlassTokens.FresnelFactor,
    val glareRange: Float = 0f,
    val glareHardness: Float = LiquidGlassTokens.GlareHardness,
    val glareFactor: Float = LiquidGlassTokens.GlareFactor * 100f,
    val glareAngle: Float = LiquidGlassTokens.GlareAngle
)

/**
 * Curated glass presets. Every preset inherits from the Master Tokens
 * and automatically adapts to Light/Dark themes.
 */
object LiquidGlassPresets {

    /**
     * Master source of truth for the "Liquid" look.
     */
    private val MasterSpec = LiquidGlassConfig()

    /**
     * Internal helper to resolve theme-aware tinting.
     */
    @Composable
    private fun resolve(config: LiquidGlassConfig): LiquidGlassConfig {
        return if (isSystemInDarkTheme()) config else config.copy(surfaceTintAlpha = 0f)
    }

    /**
     * Crisp glass tuned for top-bar icon buttons.
     */
    val IconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shadowRadius = 4.dp,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.75f
            )
        }

    /**
     * Dedicated preset for the Search button (collapsed).
     */
    val SearchIconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shadowRadius = 4.dp,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.75f
            )
        }

    /**
     * Dedicated preset for the Profile button (collapsed).
     */
    val ProfileIconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shadowRadius = 4.dp,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.75f
            )
        }

    /**
     * Theme-aware preset for the expanded Dynamic Island look.
     */
    val DynamicIsland: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 25.dp,
                restRefraction = 1.05f,
                shadowRadius = 12.dp,
                shadowOffset = DpOffset(0.dp, 4.dp),
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.45f
            )
        }

    /**
     * Dedicated preset for the Search island (expanded).
     */
    val SearchIsland: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 2.dp,
                restRefraction = 1.05f,
                shadowRadius = 33.dp,
                shadowOffset = DpOffset(0.dp, 36.dp),
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.5f
            )
        }

    /**
     * Dedicated preset for the Profile island (expanded).
     */
    val ProfileIsland: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 2.dp,
                restRefraction = 1.05f,
                shadowRadius = 12.dp,
                shadowOffset = DpOffset(0.dp, 4.dp),
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.45f
            )
        }

    /**
     * Theme-aware preset for the floating bottom navigation bar.
     */
    val NavBar: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                blurRadius = 5.dp,
                restRefraction = 0.8f,
                shadowRadius = 0.dp,
                shadowColor = Color.Black.copy(alpha = 0.6f)
            )
        )

    /**
     * Theme-aware preset for cards with a shiny border but no refraction.
     */
    val ShinyCard: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                lensHeight = 0.dp,
                lensAmount = 0.dp,
                blurRadius = 0.dp,
                restRefraction = 0f
            )
        )

    /**
     * Theme-aware preset for a heavier, more matte look suited to generic panels.
     */
    val Frosted: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                blurRadius = 1.dp,
                restRefraction = 1.0f
            )
        )

    /**
     * Theme-aware preset for dialogs and cards over a dimmed scene.
     */
    val Dialog: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 16.dp,
                restRefraction = 1.0f,
                shadowRadius = 16.dp,
                shadowColor = Color.Black.copy(alpha = 0.3f)
            )
        )

    /**
     * Minimalist flat glass with no lens distortion.
     */
    val Flat: LiquidGlassConfig
        @Composable get() = resolve(
            LiquidGlassConfig(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 24.dp,
                lensHeight = 0.dp,
                lensAmount = 0.dp,
                vibrancyEnabled = false,
                chromaticAberration = false,
                depthEffect = false,
                surfaceTint = Color.White,
                surfaceTintAlpha = 0.12f,
                highlight = Highlight.Ambient,
                shadowRadius = 12.dp,
                shadowColor = Color.Black.copy(alpha = 0.15f)
            )
        )

    /**
     * Interactive version of the flat glass.
     */
    val FlatInteractive: LiquidGlassConfig
        @Composable get() = resolve(
            LiquidGlassConfig(
                shape = CircleShape,
                blurRadius = 16.dp,
                lensHeight = 0.dp,
                lensAmount = 0.dp,
                vibrancyEnabled = false,
                chromaticAberration = false,
                depthEffect = false,
                surfaceTint = Color.White,
                surfaceTintAlpha = 0.15f,
                highlight = Highlight.Ambient,
                shadowRadius = 8.dp,
                shadowColor = Color.Black.copy(alpha = 0.2f)
            )
        )
}
