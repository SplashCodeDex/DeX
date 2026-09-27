package com.dexstudios.dex.core.designsystem.components.spotlight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Photometric and kinematic tokens for the cursor-tracking spotlight.
 *
 * The spotlight is treated as illumination rather than decoration: the pool composites with
 * [BlendMode.Plus] so it adds light energy to whatever surface it lands on. That is what lets a
 * single token set read correctly on both the near-white light-theme surfaces and the
 * near-black dark-theme surfaces without a per-theme fork.
 */
object SpotlightPhysics {
    /** Peak light energy, in tint units, contributed by a fully lit pool. */
    const val DefaultIntensity = 0.24f

    /** Additional light energy added when the surface is held at full press. */
    const val DefaultPressBoost = 0.55f

    /** Pool radius expressed as a fraction of the surface's longest side. */
    const val DefaultRadius = 0.70f

    /**
     * Power-curve exponent shaping the pool. Values above 1 tighten the highlight toward the
     * pointer and deepen the falloff, reading as a focused beam; 1 is a plain linear cone, and
     * below 1 washes the whole surface evenly.
     */
    const val DefaultFalloff = 1.9f

    /** Beam colour. White adds neutral light, so surfaces keep their own hue underneath. */
    val DefaultTint = Color(0xFFFFFFFF)

    /** Light-up spring: under-damped, so the beam overshoots slightly as it strikes. */
    val DefaultEnterSpring: AnimationSpec<Float> = spring(dampingRatio = 0.58f, stiffness = 320f)

    /** Light-down spring: near critically damped, so the surface settles without ringing. */
    val DefaultExitSpring: AnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 420f)

    /**
     * Pointer-tracking spring. Stiff and mildly under-damped, so the beam trails the cursor by
     * a perceptible few frames — that lag is what reads as a physical light source rather than
     * a decal glued to the mouse.
     */
    val DefaultPointerSpring: AnimationSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 900f)
}

/**
 * Photometric configuration for [spotlight].
 *
 * @param intensity Peak light energy of the fully lit pool.
 * @param radius Pool radius as a fraction of the surface's longest side.
 * @param falloff Power-curve exponent; see [SpotlightPhysics.DefaultFalloff].
 * @param tint Beam colour.
 * @param pressBoost Extra light energy contributed at full press, as a fraction of [intensity].
 * @param followPointer When false the pool stays pinned to the surface centre and only its
 *   energy animates — useful on small targets, where tracking adds noise instead of life.
 * @param enterSpring Energy spring used while the beam strikes.
 * @param exitSpring Energy spring used while the beam fades.
 * @param pointerSpring Spring used to chase the pointer between move events.
 */
@Immutable
data class SpotlightConfig(
    val intensity: Float = SpotlightPhysics.DefaultIntensity,
    val radius: Float = SpotlightPhysics.DefaultRadius,
    val falloff: Float = SpotlightPhysics.DefaultFalloff,
    val tint: Color = SpotlightPhysics.DefaultTint,
    val pressBoost: Float = SpotlightPhysics.DefaultPressBoost,
    val followPointer: Boolean = true,
    val enterSpring: AnimationSpec<Float> = SpotlightPhysics.DefaultEnterSpring,
    val exitSpring: AnimationSpec<Float> = SpotlightPhysics.DefaultExitSpring,
    val pointerSpring: AnimationSpec<Float> = SpotlightPhysics.DefaultPointerSpring,
) {
    companion object {
        /**
         * House default: a restrained beam that lights the surface under the pointer without
         * announcing itself. Deliberately quiet — most buttons in the app are small and sit in
         * dense rows, where a wide or hot pool competes with the content instead of aiding it.
         * Reach for [Broad] or [Focused] only where a single control should visibly dominate.
         */
        val Default = SpotlightConfig()

        /** Wider, hotter wash for large surfaces that can carry a broader pool. */
        val Broad = SpotlightConfig(intensity = 0.34f, radius = 0.85f, falloff = 1.7f)

        /** Tighter, hottest pool for primary calls to action. */
        val Focused = SpotlightConfig(intensity = 0.46f, radius = 0.62f, falloff = 2.4f)
    }
}

/**
 * Lights a surface with a cursor-tracking pool that fades in on hover, brightens under press,
 * and fades back out when the pointer leaves.
 *
 * This is a draw modifier, not a layout modifier, and it is deliberately self-contained: hover
 * is read straight off the pointer event stream, which already carries the position the beam
 * needs, so call sites need no extra interaction plumbing. All animation state lives in the
 * modifier node, so a moving cursor never recomposes the button or its subtree.
 *
 * Position in the chain matters, because it decides what the beam lands on and what stays
 * legible above it. Apply it after `clip`/`background` and before `clickable`:
 *
 * ```
 * Modifier
 *     .clip(shape)
 *     .background(surface)
 *     .spotlight(shape)              // beam lands on `surface`, confined by `shape`
 *     .shinyGlare(shape)             // rim stays above the beam
 *     .clickable(...)                // stay an ancestor so hover is still observed
 * ```
 *
 * The pool is composited before the node delegates to its own content, so icons and labels stay
 * crisp on top of the light, and additively, so it reads as light rather than paint.
 *
 * @param shape Outline the beam is confined to; must match the shape used for the surface clip.
 * @param config Photometric configuration.
 * @param enabled When false the beam is forced dark and stops tracking the pointer.
 * @param pressProgress Current press deformation, 0..1. Adds [SpotlightConfig.pressBoost] light.
 */
