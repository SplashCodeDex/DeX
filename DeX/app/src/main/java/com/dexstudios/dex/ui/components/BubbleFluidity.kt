package com.dexstudios.dex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.dexstudios.dex.ui.components.island.DynamicFluidityConfig

/**
 * Applies a "bubble fluidity" physics effect.
 * When touched, the component scales down (squishes).
 * When swiped/dragged, the component slightly pulls towards the finger.
 * When released, it bounces back with natural jelly elasticity.
 *
 * @param targetScale The scale to squish down to on press (0.70f to 1.50f).
 * @param pullFactor How much the bubble pulls towards the finger (0.0 to 1.0).
 * @param elasticity Degree of jelly elasticity (0.0f = rigid/critically damped to 1.0f = hyper-jelly wobble).
 * @param scalePressSpeed Stiffness speed for press parallax scaling (default: 600f).
 * @param scalePressDamping Damping ratio for press parallax scaling smoothness (default: 0.75f / MediumBouncy).
 * @param scaleSettleSpeed Stiffness speed for press scale release-to-settle spring back to 1.0f (default: 1500f).
 * @param onPhysicsUpdated Callback receiving live physics values (scale, translationX, translationY).
 * @param onPressedChanged Callback notified when physical touch press state changes.
 */
/**
 * Applies a "bubble fluidity" physics effect using a unified [DynamicFluidityConfig].
 *
 * Encapsulates anisotropic parallax axis scaling (horizontal leading stretch + vertical viscoelastic lag),
 * directional touch pull tracking, and jelly shear elasticity.
 *
 * @param config The unified fluidity physics configuration.
 * @param onPhysicsUpdated Callback receiving live physics values (scale, translationX, translationY).
 * @param onPressedChanged Callback notified when physical touch press state changes.
 */
fun Modifier.bubbleFluidity(
    config: DynamicFluidityConfig = DynamicFluidityConfig.Default,
    onPhysicsUpdated: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null,
    onPressedChanged: ((Boolean) -> Unit)? = null
): Modifier {
    if (!config.enabled) return this
    return this then BubbleFluidityElement(
        targetScale = config.pressScale,
        pullFactor = config.pullFactor,
        elasticity = config.elasticity,
        scalePressSpeed = config.scalePressSpeed,
        scalePressDamping = config.scalePressDamping,
        scaleSettleSpeed = config.scaleSettleSpeed,
        onPhysicsUpdated = onPhysicsUpdated,
        onPressedChanged = onPressedChanged
    )
}

fun Modifier.bubbleFluidity(
    targetScale: Float = DynamicFluidityConfig.Default.pressScale,
    pullFactor: Float = DynamicFluidityConfig.Default.pullFactor,
    elasticity: Float = DynamicFluidityConfig.Default.elasticity,
    scalePressSpeed: Float = DynamicFluidityConfig.Default.scalePressSpeed,
    scalePressDamping: Float = DynamicFluidityConfig.Default.scalePressDamping,
    scaleSettleSpeed: Float = DynamicFluidityConfig.Default.scaleSettleSpeed,
    onPhysicsUpdated: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null,
    onPressedChanged: ((Boolean) -> Unit)? = null
): Modifier = this then BubbleFluidityElement(
    targetScale,
    pullFactor,
    elasticity,
    scalePressSpeed,
    scalePressDamping,
    scaleSettleSpeed,
    onPhysicsUpdated,
    onPressedChanged
)

private data class BubbleFluidityElement(
    val targetScale: Float,
    val pullFactor: Float,
    val elasticity: Float,
    val scalePressSpeed: Float,
    val scalePressDamping: Float,
    val scaleSettleSpeed: Float,
    val onPhysicsUpdated: ((Float, Float, Float) -> Unit)?,
    val onPressedChanged: ((Boolean) -> Unit)? = null
) : ModifierNodeElement<BubbleFluidityNode>() {
    override fun create(): BubbleFluidityNode = BubbleFluidityNode(
        targetScale,
        pullFactor,
        elasticity,
        scalePressSpeed,
        scalePressDamping,
        scaleSettleSpeed,
        onPhysicsUpdated,
        onPressedChanged
    )

    override fun update(node: BubbleFluidityNode) {
        node.targetScale = targetScale
        node.pullFactor = pullFactor
        node.elasticity = elasticity
        node.scalePressSpeed = scalePressSpeed
        node.scalePressDamping = scalePressDamping
        node.scaleSettleSpeed = scaleSettleSpeed
        node.onPhysicsUpdated = onPhysicsUpdated
        node.onPressedChanged = onPressedChanged
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bubbleFluidity"
        properties["targetScale"] = targetScale
        properties["pullFactor"] = pullFactor
        properties["elasticity"] = elasticity
        properties["scalePressSpeed"] = scalePressSpeed
        properties["scalePressDamping"] = scalePressDamping
        properties["scaleSettleSpeed"] = scaleSettleSpeed
    }
}

