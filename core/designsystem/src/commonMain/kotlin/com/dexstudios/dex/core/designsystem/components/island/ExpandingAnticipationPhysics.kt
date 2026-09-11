package com.dexstudios.dex.core.designsystem.components.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live physics state for expanding anticipation and parallax overshoot scaling.
 *
 * Encapsulates the animated offsets and scales as getters directly from their respective [Animatable] instances.
 * When read inside a [Modifier.graphicsLayer] block (e.g. via [expandingAnticipation]), state reads are isolated
 * strictly to the RenderThread draw phase, preventing unnecessary recompositions during animation frames.
 */
@Stable
class ExpandingAnticipationState internal constructor(
    private val translationXAnimatable: Animatable<Float, *>,
    private val scaleXAnimatable: Animatable<Float, *>,
    private val scaleYAnimatable: Animatable<Float, *>,
    private val canExpandBoundsState: androidx.compose.runtime.State<Boolean>,
) {
    /** Current horizontal translation displacement in pixels. */
    val translationX: Float get() = translationXAnimatable.value

    /** Current horizontal scale factor. */
    val scaleX: Float get() = scaleXAnimatable.value

    /** Current vertical scale factor. */
    val scaleY: Float get() = scaleYAnimatable.value

    /**
     * Whether the container bounds (width/height) are permitted to expand.
     * Stays false during the anticipation windup (~75ms) so the button squishes and moves at rest,
     * then flips to true at the uncoil release inflection moment.
     */
    val canExpandBounds: Boolean get() = canExpandBoundsState.value
}

/**
 * Creates and remembers an [ExpandingAnticipationState] that drives:
 * 1. An anticipation directional nudge (+X or -X depending on [anchor]) during energy gathering.
 * 2. Dual-axis Poisson parallax squish (X leading with outward swell velocity, Y lagging).
 * 3. Dynamic uncoil expansion where scale springs past 1.0f to ~1.035x before settling elastically.
 *
 * @param isExpanded Whether the host component is currently expanded.
 * @param triggerKey Optional key to trigger anticipation on multi-stage expansions (defaults to [isExpanded]).
 * @param anchor Directional anchor determining the anticipation nudge direction ([ExpansionAnchor]).
 * @param config Anticipation and overshoot configuration parameters.
 * @param motion Motion spring kinematics (stiffness and damping ratios).
 * @param fluidity Fluidity tactile physics (press speeds and viscoelastic delays).
 */
