package com.dexstudios.dex.core.designsystem.components.island

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Bounds and layout dimensions configuration for dynamic morphing surfaces.
 *
 * @param collapsedWidth Width when collapsed at rest.
 * @param collapsedHeight Height when collapsed at rest.
 * @param expandedWidth Width when expanded. Use [Dp.Unspecified] to stretch dynamically to container width.
 * @param expandedHeight Height when expanded.
 */
data class DynamicDimensions(val collapsedWidth: Dp = 56.dp, val collapsedHeight: Dp = 56.dp, val expandedWidth: Dp = 260.dp, val expandedHeight: Dp = 56.dp) {
    companion object {
        /** Default dimensions for stadium pill buttons. */
        val PillDefault = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            expandedWidth = 260.dp,
            expandedHeight = 56.dp,
        )

        /** Default dimensions for dynamic island card surfaces. */
        val IslandDefault = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            expandedWidth = Dp.Unspecified,
            expandedHeight = 160.dp,
        )

        /** Default dimensions for compact action pills. */
        val CompactPill = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            expandedWidth = 130.dp,
            expandedHeight = 56.dp,
        )

        /** Default dimensions for profile identity stadium pill. */
        val ProfilePill = DynamicDimensions(
            collapsedWidth = 56.dp,
            collapsedHeight = 56.dp,
            expandedWidth = 210.dp,
            expandedHeight = 56.dp,
        )
    }
}

/**
 * Organic spring kinematics configuration for bounds morphing (expansion and collapse).
 *
 * Encapsulates direction-aware dual damping:
 * - [expandDampingRatio]: Target damping ratio when opening (overshoot spring).
 * - [collapseDampingRatio]: Target damping ratio when closing (cleaner settle).
 * - [stiffness]: Response speed and elastic force.
 * - [springSpec]: Optional explicit animation spec override.
 */
data class DynamicMotionConfig(val expandDampingRatio: Float = 0.50f, val collapseDampingRatio: Float = 0.56f, val stiffness: Float = 170f, val springSpec: AnimationSpec<Dp>? = null) {
    /** Resolves active direction-aware spring specification based on [isExpanded]. */
    fun resolveSpringSpec(isExpanded: Boolean): AnimationSpec<Dp> = springSpec ?: spring(
        dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
        stiffness = stiffness,
    )

    /** Direction-aware generic spring spec for any type (Float, Dp, Color, Int, etc.). */
    fun <T> springSpec(isExpanded: Boolean): AnimationSpec<T> = spring(
        dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
        stiffness = stiffness,
    )

    /** Standard expand spring spec. */
    fun <T> expandSpec(): AnimationSpec<T> = spring(
        dampingRatio = expandDampingRatio,
        stiffness = stiffness,
    )

    /** Standard collapse spring spec. */
    fun <T> collapseSpec(): AnimationSpec<T> = spring(
        dampingRatio = collapseDampingRatio,
        stiffness = stiffness,
    )

    companion object {
        /** CodeDeX signature fluid overshoot kinematics defaults. */
        val Default = DynamicMotionConfig()

        /** Playful high-bouncy kinematics for expressive micro-interactions. */
        val Bouncy = DynamicMotionConfig(
            expandDampingRatio = 0.50f,
            collapseDampingRatio = 0.65f,
            stiffness = 350f,
        )

        /** Snappy responsive kinematics with minimal overshoot for productivity surfaces. */
        val Snappy = DynamicMotionConfig(
            expandDampingRatio = 0.75f,
            collapseDampingRatio = 0.85f,
            stiffness = 600f,
        )
    }
}

/**
 * Tactile touch, press deformation, and drag-tracking physics configuration.
 *
 * @param enabled Whether bubble fluidity physics are enabled.
 * @param pressScale Squish (< 1.0f) or swell (> 1.0f) scale multiplier on press.
 * @param pullFactor Finger/mouse drag displacement tracking factor.
 * @param elasticity Jelly stretch rebound and harmonic oscillation factor.
 * @param scalePressSpeed Response speed of touch-down scaling.
 * @param scalePressDamping Smoothness and damping of touch-down parallax scaling.
 * @param scaleSettleSpeed Release-to-settle spring speed while drag pull oscillation stays locked.
 */
