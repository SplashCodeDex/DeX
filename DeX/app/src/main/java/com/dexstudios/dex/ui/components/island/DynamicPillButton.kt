package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.kyant.backdrop.Backdrop

/**
 * Standard configuration defaults for [DynamicPillButton].
 */
object DynamicPillDefaults {

    /** Default content transition inside the pill. */
    val ContentTransition: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = {
        val enterSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
        val exitSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 500f)
        (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = enterSpec))
            .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.88f, animationSpec = exitSpec))
    }

    /** Grouped default configurations */
    val DimensionsDefault: DynamicDimensions = DynamicDimensions.PillDefault
    val MotionDefault: DynamicMotionConfig = DynamicMotionConfig.Default
    val FluidityDefault: DynamicFluidityConfig = DynamicFluidityConfig.Default
    val ExpandedFluidityDefault: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit
    val ShadowsDefault: DynamicShadowVariants = DynamicShadowVariants.Default
    val ColorsDefault: DynamicColorVariants = DynamicColorVariants.Default
    val ContentBlurDefault: DynamicContentBlurConfig = DynamicContentBlurConfig.Default
    val AnticipationDefault: DynamicAnticipationConfig = DynamicAnticipationConfig.Default

    /** Backward-compatibility shadow aliases */
    val UnexpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.unexpanded
    val ExpandedShadowDefault: LiquidGlassShadowProperties = ShadowsDefault.expanded
}