private class BubbleFluidityNode(
    var targetScale: Float,
    var pullFactor: Float,
    var elasticity: Float,
    var scalePressSpeed: Float,
    var scalePressDamping: Float,
    var scaleSettleSpeed: Float,
    var onPhysicsUpdated: ((Float, Float, Float) -> Unit)?,
    var onPressedChanged: ((Boolean) -> Unit)?
) : DelegatingNode(), LayoutModifierNode, PointerInputModifierNode {

    private val baseScaleX = Animatable(1f)
    private val baseScaleY = Animatable(1f)
    private val stretchX = Animatable(0f)
    private val stretchY = Animatable(0f)
    private val translationX = Animatable(0f)
    private val translationY = Animatable(0f)

    private var size: IntSize = IntSize.Zero
    private var scaleJob: Job? = null

    private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onPressedChanged?.invoke(true)

            val clampedElasticity = elasticity.coerceIn(0f, 1f)

            // On Press: Parallax Axis Scaling
            // X-axis initiates immediately with subtle leading stretch; Y-axis smoothly catches up
            scaleJob?.cancel()
            scaleJob = coroutineScope.launch {
                val clampedSpeed = scalePressSpeed.coerceAtLeast(100f)
                val clampedDamping = scalePressDamping.coerceIn(0.20f, 1.20f)
                val lagMs = (16f * (600f / clampedSpeed)).toLong().coerceIn(6L, 35L)

                if (targetScale >= 1.0f) {
                    // Swell / Expansion (e.g. 1.0 to 1.2):
                    // X-axis starts immediately (stretching horizontally first)
                    launch {
                        baseScaleX.animateTo(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = clampedDamping,
                                stiffness = clampedSpeed * 1.10f
                            )
                        )
                    }
                    // Y-axis has a subtle viscoelastic lag, then smoothly catches up
                    launch {
                        delay(lagMs)
                        baseScaleY.animateTo(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = clampedDamping,
                                stiffness = clampedSpeed * 0.85f
                            )
                        )
                    }
                } else {
                    // Squish / Compression (e.g. 1.0 to 0.85):
                    // Viscoelastic Poisson effect: vertical compression creates an initial subtle outward stretch on X
                    // before X smoothly pulls in and catches up with Y at targetScale.
                    // Using an initial outward velocity on baseScaleX creates a continuous, single-spring curve
                    // that smoothly peaks outward and glides into targetScale without any animation hitch or stall.
                    val squishDepth = (1.0f - targetScale).coerceAtLeast(0f)
                    val outwardVelocity = squishDepth * (clampedSpeed / 600f) * 2.2f

                    launch {
                        baseScaleX.animateTo(
                            targetValue = targetScale,
                            initialVelocity = outwardVelocity,
                            animationSpec = spring(
                                dampingRatio = clampedDamping,
                                stiffness = clampedSpeed * 0.95f
                            )
                        )
                    }
                    launch {
                        delay(lagMs)
                        baseScaleY.animateTo(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = clampedDamping,
                                stiffness = clampedSpeed * 0.80f
                            )
                        )
                    }
                }
            }

            try {
                var pointerEvent = awaitPointerEvent()
                while (pointerEvent.changes.any { it.pressed }) {
                    val change = pointerEvent.changes.first()
                    val position = change.position

                    val offsetX = position.x - size.width / 2f
                    val offsetY = position.y - size.height / 2f

                    // Directional drag pull tracking
                    coroutineScope.launch {
                        translationX.animateTo(
                            targetValue = offsetX * pullFactor,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                    }
                    coroutineScope.launch {
                        translationY.animateTo(
                            targetValue = offsetY * pullFactor,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                    }

                    // Jelly Elasticity: When user rubs on the button, it stretches along the direction of touch
                    val distSq = offsetX * offsetX + offsetY * offsetY
                    if (distSq > 0.001f && size.width > 0 && size.height > 0) {
                        val dist = kotlin.math.sqrt(distSq)
                        val halfSize = (kotlin.math.min(size.width, size.height) / 2f).coerceAtLeast(1f)
                        val normDist = (dist / halfSize).coerceIn(0f, 1.5f)
                        val totalStretch = normDist * clampedElasticity * 0.26f

                        // Projection ratios along pull axis (quadrant-invariant since squared)
                        val cos2 = (offsetX * offsetX) / distSq
                        val sin2 = (offsetY * offsetY) / distSq

                        // Elongates along the touch axis and slightly pinches across the transverse axis
                        val targetStretchX = (totalStretch * cos2) - (totalStretch * sin2 * 0.35f)
                        val targetStretchY = (totalStretch * sin2) - (totalStretch * cos2 * 0.35f)

                        coroutineScope.launch {
                            launch { stretchX.animateTo(targetStretchX, spring(stiffness = Spring.StiffnessMedium)) }
                            launch { stretchY.animateTo(targetStretchY, spring(stiffness = Spring.StiffnessMedium)) }
                        }
                    }

                    pointerEvent = awaitPointerEvent()
                }
            } finally {
                onPressedChanged?.invoke(false)
            }

            // On Release: Elastic bounce back with adjustable scaleSettleSpeed, while drag pull oscillation stays locked at Spring.StiffnessMedium (1500f)
            scaleJob?.cancel()
            scaleJob = coroutineScope.launch {
                val clampedElasticity = elasticity.coerceIn(0f, 1f)
                // At baseline elasticity (0.55f), damping is Spring.DampingRatioHighBouncy (0.50f).
                // Cranked elasticity allows extra lively wobble, while stiffness stays locked at Spring.StiffnessMedium (1500f).
                val releaseDamping = (Spring.DampingRatioHighBouncy - (0.20f * (clampedElasticity - 0.55f))).coerceIn(0.28f, 0.85f)
                val pullStiffness = Spring.StiffnessMedium

                val scaleBounceSpecX = spring<Float>(
                    dampingRatio = releaseDamping,
                    stiffness = scaleSettleSpeed
                )
                val scaleBounceSpecY = spring<Float>(
                    dampingRatio = releaseDamping,
                    stiffness = scaleSettleSpeed * 0.94f
                )
                val pullBounceSpec = spring<Float>(
                    dampingRatio = releaseDamping,
                    stiffness = pullStiffness
                )

                launch { baseScaleX.animateTo(1f, scaleBounceSpecX) }
                launch {
                    delay(8)
                    baseScaleY.animateTo(1f, scaleBounceSpecY)
                }
                launch { stretchX.animateTo(0f, pullBounceSpec) }
                launch { stretchY.animateTo(0f, pullBounceSpec) }
                launch { translationX.animateTo(0f, pullBounceSpec) }
                launch { translationY.animateTo(0f, pullBounceSpec) }
            }
        }
    })

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        size = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val curScaleX = this@BubbleFluidityNode.baseScaleX.value
                val curScaleY = this@BubbleFluidityNode.baseScaleY.value
                val sX = (curScaleX * (1f + this@BubbleFluidityNode.stretchX.value)).coerceIn(0.5f, 2.0f)
                val sY = (curScaleY * (1f + this@BubbleFluidityNode.stretchY.value)).coerceIn(0.5f, 2.0f)
                scaleX = sX
                scaleY = sY
                translationX = this@BubbleFluidityNode.translationX.value
                translationY = this@BubbleFluidityNode.translationY.value
                onPhysicsUpdated?.invoke((curScaleX + curScaleY) / 2f, translationX, translationY)
            }
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        onPressedChanged?.invoke(false)
        scaleJob?.cancel()
        coroutineScope.launch {
            val bounceSpec = spring<Float>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            launch { baseScaleX.animateTo(1f, bounceSpec) }
            launch { baseScaleY.animateTo(1f, bounceSpec) }
            launch { stretchX.animateTo(0f, bounceSpec) }
            launch { stretchY.animateTo(0f, bounceSpec) }
            launch { translationX.animateTo(0f, bounceSpec) }
            launch { translationY.animateTo(0f, bounceSpec) }
        }
        pointerInputNode.onCancelPointerInput()
    }
}
