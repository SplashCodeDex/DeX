package com.dexstudios.dex.core.designsystem.components

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

/**
 * Applies a "bubble fluidity" physics effect.
 * When touched, the component scales down (squishes).
 * When swiped/dragged, the component slightly pulls towards the finger.
 * When released, it bounces back with high elasticity.
 *
 * @param targetScale The scale to squish down to on press.
 * @param pullFactor How much the bubble pulls towards the finger (0.0 to 1.0).
 * @param scalePressSpeed Stiffness speed for press parallax scaling (default: 600f).
 * @param scalePressDamping Damping ratio for press parallax scaling smoothness (default: 0.75f / MediumBouncy).
 * @param scaleSettleSpeed Stiffness speed for press scale release-to-settle spring back to 1.0f (default: 1500f).
 * @param onPhysicsUpdated Callback receiving live physics values (scale, translationX, translationY).
 * @param onPressedChanged Callback notified when physical touch press state changes.
 */
fun Modifier.bubbleFluidity(
    targetScale: Float = 0.85f,
    pullFactor: Float = 0.1f,
    scalePressSpeed: Float = 600f,
    scalePressDamping: Float = Spring.DampingRatioMediumBouncy,
    scaleSettleSpeed: Float = Spring.StiffnessMedium,
    onPhysicsUpdated: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null,
    onPressedChanged: ((Boolean) -> Unit)? = null
): Modifier =
    this then BubbleFluidityElement(
        targetScale,
        pullFactor,
        scalePressSpeed,
        scalePressDamping,
        scaleSettleSpeed,
        onPhysicsUpdated,
        onPressedChanged
    )

private data class BubbleFluidityElement(
    val targetScale: Float,
    val pullFactor: Float,
    val scalePressSpeed: Float,
    val scalePressDamping: Float,
    val scaleSettleSpeed: Float,
    val onPhysicsUpdated: ((Float, Float, Float) -> Unit)?,
    val onPressedChanged: ((Boolean) -> Unit)? = null
) : ModifierNodeElement<BubbleFluidityNode>() {
    override fun create(): BubbleFluidityNode = BubbleFluidityNode(
        targetScale,
        pullFactor,
        scalePressSpeed,
        scalePressDamping,
        scaleSettleSpeed,
        onPhysicsUpdated,
        onPressedChanged
    )

    override fun update(node: BubbleFluidityNode) {
        node.targetScale = targetScale
        node.pullFactor = pullFactor
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
        properties["scalePressSpeed"] = scalePressSpeed
        properties["scalePressDamping"] = scalePressDamping
        properties["scaleSettleSpeed"] = scaleSettleSpeed
    }
}

private class BubbleFluidityNode(
    var targetScale: Float,
    var pullFactor: Float,
    var scalePressSpeed: Float,
    var scalePressDamping: Float,
    var scaleSettleSpeed: Float,
    var onPhysicsUpdated: ((Float, Float, Float) -> Unit)?,
    var onPressedChanged: ((Boolean) -> Unit)?
) :
    DelegatingNode(),
    LayoutModifierNode,
    PointerInputModifierNode {

    private val baseScaleX = Animatable(1f)
    private val baseScaleY = Animatable(1f)
    private val translationX = Animatable(0f)
    private val translationY = Animatable(0f)

    private var size: IntSize = IntSize.Zero
    private var scaleJob: Job? = null

    private val pointerInputNode = delegate(
        SuspendingPointerInputModifierNode {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onPressedChanged?.invoke(true)

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

                        // Optimized pull effect: Animatable.animateTo will cancel previous animations automatically.
                        coroutineScope.launch {
                            translationX.animateTo(
                                targetValue = offsetX * pullFactor,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            )
                        }
                        coroutineScope.launch {
                            translationY.animateTo(
                                targetValue = offsetY * pullFactor,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            )
                        }

                        pointerEvent = awaitPointerEvent()
                    }
                } finally {
                    onPressedChanged?.invoke(false)
                }

                // On Release: Elastic bounce back
                scaleJob?.cancel()
                scaleJob = coroutineScope.launch {
                    val scaleBounceSpecX = spring<Float>(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = scaleSettleSpeed,
                    )
                    val scaleBounceSpecY = spring<Float>(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = scaleSettleSpeed * 0.94f,
                    )
                    val pullBounceSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                    launch { baseScaleX.animateTo(1f, scaleBounceSpecX) }
                    launch {
                        delay(8)
                        baseScaleY.animateTo(1f, scaleBounceSpecY)
                    }
                    launch { translationX.animateTo(0f, pullBounceSpec) }
                    launch { translationY.animateTo(0f, pullBounceSpec) }
                }
            }
        },
    )

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        size = IntSize(placeable.width, placeable.height)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val curScaleX = this@BubbleFluidityNode.baseScaleX.value
                val curScaleY = this@BubbleFluidityNode.baseScaleY.value
                scaleX = curScaleX
                scaleY = curScaleY
                translationX = this@BubbleFluidityNode.translationX.value
                translationY = this@BubbleFluidityNode.translationY.value
                onPhysicsUpdated?.invoke((curScaleX + curScaleY) / 2f, translationX, translationY)
            }
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        onPressedChanged?.invoke(false)
        scaleJob?.cancel()
        coroutineScope.launch {
            val bounceSpec = spring<Float>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            launch { baseScaleX.animateTo(1f, bounceSpec) }
            launch { baseScaleY.animateTo(1f, bounceSpec) }
            launch { translationX.animateTo(0f, bounceSpec) }
            launch { translationY.animateTo(0f, bounceSpec) }
        }
        pointerInputNode.onCancelPointerInput()
    }
}
