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
    val BlurRadius = 5.dp
    val RestRefraction = 0.8f
    val GlareAngle = -52.82f
    val GlareFalloff = 2.5f
    val GlareFactor = 0.95f
    val GlareRestAlpha = 0.80f

    val SheetGlareAngle = -52.82f
    val SheetGlareFalloff = 3.5f
    val SheetGlareFactor = 0.3f

    val SurfaceTint = Color.Black
    val DarkTintAlpha = 0.23f
    val ChromaticAberration = false
    val DepthEffect = true

    val InnerShadowRadius = 6.dp
    val InnerShadowOffset = DpOffset(x = 2.dp, y = 23.dp)
    val InnerShadowColor = Color.Black
    val InnerShadowAlpha = 0f

    // Centralized Deep Drop Shadow tokens (Search Island & Floating NavBar)
    val ExpandedSearchShadowRadius = 33.dp
    val ExpandedSearchShadowOffset = DpOffset(0.dp, 36.dp)
    val ExpandedSearchShadowColor = Color.Black.copy(alpha = 0.25f)
}

/**
 * Complete shadow parameter configuration encapsulating both outer drop shadow
 * and inner rim shadow for liquid glass surfaces.
 *
 * Includes all shadow parameters across both drop shadows and inner shadows:
 * - Drop Shadow: [radius], [color], [alpha], [offset] ([offsetX], [offsetY])
 * - Inner Shadow: [innerRadius], [innerColor], [innerAlpha], [innerOffset] ([innerOffsetX], [innerOffsetY])
 *
 * @param radius Outer drop shadow blur radius (0.dp disables drop shadow).
 * @param color Outer drop shadow base color.
 * @param alpha Outer drop shadow opacity (0f..1f).
 * @param offset Outer drop shadow translation offset (x, y).
 * @param innerRadius Inner shadow blur radius (0.dp disables inner shadow).
 * @param innerColor Inner shadow base color.
 * @param innerAlpha Inner shadow opacity (0f..1f).
 * @param innerOffset Inner shadow translation offset (x, y).
 */