fun Modifier.spotlight(shape: Shape, config: SpotlightConfig = SpotlightConfig.Default, enabled: Boolean = true, pressProgress: Float = 0f): Modifier {
    if (!enabled) return this
    return this then SpotlightElement(shape, config, pressProgress.coerceIn(0f, 1f))
}

private data class SpotlightElement(val shape: Shape, val config: SpotlightConfig, val pressProgress: Float) : ModifierNodeElement<SpotlightNode>() {
    override fun create(): SpotlightNode = SpotlightNode(shape, config, pressProgress)

    override fun update(node: SpotlightNode) {
        val configChanged = node.config != config
        node.shape = shape
        node.config = config
        node.pressProgress = pressProgress
        if (configChanged) {
            // Photometry changed: drop the placed pool so a stale chase target is not inherited
            // from the previous radius, and force an energy retarget on the next tick.
            node.forgetPool()
        }
        node.retargetEnergy()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "spotlight"
        properties["shape"] = shape
        properties["config"] = config
        properties["pressProgress"] = pressProgress
    }
}

/** Number of samples used to approximate the [SpotlightConfig.falloff] power curve. */
private const val FalloffSamples = 5

private class SpotlightNode(var shape: Shape, var config: SpotlightConfig, var pressProgress: Float) :
    DelegatingNode(),
    DrawModifierNode,
    PointerInputModifierNode {

    private val energy = Animatable(0f)
    private val poolX = Animatable(0f)
    private val poolY = Animatable(0f)

    private var isHovered = false

    /** False until the first pointer position arrives, so the pool can rest on the centre. */
    private var isPoolPlaced = false

    private var targetEnergy = 0f

    fun forgetPool() {
        isPoolPlaced = false
        targetEnergy = -1f
    }

    private fun placePool(position: Offset?) {
        if (position == null) return
        val chase = config.followPointer && isPoolPlaced
        isPoolPlaced = true
        val targetX = position.x
        val targetY = position.y
        val spec = config.pointerSpring
        coroutineScope.launch {
            if (chase) {
                launch { poolX.animateTo(targetX, spec) }
                launch { poolY.animateTo(targetY, spec) }
            } else {
                // Arrival snap: springing in from a stale position would fly the beam across the
                // surface on every fresh hover, which reads as a bug rather than as motion.
                poolX.snapTo(targetX)
                poolY.snapTo(targetY)
            }
        }
    }

    fun retargetEnergy() {
        val press = pressProgress.coerceIn(0f, 1f)
        val peak = if (isHovered) config.intensity * (1f + config.pressBoost * press) else 0f
        if (abs(peak - targetEnergy) < 0.0005f) return
        targetEnergy = peak
        val spec = if (peak > 0f) config.enterSpring else config.exitSpring
        coroutineScope.launch { energy.animateTo(peak, spec) }
    }

    /**
     * Hover is observed on [PointerEventPass.Initial] so the beam is seen before `clickable`'s
     * own gesture detection runs and can swallow the move. Nothing is ever consumed here, so
     * click semantics and drag tracking downstream are untouched.
     */
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        when (event.type) {
            PointerEventType.Enter -> {
                isHovered = true
                placePool(event.changes.firstOrNull()?.position)
            }

            PointerEventType.Move -> {
                isHovered = true
                placePool(event.changes.firstOrNull()?.position)
            }

            PointerEventType.Exit -> isHovered = false

            else -> return
        }
        retargetEnergy()
    }

    override fun onCancelPointerInput() {
        isHovered = false
        retargetEnergy()
    }

    override fun ContentDrawScope.draw() {
        // Painted before delegating, so the pool lands under the icons and labels rather than
        // washing them out. Animatable reads here also register the draw invalidation that
        // keeps the beam animating without any recomposition.
        drawPool()
        drawContent()
    }

    private fun ContentDrawScope.drawPool() {
        val lit = energy.value
        if (lit <= 0.001f) return
        val radius = config.radius * max(size.width, size.height)
        if (radius <= 0f) return

        val center = if (isPoolPlaced) {
            Offset(poolX.value, poolY.value)
        } else {
            Offset(size.width / 2f, size.height / 2f)
        }

        val peak = lit.coerceIn(0f, 1f)
        val exponent = config.falloff.coerceAtLeast(0.1f)
        val tint = config.tint
        val stops = Array(FalloffSamples) { index ->
            val position = index / (FalloffSamples - 1f)
            position to tint.copy(alpha = (peak * (1f - position).pow(exponent)).coerceIn(0f, 1f))
        }

        drawCircle(
            brush = Brush.radialGradient(colorStops = stops, center = center, radius = radius),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }
}