data class DynamicFluidityConfig(
    val enabled: Boolean = true,
    val pressScale: Float = 1.08f,
    val pullFactor: Float = 0.14f,
    val elasticity: Float = 0.13f,
    val scalePressSpeed: Float = 288f,
    val scalePressDamping: Float = 0.35f,
    val scaleSettleSpeed: Float = 300f,
) {
    companion object {
        /** Authentic tactile fluidity defaults. */
        val Default = DynamicFluidityConfig()

        /** Subtle, minimal tactile deformation. */
        val Subtle = DynamicFluidityConfig(
            pressScale = 0.95f,
            pullFactor = 0.10f,
            elasticity = 0.30f,
        )

        /** Highly expressive fluid jelly kinematics. */
        val HighBouncy = DynamicFluidityConfig(
            pressScale = 0.85f,
            pullFactor = 0.25f,
            elasticity = 0.75f,
        )

        /** Completely disabled bubble fluidity. */
        val Disabled = DynamicFluidityConfig(enabled = false)
    }
}

/**
 * Physics configuration for the expanded state of dynamic islands and pills.
 *
 * @param enabled Master toggle: if false, the expanded surface is completely exempt from bubble fluidity.
 * @param pressScale Press squish/swell scale when expanded (null = inherit collapsed [pressScale]).
 * @param pullFactor Drag pull tracking factor when expanded (null = inherit collapsed [pullFactor]).
 * @param elasticity Jelly stretch elasticity when expanded (null = inherit collapsed [elasticity]).
 * @param scalePressSpeed Press parallax response speed when expanded.
 * @param scalePressDamping Press parallax smoothness / damping when expanded.
 * @param scaleSettleSpeed Release-to-settle spring speed when expanded.
 */
data class ExpandedBubbleFluidityConfig(
    val enabled: Boolean = true,
    val pressScale: Float? = null,
    val pullFactor: Float? = null,
    val elasticity: Float? = null,
    val scalePressSpeed: Float? = null,
    val scalePressDamping: Float? = null,
    val scaleSettleSpeed: Float? = null,
) {
    companion object {
        /** Completely exempt from all expanded bubble fluidity. */
        val Exempt = ExpandedBubbleFluidityConfig(
            enabled = false,
            pressScale = 1.0f,
            pullFactor = 0.0f,
            elasticity = 0.0f,
        )

        /** Inherits all collapsed bubble fluidity properties. */
        val Inherit = ExpandedBubbleFluidityConfig(
            enabled = true,
            pressScale = null,
            pullFactor = null,
            elasticity = null,
        )
    }
}

/**
 * Pure Compose Multiplatform shadow properties.
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

    companion object {
        val None = LiquidGlassShadowProperties()
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
        val ExpandedSearch = LiquidGlassShadowProperties(
            radius = 33.dp,
            color = Color.Black,
            alpha = 0.35f,
            offset = DpOffset(0.dp, 36.dp),
            innerRadius = 10.dp,
            innerColor = Color.Black,
            innerAlpha = 0.20f,
            innerOffset = DpOffset(0.dp, 2.dp),
        )
    }
}

/**
 * Grouped shadow configuration containing both unexpanded and expanded shadow variants.
 */
data class DynamicShadowVariants(
    val unexpanded: LiquidGlassShadowProperties = LiquidGlassShadowProperties.Unexpanded,
    val expanded: LiquidGlassShadowProperties = LiquidGlassShadowProperties.Expanded,
) {
    companion object {
        val Default = DynamicShadowVariants()
        val SearchIsland = DynamicShadowVariants(
            unexpanded = LiquidGlassShadowProperties.Unexpanded,
            expanded = LiquidGlassShadowProperties.ExpandedSearch,
        )
        val None = DynamicShadowVariants(LiquidGlassShadowProperties.None, LiquidGlassShadowProperties.None)
    }
}

/**
 * Surface color and tint configuration for dynamic morphing buttons across resting and expanded states.
 */