/**
 * Centralized, standard Dynamic Pill Button with organic overshoot spring kinematics.
 *
 * Unlike rectangular or squircle islands, this button maintains a strict **stadium pill geometry**
 * ([CircleShape]) across all expansion states. When expanding horizontally or vertically, both caps
 * remain continuous semicircles at every frame.
 *
 * Encapsulates:
 * 1. Compact resting state (circle / pill) with tactile squish/swell physics.
 * 2. Elongated pill state with perfectly rounded semicircular caps (never rectangular).
 * 3. Direction-aware dual damping: distinct damping ratios for expanding vs. collapsing.
 * 4. Fluid, synchronous bounds morphing (width and height) with overshoot springs.
 * 5. Outside-tap dismissal scrim.
 * 6. Grouped configuration settings for modular reuse ([DynamicDimensions], [DynamicMotionConfig],
 *    [DynamicFluidityConfig], [ExpandedBubbleFluidityConfig], [DynamicShadowVariants], [DynamicColorVariants],
 *    [DynamicContentBlurConfig]).
 * 7. Transient optical motion blur on slot contents during rapid morphing.
 * 8. 100% decoupled from project domain models via idiomatic Compose slots.
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dimensions: DynamicDimensions = DynamicPillDefaults.DimensionsDefault,
    motion: DynamicMotionConfig = DynamicPillDefaults.MotionDefault,
    fluidity: DynamicFluidityConfig = DynamicPillDefaults.FluidityDefault,
    expandedFluidityConfig: ExpandedBubbleFluidityConfig = DynamicPillDefaults.ExpandedFluidityDefault,
    shadows: DynamicShadowVariants = DynamicPillDefaults.ShadowsDefault,
    colors: DynamicColorVariants = DynamicPillDefaults.ColorsDefault,
    contentBlur: DynamicContentBlurConfig = DynamicPillDefaults.ContentBlurDefault,
    collapsedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIconButton.copy(shape = CircleShape),
    expandedGlassConfig: LiquidGlassConfig = LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    backdrop: Backdrop? = null,
    dismissOnOutsideTap: Boolean = true,
    contentTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = DynamicPillDefaults.ContentTransition,
    // Optional individual property overrides for granular inline tweaks
    collapsedWidth: Dp = dimensions.collapsedWidth,
    collapsedHeight: Dp = dimensions.collapsedHeight,
    expandedWidth: Dp = dimensions.expandedWidth,
    expandedHeight: Dp = dimensions.expandedHeight,
    expandDampingRatio: Float = motion.expandDampingRatio,
    collapseDampingRatio: Float = motion.collapseDampingRatio,
    stiffness: Float = motion.stiffness,
    morphSpringSpec: AnimationSpec<Dp>? = motion.springSpec,
    unexpandedShadow: LiquidGlassShadowProperties = shadows.unexpanded,
    expandedShadow: LiquidGlassShadowProperties = shadows.expanded,
    restingColor: Color = if (collapsedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTint else colors.restingColor,
    restingAlpha: Float = if (collapsedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) collapsedGlassConfig.surfaceTintAlpha else colors.restingAlpha,
    expandedColor: Color = if (expandedGlassConfig.surfaceTint.isSpecified && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTint else colors.expandedColor,
    expandedAlpha: Float = if (expandedGlassConfig.surfaceTintAlpha > 0f && colors == DynamicColorVariants.Default) expandedGlassConfig.surfaceTintAlpha else colors.expandedAlpha,
    enableBubbleFluidity: Boolean = fluidity.enabled,
    pressScale: Float = fluidity.pressScale,
    pullFactor: Float = fluidity.pullFactor,
    elasticity: Float = fluidity.elasticity,
    scalePressSpeed: Float = fluidity.scalePressSpeed,
    scalePressDamping: Float = fluidity.scalePressDamping,
    scaleSettleSpeed: Float = fluidity.scaleSettleSpeed,
    enableExpandedBubbleFluidity: Boolean = expandedFluidityConfig.enabled,
    expandedPressScale: Float = expandedFluidityConfig.pressScale ?: pressScale,
    expandedPullFactor: Float = expandedFluidityConfig.pullFactor ?: pullFactor,
    expandedElasticity: Float = expandedFluidityConfig.elasticity ?: elasticity,
    expandedScalePressSpeed: Float = expandedFluidityConfig.scalePressSpeed ?: scalePressSpeed,
    expandedScalePressDamping: Float = expandedFluidityConfig.scalePressDamping ?: scalePressDamping,
    expandedScaleSettleSpeed: Float = expandedFluidityConfig.scaleSettleSpeed ?: scaleSettleSpeed,
    enableContentBlur: Boolean = contentBlur.enabled,
    maxContentBlur: Dp = contentBlur.maxBlur,
    blurRiseDurationMillis: Int = contentBlur.riseDurationMillis,
    blurOnExpand: Boolean = contentBlur.blurOnExpand,
    blurOnCollapse: Boolean = contentBlur.blurOnCollapse,
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    anticipation: DynamicAnticipationConfig = DynamicPillDefaults.AnticipationDefault,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Anticipation directional nudge & dual-axis Poisson parallax overshoot scaling
    val activeMotionConfig = remember(expandDampingRatio, collapseDampingRatio, stiffness) {
        DynamicMotionConfig(
            expandDampingRatio = expandDampingRatio,
            collapseDampingRatio = collapseDampingRatio,
            stiffness = stiffness
        )
    }
    val activeFluidityConfig = remember(pressScale, pullFactor, elasticity, scalePressSpeed, scalePressDamping, scaleSettleSpeed) {
        DynamicFluidityConfig(
            pressScale = pressScale,
            pullFactor = pullFactor,
            elasticity = elasticity,
            scalePressSpeed = scalePressSpeed,
            scalePressDamping = scalePressDamping,
            scaleSettleSpeed = scaleSettleSpeed
        )
    }
    val anticipationState = rememberExpandingAnticipationPhysics(
        isExpanded = isExpanded,
        anchor = expansionAnchor,
        config = anticipation,
        motion = activeMotionConfig,
        fluidity = activeFluidityConfig
    )

    // Direction-aware spring spec: expandDampingRatio when opening, collapseDampingRatio when closing
    val activeSpringSpec = morphSpringSpec ?: remember(isExpanded, expandDampingRatio, collapseDampingRatio, stiffness) {
        spring(
            dampingRatio = if (isExpanded) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        )
    }

    // 1. Dynamic morphing dimensions with overshoot springs
    // During anticipation windup (~75ms), bounds remain at collapsed resting size while the button
    // squishes and nudges directionally. When anticipation completes, bounds expand outward to full target size.
    val shouldExpandBounds = isExpanded && (!anticipation.enabled || anticipationState.canExpandBounds)
    val currentWidth by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedWidth else collapsedWidth,
        animationSpec = activeSpringSpec,
        label = "pillMorphWidth"
    )
    val currentHeight by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedHeight else collapsedHeight,
        animationSpec = activeSpringSpec,
        label = "pillMorphHeight"
    )

    // Dynamic shadow properties: morph smoothly between unexpanded and expanded states
    val currentShadowRadius by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.radius else unexpandedShadow.radius,
        animationSpec = activeSpringSpec,
        label = "pillShadowRadius"
    )
    val currentShadowAlpha by animateFloatAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.alpha else unexpandedShadow.alpha,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillShadowAlpha"
    )
    val currentShadowColor by animateColorAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.color else unexpandedShadow.color,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillShadowColor"
    )
    val currentShadowOffsetX by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.offsetX else unexpandedShadow.offsetX,
        animationSpec = activeSpringSpec,
        label = "pillShadowOffsetX"
    )
    val currentShadowOffsetY by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.offsetY else unexpandedShadow.offsetY,
        animationSpec = activeSpringSpec,
        label = "pillShadowOffsetY"
    )

    val currentInnerShadowRadius by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.innerRadius else unexpandedShadow.innerRadius,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowRadius"
    )
    val currentInnerShadowAlpha by animateFloatAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.innerAlpha else unexpandedShadow.innerAlpha,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillInnerShadowAlpha"
    )
    val currentInnerShadowColor by animateColorAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.innerColor else unexpandedShadow.innerColor,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillInnerShadowColor"
    )
    val currentInnerShadowOffsetX by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.innerOffsetX else unexpandedShadow.innerOffsetX,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowOffsetX"
    )
    val currentInnerShadowOffsetY by animateDpAsState(
        targetValue = if (shouldExpandBounds) expandedShadow.innerOffsetY else unexpandedShadow.innerOffsetY,
        animationSpec = activeSpringSpec,
        label = "pillInnerShadowOffsetY"
    )

    // Dynamic surface color & tint: morph smoothly between resting and expanded states
    val currentSurfaceColor by animateColorAsState(
        targetValue = if (shouldExpandBounds) expandedColor else restingColor,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillSurfaceColor"
    )
    val currentSurfaceAlpha by animateFloatAsState(
        targetValue = if (shouldExpandBounds) expandedAlpha else restingAlpha,
        animationSpec = spring(
            dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
            stiffness = stiffness
        ),
        label = "pillSurfaceAlpha"
    )

    val currentShadowProperties = remember(
        currentShadowRadius,
        currentShadowColor,
        currentShadowAlpha,
        currentShadowOffsetX,
        currentShadowOffsetY,
        currentInnerShadowRadius,
        currentInnerShadowColor,
        currentInnerShadowAlpha,
        currentInnerShadowOffsetX,
        currentInnerShadowOffsetY
    ) {
        LiquidGlassShadowProperties(
            radius = currentShadowRadius,
            color = currentShadowColor,
            alpha = currentShadowAlpha,
            offset = DpOffset(currentShadowOffsetX, currentShadowOffsetY),
            innerRadius = currentInnerShadowRadius,
            innerColor = currentInnerShadowColor,
            innerAlpha = currentInnerShadowAlpha,
            innerOffset = DpOffset(currentInnerShadowOffsetX, currentInnerShadowOffsetY),
        )
    }

    // Transient optical content blur during expansion/collapse morphing
    val contentBlurAnimatable = remember { Animatable(0f) }
    var isInitialComposition by remember { mutableStateOf(true) }

    LaunchedEffect(shouldExpandBounds) {
        if (isInitialComposition) {
            isInitialComposition = false
            return@LaunchedEffect
        }
        val shouldBlur = if (shouldExpandBounds) blurOnExpand else blurOnCollapse
        if (enableContentBlur && shouldBlur && maxContentBlur > 0.dp) {
            // Rapid rise to peak blur as expansion accelerates
            contentBlurAnimatable.animateTo(
                targetValue = maxContentBlur.value,
                animationSpec = tween(
                    durationMillis = blurRiseDurationMillis,
                    easing = FastOutSlowInEasing
                )
            )
            // Elastic falloff back to tack-sharp clarity as spring settles
            contentBlurAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = if (shouldExpandBounds) expandDampingRatio else collapseDampingRatio,
                    stiffness = stiffness
                )
            )
        } else {
            contentBlurAnimatable.snapTo(0f)
        }
    }

    // 2. Strict pill/capsule shape enforcement (semicircular caps guaranteed)
    val pillShape = CircleShape

    // 3. Main morphing pill container
    Box(
        modifier = modifier
            .size(currentWidth, currentHeight)
            .expandingAnticipation(anticipationState),
        contentAlignment = Alignment.Center
    ) {
        // Enforce pill shape and apply animated color and shadow properties
        val activeConfig = remember(shouldExpandBounds, collapsedGlassConfig, expandedGlassConfig, currentSurfaceColor, currentSurfaceAlpha, currentShadowProperties) {
            val base = if (shouldExpandBounds) expandedGlassConfig else collapsedGlassConfig
            base.withShadowProperties(currentShadowProperties).copy(
                shape = pillShape,
                surfaceTint = currentSurfaceColor,
                surfaceTintAlpha = currentSurfaceAlpha
            )
        }

        val activeFluidityEnabled = if (shouldExpandBounds) enableExpandedBubbleFluidity else enableBubbleFluidity
        val activePressScale = if (shouldExpandBounds) expandedPressScale else pressScale
        val activePullFactor = if (shouldExpandBounds) expandedPullFactor else pullFactor
        val activeElasticity = if (shouldExpandBounds) expandedElasticity else elasticity
        val activeScalePressSpeed = if (shouldExpandBounds) expandedScalePressSpeed else scalePressSpeed
        val activeScalePressDamping = if (shouldExpandBounds) expandedScalePressDamping else scalePressDamping
        val activeScaleSettleSpeed = if (shouldExpandBounds) expandedScaleSettleSpeed else scaleSettleSpeed

        LiquidGlassIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onExpandedChange(true)
            },
            enabled = !isExpanded,
            width = currentWidth,
            height = currentHeight,
            backdrop = backdrop,
            config = activeConfig,
            enableBubbleFluidity = activeFluidityEnabled,
            pressScale = activePressScale,
            pullFactor = activePullFactor,
            elasticity = activeElasticity,
            scalePressSpeed = activeScalePressSpeed,
            scalePressDamping = activeScalePressDamping,
            scaleSettleSpeed = activeScaleSettleSpeed
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pillShape),
                contentAlignment = Alignment.Center
            ) {
                val currentBlur = contentBlurAnimatable.value.dp
                val contentBlurModifier = if (enableContentBlur && currentBlur > 0.5.dp) {
                    Modifier.blur(currentBlur)
                } else {
                    Modifier
                }

                AnimatedContent(
                    targetState = shouldExpandBounds,
                    transitionSpec = contentTransitionSpec,
                    modifier = contentBlurModifier,
                    label = "pillSlotContent"
                ) { expandedState ->
                    if (expandedState) {
                        expandedContent {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandedChange(false)
                        }
                    } else {
                        collapsedContent()
                    }
                }
            }
        }
    }
}

/**
 * Overload of [DynamicPillButton] accepting a unified [DynamicPillConfig].
 */
