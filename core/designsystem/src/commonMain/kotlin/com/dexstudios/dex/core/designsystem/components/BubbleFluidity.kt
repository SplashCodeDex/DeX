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
import kotlinx.coroutines.launch

/**
 * Applies a "bubble fluidity" physics effect.
 * When touched, the component scales down (squishes).
 * When swiped/dragged, the component slightly pulls towards the finger.
 * When released, it bounces back with high elasticity.
 *
 * @param targetScale The scale to squish down to on press.
 * @param pullFactor How much the bubble pulls towards the finger (0.0 to 1.0).
 */
fun Modifier.bubbleFluidity(
    targetScale: Float = 0.85f,
    pullFactor: Float = 0.1f,
    onPhysicsUpdated: ((scale: Float, tx: Float, ty: Float) -> Unit)? = null
): Modifier = this then BubbleFluidityElement(targetScale, pullFactor, onPhysicsUpdated)

private data class BubbleFluidityElement(
    val targetScale: Float,
    val pullFactor: Float,
    val onPhysicsUpdated: ((Float, Float, Float) -> Unit)?
) : ModifierNodeElement<BubbleFluidityNode>() {
    override fun create(): BubbleFluidityNode = BubbleFluidityNode(targetScale, pullFactor, onPhysicsUpdated)

    override fun update(node: BubbleFluidityNode) {
        node.targetScale = targetScale
        node.pullFactor = pullFactor
        node.onPhysicsUpdated = onPhysicsUpdated
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bubbleFluidity"
        properties["targetScale"] = targetScale
        properties["pullFactor"] = pullFactor
    }
}

private class BubbleFluidityNode(
    var targetScale: Float,
    var pullFactor: Float,
    var onPhysicsUpdated: ((Float, Float, Float) -> Unit)?
) : DelegatingNode(), LayoutModifierNode, PointerInputModifierNode {

    private val scale = Animatable(1f)
    private val translationX = Animatable(0f)
    private val translationY = Animatable(0f)

    private var size: IntSize = IntSize.Zero

    private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)

            // On Press: Shrink and squish
            coroutineScope.launch {
                scale.animateTo(
                    targetValue = targetScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

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
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }
                coroutineScope.launch {
                    translationY.animateTo(
                        targetValue = offsetY * pullFactor,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
                }

                pointerEvent = awaitPointerEvent()
            }

            // On Release: Elastic bounce back
            coroutineScope.launch {
                val bounceSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = 800f
                )
                launch { scale.animateTo(1f, bounceSpec) }
                launch { translationX.animateTo(0f, bounceSpec) }
                launch { translationY.animateTo(0f, bounceSpec) }
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
                scaleX = scale.value
                scaleY = scale.value
                translationX = this@BubbleFluidityNode.translationX.value
                translationY = this@BubbleFluidityNode.translationY.value
                onPhysicsUpdated?.invoke(scale.value, translationX, translationY)
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
        pointerInputNode.onCancelPointerInput()
    }
}