data class DynamicColorVariants(val restingColor: Color = Color(0xFF121214), val restingAlpha: Float = 0.85f, val expandedColor: Color = Color(0xFF1E1E24), val expandedAlpha: Float = 0.88f) {
    companion object {
        val Default = DynamicColorVariants()
        val Midnight = DynamicColorVariants(
            restingColor = Color(0xFF000000),
            restingAlpha = 0.95f,
            expandedColor = Color(0xFF0A0A0E),
            expandedAlpha = 0.92f,
        )
        val Slate = DynamicColorVariants(
            restingColor = Color(0xFF0F172A),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF1E293B),
            expandedAlpha = 0.88f,
        )
        val FrostedWhite = DynamicColorVariants(
            restingColor = Color(0xFFFFFFFF),
            restingAlpha = 0.70f,
            expandedColor = Color(0xFFF8FAFC),
            expandedAlpha = 0.85f,
        )
        val Indigo = DynamicColorVariants(
            restingColor = Color(0xFF3730A3),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF312E81),
            expandedAlpha = 0.90f,
        )
        val Emerald = DynamicColorVariants(
            restingColor = Color(0xFF064E3B),
            restingAlpha = 0.85f,
            expandedColor = Color(0xFF065F46),
            expandedAlpha = 0.90f,
        )
    }
}

/**
 * Transient optical content blur configuration applied during morphing state transitions.
 *
 * @param enabled Master toggle for transient content blur.
 * @param maxBlur Peak blur radius reached during the state transition.
 * @param blurOnExpand Whether blur is applied when expanding outward.
 * @param blurOnCollapse Whether blur is applied when collapsing inward.
 * @param riseDurationMillis Duration in milliseconds for the blur to reach peak intensity.
 */
data class DynamicContentBlurConfig(val enabled: Boolean = true, val maxBlur: Dp = 8.dp, val blurOnExpand: Boolean = true, val blurOnCollapse: Boolean = true, val riseDurationMillis: Int = 75) {
    /** Standard rise animation spec for transient content blur. */
    val riseAnimationSpec: AnimationSpec<Float>
        get() = tween(durationMillis = riseDurationMillis, easing = FastOutSlowInEasing)

    companion object {
        val Default = DynamicContentBlurConfig()
        val Subtle = DynamicContentBlurConfig(maxBlur = 5.dp, riseDurationMillis = 60)
        val Cinematic = DynamicContentBlurConfig(maxBlur = 14.dp, riseDurationMillis = 90)
        val Disabled = DynamicContentBlurConfig(enabled = false, maxBlur = 0.dp)
    }
}

/**
 * Anchor alignment for dynamic expansion specifying the directional origin of the motion.
 */
enum class ExpansionAnchor {
    Start,
    End,
    Center,
}

/**
 * Organic anticipation and parallax overshoot scaling configuration for buttons and surfaces.
 */
data class DynamicAnticipationConfig(
    val enabled: Boolean = true,
    val nudgeDistance: Dp = 32.dp,
    val squishScale: Float = 1.25f,
    val anticipationDurationMillis: Int = 70,
    val overshootVelocityMultiplier: Float = -20f,
    val nudgeSpeed: Float = 350f,
) {
    companion object {
        val Default = DynamicAnticipationConfig()
        val Disabled = DynamicAnticipationConfig(enabled = false)
    }
}

/**
 * Configuration for the dynamic edge-aware offset.
 */
data class DynamicButtonExpansionOffsetConfig(val enabled: Boolean = false, val fadeInDurationMillis: Int = 180) {
    companion object {
        val Default = DynamicButtonExpansionOffsetConfig()
        val Enabled = DynamicButtonExpansionOffsetConfig(enabled = true)
        val Disabled = DynamicButtonExpansionOffsetConfig(enabled = false)
    }
}

/**
 * Unified composite configuration for dynamic morphing surfaces.
 */
data class DynamicPillConfig(
    val dimensions: DynamicDimensions = DynamicDimensions.PillDefault,
    val motion: DynamicMotionConfig = DynamicMotionConfig.Default,
    val fluidity: DynamicFluidityConfig = DynamicFluidityConfig.Default,
    val expandedFluidity: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit,
    val shadows: DynamicShadowVariants = DynamicShadowVariants.Default,
    val colors: DynamicColorVariants = DynamicColorVariants.Default,
    val contentBlur: DynamicContentBlurConfig = DynamicContentBlurConfig.Default,
    val anticipation: DynamicAnticipationConfig = DynamicAnticipationConfig.Default,
    val buttonExpansionOffset: DynamicButtonExpansionOffsetConfig = DynamicButtonExpansionOffsetConfig.Default,
) {
    companion object {
        val Default = DynamicPillConfig()
    }
}