data class LiquidGlassShadowProperties(
    val radius: Dp = 0.dp,
    val color: Color = Color.Black,
    val alpha: Float = 0.20f,
    val offset: DpOffset = DpOffset.Zero,
    val innerRadius: Dp = 0.dp,
    val innerColor: Color = Color.Black,
    val innerAlpha: Float = 0f,
    val innerOffset: DpOffset = DpOffset.Zero,
) {
    val offsetX: Dp get() = offset.x
    val offsetY: Dp get() = offset.y
    val innerOffsetX: Dp get() = innerOffset.x
    val innerOffsetY: Dp get() = innerOffset.y

    constructor(
        radius: Dp = 0.dp,
        color: Color = Color.Black,
        alpha: Float = 0.20f,
        offsetX: Dp = 0.dp,
        offsetY: Dp = 0.dp,
        innerRadius: Dp = 0.dp,
        innerColor: Color = Color.Black,
        innerAlpha: Float = 0f,
        innerOffsetX: Dp = 0.dp,
        innerOffsetY: Dp = 0.dp,
    ) : this(
        radius = radius,
        color = color,
        alpha = alpha,
        offset = DpOffset(offsetX, offsetY),
        innerRadius = innerRadius,
        innerColor = innerColor,
        innerAlpha = innerAlpha,
        innerOffset = DpOffset(innerOffsetX, innerOffsetY),
    )

    /** Resolved Backdrop outer drop shadow, or null if disabled. */
    val dropShadow: Shadow?
        get() = if (radius > 0.dp && alpha > 0f) {
            Shadow(
                radius = radius,
                color = color.copy(alpha = alpha),
                offset = offset
            )
        } else null

    /** Resolved Backdrop inner shadow, or null if disabled. */
    val innerShadow: InnerShadow?
        get() = if (innerRadius > 0.dp && innerAlpha > 0f) {
            InnerShadow(
                radius = innerRadius,
                offset = innerOffset,
                color = innerColor,
                alpha = innerAlpha
            )
        } else null

    companion object {
        /** Unexpanded (resting/collapsed) shadow variant. CodeDeX tuned elevation with volumetric glass bezel. */
        val Unexpanded = LiquidGlassShadowProperties(
            radius = 23.dp,
            color = Color.Black,
            alpha = 0.28f,
            offset = DpOffset(0.dp, 18.dp),
            innerRadius = 10.dp,
            innerColor = Color.Black,
            innerAlpha = 0.22f,
            innerOffset = DpOffset(0.dp, 2.dp),
        )

        /** Expanded shadow variant. CodeDeX tuned deep atmospheric floating sheet shadow. */
        val Expanded = LiquidGlassShadowProperties(
            radius = 28.dp,
            color = Color.Black,
            alpha = 0.20f,
            offset = DpOffset(0.dp, 24.dp),
            innerRadius = 12.dp,
            innerColor = Color.Black,
            innerAlpha = 0.11f,
            innerOffset = DpOffset(0.dp, 7.dp),
        )

        /** Search Island expanded shadow variant. Matches signature search island deep elevation. */
        val ExpandedSearch = LiquidGlassShadowProperties(
            radius = LiquidGlassTokens.ExpandedSearchShadowRadius,
            color = Color.Black,
            alpha = 0.25f,
            offset = LiquidGlassTokens.ExpandedSearchShadowOffset,
            innerRadius = LiquidGlassTokens.InnerShadowRadius,
            innerColor = LiquidGlassTokens.InnerShadowColor,
            innerAlpha = LiquidGlassTokens.InnerShadowAlpha,
            innerOffset = LiquidGlassTokens.InnerShadowOffset,
        )

        /** Flat / disabled shadows (for nested or flat glass elements). */
        val None = LiquidGlassShadowProperties(
            radius = 0.dp,
            alpha = 0f,
            innerRadius = 0.dp,
            innerAlpha = 0f,
        )
    }
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
    val glareFalloff: Float = LiquidGlassTokens.GlareFalloff,
    val glareFactor: Float = LiquidGlassTokens.GlareFactor * 100f,
    val glareRestAlpha: Float = LiquidGlassTokens.GlareRestAlpha,
    val glareAngle: Float = LiquidGlassTokens.GlareAngle,
    val useAmbientHighlight: Boolean = false,
    val shadowRadius: Dp = 0.dp,
    val shadowColor: Color = Color.Black.copy(alpha = 0.2f),
    val shadowOffset: DpOffset = DpOffset.Zero,
    val innerShadowRadius: Dp = LiquidGlassTokens.InnerShadowRadius,
    val innerShadowOffset: DpOffset = LiquidGlassTokens.InnerShadowOffset,
    val innerShadowColor: Color = LiquidGlassTokens.InnerShadowColor,
    val innerShadowAlpha: Float = LiquidGlassTokens.InnerShadowAlpha,
    val shadowProperties: LiquidGlassShadowProperties? = null,
) {
    val highlight: Highlight
        get() = if (useAmbientHighlight) {
            Highlight.Ambient
        } else {
            Highlight(
                style = HighlightStyle.Default(
                    angle = glareAngle,
                    falloff = glareFalloff
                ),
                alpha = glareFactor / 100f
            )
        }

    /**
     * Computes dynamic specular highlight reacting to physical touch compression [progress] (0f = rest, 1f = compressed).
     *
     * In authentic Apple glass optics, physical compression concentrates light along the incident rim:
     * - Specular glare alpha flares from [glareRestAlpha] up to [glareFactor] / 100f.
     * - Falloff tightens slightly, sharpening the rim reflection.
     */
    fun dynamicHighlight(progress: Float): Highlight {
        if (useAmbientHighlight) return Highlight.Ambient
        val peakAlpha = glareFactor / 100f
        val baseAlpha = glareRestAlpha.coerceAtMost(peakAlpha)
        val animatedAlpha = (baseAlpha + (peakAlpha - baseAlpha) * progress).coerceIn(0f, 1f)
        val animatedFalloff = glareFalloff * (1f + 0.25f * progress.coerceIn(0f, 1.5f))
        return Highlight(
            style = HighlightStyle.Default(
                angle = glareAngle,
                falloff = animatedFalloff
            ),
            alpha = animatedAlpha
        )
    }

    /** Resolved outer drop shadow, delegating to [shadowProperties] when present or fallback fields. */
    val dropShadow: Shadow?
        get() = shadowProperties?.dropShadow ?: if (shadowRadius > 0.dp) {
            Shadow(
                radius = shadowRadius,
                color = shadowColor,
                offset = shadowOffset
            )
        } else null

    /** Resolved inner shadow, delegating to [shadowProperties] when present or fallback fields. */
    val innerShadow: InnerShadow?
        get() = shadowProperties?.innerShadow ?: if (innerShadowAlpha > 0f || innerShadowRadius > 0.dp) {
            InnerShadow(
                radius = innerShadowRadius,
                offset = innerShadowOffset,
                color = innerShadowColor,
                alpha = innerShadowAlpha
            )
        } else null

    /** Returns a copy with updated [LiquidGlassShadowProperties] and synchronized legacy fields. */
    fun withShadowProperties(properties: LiquidGlassShadowProperties): LiquidGlassConfig = copy(
        shadowProperties = properties,
        shadowRadius = properties.radius,
        shadowColor = properties.color.copy(alpha = properties.alpha),
        shadowOffset = properties.offset,
        innerShadowRadius = properties.innerRadius,
        innerShadowOffset = properties.innerOffset,
        innerShadowColor = properties.innerColor,
        innerShadowAlpha = properties.innerAlpha
    )
}

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
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.35f
            )
        }

    /**
     * Dedicated preset for the Search button (collapsed).
     */
    val SearchIconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = CircleShape,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.35f
            ).withShadowProperties(LiquidGlassShadowProperties.Unexpanded)
        }

    /**
     * Dedicated preset for the Profile button (collapsed).
     */
    val ProfileIconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.75f
            ).withShadowProperties(LiquidGlassShadowProperties.Unexpanded)
        }

    /**
     * Dedicated preset for the History button (collapsed).
     */
    val HistoryIconButton: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = CircleShape,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.35f
            ).withShadowProperties(LiquidGlassShadowProperties.Unexpanded)
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
                shape = CircleShape,
                blurRadius = 2.dp,
                restRefraction = 1.05f,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.5f
            ).withShadowProperties(LiquidGlassShadowProperties.Expanded)
        }

    /**
     * Dedicated preset for the Profile island (expanded).
     */
    val ProfileIsland: LiquidGlassConfig
        @Composable get() {
            val isDark = isSystemInDarkTheme()
            return MasterSpec.copy(
                shape = RoundedCornerShape(48.dp),
                blurRadius = 24.dp,
                restRefraction = 1.05f,
                surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                surfaceTintAlpha = if (isDark) LiquidGlassTokens.DarkTintAlpha else 0.45f
            ).withShadowProperties(LiquidGlassShadowProperties.Expanded)
        }

    /**
     * Theme-aware preset for the floating bottom navigation bar.
     */
    val NavBar: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                shadowRadius = LiquidGlassTokens.ExpandedSearchShadowRadius,
                shadowOffset = LiquidGlassTokens.ExpandedSearchShadowOffset,
                shadowColor = LiquidGlassTokens.ExpandedSearchShadowColor,
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
                useAmbientHighlight = true,
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
                useAmbientHighlight = true,
                shadowRadius = 8.dp,
                shadowColor = Color.Black.copy(alpha = 0.2f)
            )
        )

    /**
     * Dedicated preset for the navigation bottom sheet.
     */
    val Sheet: LiquidGlassConfig
        @Composable get() = resolve(
            MasterSpec.copy(
                glareAngle = LiquidGlassTokens.SheetGlareAngle,
                glareFalloff = LiquidGlassTokens.SheetGlareFalloff,
                glareFactor = LiquidGlassTokens.SheetGlareFactor * 100f
            )
        )
}