@Composable
fun rememberExpandingAnticipationPhysics(
    isExpanded: Boolean,
    triggerKey: Any? = isExpanded,
    anchor: ExpansionAnchor = ExpansionAnchor.Center,
    config: DynamicAnticipationConfig = DynamicAnticipationConfig.Default,
    motion: DynamicMotionConfig = DynamicMotionConfig.Default,
    fluidity: DynamicFluidityConfig = DynamicFluidityConfig.Default,
): ExpandingAnticipationState {
    val density = LocalDensity.current

    val translationXAnimatable = remember { Animatable(0f) }
    val scaleXAnimatable = remember { Animatable(1f) }
    val scaleYAnimatable = remember { Animatable(1f) }
    val canExpandBoundsState = remember { mutableStateOf(isExpanded) }

    var isInitialComposition by remember { mutableStateOf(true) }
    var previousTriggerKey by remember { mutableStateOf(triggerKey) }
    var previousExpanded by remember { mutableStateOf(isExpanded) }

    LaunchedEffect(triggerKey, isExpanded) {
        if (isInitialComposition) {
            isInitialComposition = false
            previousTriggerKey = triggerKey
            previousExpanded = isExpanded
            canExpandBoundsState.value = isExpanded
            return@LaunchedEffect
        }

        val hasNewlyExpanded = (!previousExpanded && isExpanded) || (isExpanded && triggerKey != previousTriggerKey)
        val hasCollapsed = previousExpanded && !isExpanded

        previousTriggerKey = triggerKey
        previousExpanded = isExpanded

        if (hasNewlyExpanded) {
            if (config.enabled) {
                val duration = config.anticipationDurationMillis
                val nudgePx = with(density) { config.nudgeDistance.toPx() }
                val targetNudge = when (anchor) {
                    ExpansionAnchor.Start -> nudgePx

                    // Anchored at Start/Left, expands Right -> nudges +X
                    ExpansionAnchor.End -> -nudgePx

                    // Anchored at End/Right, expands Left -> nudges -X
                    ExpansionAnchor.Center -> 0f // Anchored at Center -> pure symmetric squish
                }
                val squishScale = config.squishScale.coerceIn(0.40f, 1.60f)
                val motionStiffness = motion.stiffness
                val motionDamping = motion.expandDampingRatio
                val scaleDeltaFromNeutral = 1.0f - squishScale
                val reboundBase = if (scaleDeltaFromNeutral != 0f) scaleDeltaFromNeutral else 0.05f
                val initialReboundVelocity = reboundBase * config.overshootVelocityMultiplier

                if (duration > 0) {
                    canExpandBoundsState.value = false

                    // Phase 1: Coordinated Anticipation Windup (0..duration ms)
                    // All axes reach their maximum potential energy simultaneously at t = duration
                    val lagMs = (duration * 0.15f).toLong().coerceIn(4L, 50L)
                    val yDuration = (duration - lagMs).toInt().coerceAtLeast(10)

                    launch {
                        if (targetNudge != 0f) {
                            launch {
                                if (config.nudgeSpeed > 0f) {
                                    translationXAnimatable.animateTo(
                                        targetValue = targetNudge,
                                        animationSpec = spring(
                                            dampingRatio = 0.70f,
                                            stiffness = config.nudgeSpeed.coerceIn(50f, 2500f),
                                        ),
                                    )
                                } else {
                                    translationXAnimatable.animateTo(
                                        targetValue = targetNudge,
                                        animationSpec = tween(
                                            durationMillis = duration,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                }
                            }
                        }
                        launch {
                            scaleXAnimatable.animateTo(
                                targetValue = squishScale,
                                animationSpec = tween(
                                    durationMillis = duration,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                        launch {
                            delay(lagMs)
                            scaleYAnimatable.animateTo(
                                targetValue = squishScale,
                                animationSpec = tween(
                                    durationMillis = yDuration,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    }

                    // Await full windup completion to the exact millisecond
                    delay(duration.toLong())
                }

                // Phase 2: Inflection Moment -> Release bounds & launch uncoil rebound
                canExpandBoundsState.value = true

                launch {
                    if (translationXAnimatable.value != 0f) {
                        launch {
                            translationXAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = motionDamping,
                                    stiffness = motionStiffness,
                                ),
                            )
                        }
                    }
                    launch {
                        scaleXAnimatable.animateTo(
                            targetValue = 1f,
                            initialVelocity = initialReboundVelocity,
                            animationSpec = spring(
                                dampingRatio = motionDamping,
                                stiffness = motionStiffness,
                            ),
                        )
                    }
                    launch {
                        delay(8)
                        scaleYAnimatable.animateTo(
                            targetValue = 1f,
                            initialVelocity = initialReboundVelocity * 0.92f,
                            animationSpec = spring(
                                dampingRatio = motionDamping,
                                stiffness = motionStiffness * 0.94f,
                            ),
                        )
                    }
                }
            } else {
                canExpandBoundsState.value = true
            }
        } else if (hasCollapsed) {
            // Collapse Phase: Clean elastic return without anticipation
            canExpandBoundsState.value = false
            val collapseDamping = motion.collapseDampingRatio
            val collapseStiffness = motion.stiffness

            launch {
                if (translationXAnimatable.value != 0f) {
                    launch {
                        translationXAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = collapseDamping,
                                stiffness = collapseStiffness,
                            ),
                        )
                    }
                }
                launch {
                    scaleXAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = collapseDamping,
                            stiffness = collapseStiffness,
                        ),
                    )
                }
                launch {
                    scaleYAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = collapseDamping,
                            stiffness = collapseStiffness,
                        ),
                    )
                }
            }
        }
    }

    return remember {
        ExpandingAnticipationState(
            translationXAnimatable = translationXAnimatable,
            scaleXAnimatable = scaleXAnimatable,
            scaleYAnimatable = scaleYAnimatable,
            canExpandBoundsState = canExpandBoundsState,
        )
    }
}

/**
 * Applies the hardware-accelerated [ExpandingAnticipationState] transforms directly to the RenderThread layer.
 */
fun Modifier.expandingAnticipation(state: ExpandingAnticipationState): Modifier = this.graphicsLayer {
    translationX = state.translationX
    scaleX = state.scaleX
    scaleY = state.scaleY
}