@Composable
fun DynamicPillButton(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    config: DynamicPillConfig,
    modifier: Modifier = Modifier,
    expansionAnchor: ExpansionAnchor = ExpansionAnchor.Center,
    backdrop: Backdrop? = null,
    dismissOnOutsideTap: Boolean = true,
    contentTransitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = DynamicPillDefaults.ContentTransition,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable (collapse: () -> Unit) -> Unit,
) = DynamicPillButton(
    isExpanded = isExpanded,
    onExpandedChange = onExpandedChange,
    modifier = modifier,
    dimensions = config.dimensions,
    motion = config.motion,
    fluidity = config.fluidity,
    expandedFluidityConfig = config.expandedFluidity,
    shadows = config.shadows,
    colors = config.colors,
    contentBlur = config.contentBlur,
    expansionAnchor = expansionAnchor,
    anticipation = config.anticipation,
    collapsedGlassConfig = config.collapsedGlassConfig ?: LiquidGlassPresets.ProfileIconButton.copy(shape = CircleShape),
    expandedGlassConfig = config.expandedGlassConfig ?: LiquidGlassPresets.ProfileIsland.copy(shape = CircleShape),
    backdrop = backdrop,
    dismissOnOutsideTap = dismissOnOutsideTap,
    contentTransitionSpec = contentTransitionSpec,
    collapsedContent = collapsedContent,
    expandedContent = expandedContent,
)
