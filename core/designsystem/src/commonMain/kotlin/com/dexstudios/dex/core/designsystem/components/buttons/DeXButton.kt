package com.dexstudios.dex.core.designsystem.components.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.core.designsystem.components.spotlight.SpotlightConfig
import com.dexstudios.dex.core.designsystem.components.spotlight.spotlight
import com.dexstudios.dex.core.designsystem.theme.HoverEase

/**
 * Cast-shadow intensity for a button, expressed as a single elevation step.
 *
 * The spot and ambient alphas used to be hand-picked at every call site, which is how a 4dp
 * shadow ended up at 0.20/0.10 in one panel and 0.15/0.08 in the next. Declaring only the
 * elevation here and deriving both alphas from one table makes the whole app share a single
 * shadow ramp, so raising or calming every button is a one-value change.
 */
@Immutable
data class DeXButtonShadow(val elevation: Dp) {
    /** Spot (directional) shadow alpha implied by [elevation]. */
    val spotAlpha: Float get() = alphasFor(elevation).first

    /** Ambient (fill) shadow alpha implied by [elevation]; always trails the spot alpha. */
    val ambientAlpha: Float get() = alphasFor(elevation).second

    companion object {
        val None = DeXButtonShadow(0.dp)
        val Low = DeXButtonShadow(2.dp)
        val Base = DeXButtonShadow(4.dp)
        val Raised = DeXButtonShadow(8.dp)
        val Floating = DeXButtonShadow(12.dp)
    }
}

/**
 * Single source of truth for the button ramp: every (spot, ambient) pair the app uses, keyed by
 * elevation band. Keep this the only place shadow opacity is decided.
 */
private fun alphasFor(elevation: Dp): Pair<Float, Float> = when {
    elevation <= 0.dp -> 0f to 0f
    elevation < 3.dp -> 0.15f to 0.08f
    elevation < 6.dp -> 0.20f to 0.10f
    elevation < 10.dp -> 0.35f to 0.18f
    else -> 0.48f to 0.26f
}

/**
 * Geometry, motion and photometry for a [DeXButton].
 *
 * A style is one of the [DeXButtonDefaults] presets, or a preset `.copy(...)`-ed to move a
 * single button up or down the ramp. Only vary what genuinely differs between call sites —
 * the point of this type is that most buttons should not be configured at all.
 *
 * @param shape Outline used for the clip, the shadow, the glare rim and the spotlight, so all
 *   four stay locked together and no caller can desynchronise them.
 * @param minWidth Minimum surface width, for buttons that must stay a readable target.
 * @param minHeight Minimum surface height.
 * @param horizontalPadding Inset between the surface edge and its content.
 * @param verticalPadding Inset between the surface edge and its content.
 * @param shadow Resting cast shadow; the live elevation additionally dips by 20% at full press.
 * @param hoverScale Scale at rest 1.0, at full hover [hoverScale]. 1.0 disables the lift.
 * @param hoverLift Upward translation at full hover, in addition to [hoverScale].
 * @param raiseOnHover Bumps the surface above its siblings while hovered, so a scaled button
 *   never renders underneath the neighbour it is scaling away from.
 * @param fluidity Tactile press deformation physics.
 * @param spotlight Beam photometry. Ignored when [spotlightEnabled] is false.
 * @param spotlightEnabled Escape hatch for surfaces with nothing to light, such as a button
 *   whose child paints its own opaque fill.
 * @param glareOnHoverOnly Resting glare rim dark, flaring only on hover and press.
 * @param fillWidth Lets the content Row claim the full offered width instead of wrapping, which
 *   is what weighted spacers need for a content block that re-centres itself as it expands.
 * @param fillSurface Marks the button as adopting the size its caller imposed (weight,
 *   fillMaxWidth, an explicit height) instead of hugging its content. Required whenever the
 *   style carries no min size, otherwise the surface collapses onto its content. Deliberately
 *   off by default: a `Row` offers bounded constraints to every child, so filling
 *   unconditionally would stretch a circular icon button into a pill.
 * @param contentGap Spacing between content children (icon to label).
 */
@Immutable
data class DeXButtonStyle(
    val shape: Shape,
    val minWidth: Dp = 0.dp,
    val minHeight: Dp = 0.dp,
    val horizontalPadding: Dp = 0.dp,
    val verticalPadding: Dp = 0.dp,
    val shadow: DeXButtonShadow = DeXButtonShadow.Base,
    val hoverScale: Float = 1f,
    val hoverLift: Dp = 0.dp,
    val raiseOnHover: Boolean = false,
    val fluidity: DynamicFluidityConfig = DynamicFluidityConfig.Default,
    val spotlight: SpotlightConfig = SpotlightConfig.Default,
    val spotlightEnabled: Boolean = true,
    val glareOnHoverOnly: Boolean = false,
    val fillWidth: Boolean = false,
    val fillSurface: Boolean = false,
    val contentGap: Dp = 8.dp,
)

/**
 * The button ramp. Each preset exists because at least one surface in the app needs it; adding
 * a preset means deleting a hand-rolled chain, not inventing a look.
 */
object DeXButtonDefaults {
    /** Square icon button: close buttons, the drag-handle pin, the mirror rotate control. */
    val icon = DeXButtonStyle(
        shape = CircleShape,
        shadow = DeXButtonShadow.Base,
        hoverScale = 1.05f,
        glareOnHoverOnly = true,
        contentGap = 0.dp,
    )

    /** Toolbar pill: the quick-action row, 62x48dp, lifting 3dp as it grows. */
    val pill = DeXButtonStyle(
        shape = CircleShape,
        minWidth = 62.dp,
        minHeight = 48.dp,
        shadow = DeXButtonShadow.Base,
        hoverScale = 1.08f,
        hoverLift = (-3).dp,
        raiseOnHover = true,
    )

    /** Labelled call to action: pairing confirm/cancel, dialog cancel. */
    val action = DeXButtonStyle(
        shape = CircleShape,
        minWidth = 80.dp,
        horizontalPadding = 18.dp,
        verticalPadding = 10.dp,
        shadow = DeXButtonShadow.Base,
        spotlight = SpotlightConfig.Focused,
    )

    /** Full-width pill: the send-file bars in the device status and history panels. */
    val wide = DeXButtonStyle(
        shape = CircleShape,
        minHeight = 40.dp,
        horizontalPadding = 16.dp,
        shadow = DeXButtonShadow.Base,
        hoverScale = 1.05f,
    )

    /** Small rounded rectangle: settings chips and file-preview actions. */
    val compact = DeXButtonStyle(
        shape = RoundedCornerShape(8.dp),
        minHeight = 32.dp,
        horizontalPadding = 12.dp,
        verticalPadding = 6.dp,
        shadow = DeXButtonShadow.Low,
        hoverScale = 1.05f,
        contentGap = 6.dp,
    )

    /** Tighter rounded rectangle for dense strips: notification and toast actions. */
    val dense = DeXButtonStyle(
        shape = RoundedCornerShape(12.dp),
        horizontalPadding = 12.dp,
        verticalPadding = 6.dp,
        shadow = DeXButtonShadow.Low,
        contentGap = 6.dp,
    )

    /** Stadium action for modal dialog footers, 22dp radius over a 44dp height. */
    val stadium = DeXButtonStyle(
        shape = RoundedCornerShape(22.dp),
        minHeight = 44.dp,
        horizontalPadding = 20.dp,
        shadow = DeXButtonShadow.Low,
        contentGap = 6.dp,
    )

    /** Elevated floating toolbar button, used where a control must clear its neighbours. */
    val floating = pill.copy(shadow = DeXButtonShadow.Floating, minWidth = 40.dp, minHeight = 40.dp)
}

/**
 * The single button surface for the app.
 *
 * Every interactive button in DeX is this composable with a [DeXButtonStyle] (or
 * [DeXCloseButton], which is itself built on it). That centralisation is the whole point: the
 * touch physics, the cast shadow, the glare rim, the hand cursor, the press-coupled
 * deformation and the [spotlight] beam are one indivisible stack, so a new button inherits the
 * full treatment instead of the author remembering to add each layer.
 *
 * The modifier order below is load-bearing and must not be rearranged at call sites:
 *
 * ```
 * caller modifier (layout, drag, z-order)
 *   -> hover scale + lift
 *     -> bubble fluidity
 *       -> cast shadow
 *         -> clip
 *           -> container fill
 *             -> spotlight          <- needs the fill beneath it and the click target above it
 *               -> glare rim
 *                 -> hover + cursor
 *                   -> click target  <- the spotlight must stay an ancestor to observe hover
 *                     -> content, then overlay
 * ```
 *
 * [overlay] is drawn inside the click target so corner badges stay clickable, which is why the
 * surface is a [Box] and the content a [Row] within it.
 *
 * @param onClick Invoked on a completed tap. Consumes nothing else from the pointer stream.
 * @param modifier Caller-owned placement, measurement and layering. Use it for behaviour that
 *   is genuinely this button's own (expansion layout, drag tracking); never for visuals, which
 *   belong to [style].
 * @param style Geometry, motion and photometry. Default [DeXButtonDefaults.pill].
 * @param enabled Disables interaction, the hand cursor, the shadow, the glare and the beam.
 * @param containerColor Resting surface fill.
 * @param hoverContainerColor Surface fill at full hover, cross-faded over 200ms.
 * @param interactionSource Hoisted so callers that need hover or press state can share it.
 * @param overlay Optional corner decoration drawn above the content, inside the click target.
 * @param content Button content. Tint it explicitly; there is no ambient content colour.
 */
@Composable
fun DeXButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: DeXButtonStyle = DeXButtonDefaults.pill,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    hoverContainerColor: Color = containerColor,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val isHoveredRaw by interactionSource.collectIsHoveredAsState()
    val isPressedRaw by interactionSource.collectIsPressedAsState()
    var isFluidityPressed by remember { mutableStateOf(false) }
    val isPressed = (isPressedRaw || isFluidityPressed) && enabled
    val isActive = isHoveredRaw && enabled

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = DynamicMotionConfig.Default.springSpec(isPressed),
        label = "DeXButtonPressProgress",
    )
    val hoverScale by animateFloatAsState(
        targetValue = if (isActive) style.hoverScale else 1f,
        animationSpec = tween(500, easing = HoverEase),
        label = "DeXButtonHoverScale",
    )
    val hoverLift by animateDpAsState(
        targetValue = if (isActive) style.hoverLift else 0.dp,
        animationSpec = tween(500, easing = HoverEase),
        label = "DeXButtonHoverLift",
    )
    val elevation by animateDpAsState(
        targetValue = (style.shadow.elevation * (1f - 0.20f * pressProgress)).coerceAtLeast(0.dp),
        animationSpec = DynamicMotionConfig.Default.springSpec(isPressed),
        label = "DeXButtonElevation",
    )
    val resolvedContainerColor by animateColorAsState(
        targetValue = if (isActive) hoverContainerColor else containerColor,
        animationSpec = tween(200),
        label = "DeXButtonContainerColor",
    )

    Box(
        modifier = modifier
            .then(if (style.raiseOnHover) Modifier.zIndex(if (isActive) 1f else 0f) else Modifier)
            .graphicsLayer {
                scaleX = hoverScale
                scaleY = hoverScale
                translationY = hoverLift.toPx()
            },
    ) {
        Box(
            modifier = Modifier
                // The surface hugs its content, floored by the variant's minimum, unless the
                // caller imposed a size of its own. Sizing it here rather than on the content
                // Row is what leaves that Row free to wrap, so centring has slack to work with.
                .defaultMinSize(minWidth = style.minWidth, minHeight = style.minHeight)
                .then(if (style.fillSurface) Modifier.fillMaxSize() else Modifier)
                .bubbleFluidity(
                    config = style.fluidity.copy(enabled = enabled),
                    onPressedChanged = { isFluidityPressed = it },
                )
                .shadow(
                    elevation = elevation,
                    shape = style.shape,
                    spotColor = Color.Black.copy(alpha = style.shadow.spotAlpha),
                    ambientColor = Color.Black.copy(alpha = style.shadow.ambientAlpha),
                )
                .clip(style.shape)
                .background(resolvedContainerColor)
                .spotlight(
                    shape = style.shape,
                    config = style.spotlight,
                    enabled = enabled && style.spotlightEnabled,
                    pressProgress = pressProgress,
                )
                .shinyGlare(
                    shape = style.shape,
                    intensity = if (style.glareOnHoverOnly && !isActive && !isPressed) {
                        0f
                    } else {
                        DefaultGlareIntensity * (1f + 0.60f * pressProgress)
                    },
                )
                .hoverable(interactionSource = interactionSource, enabled = enabled)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            // The content Row wraps its children and the Row is then centred here, so an icon in
            // a fixed square (close button, toolbar pill, 40dp tool) lands dead centre and an
            // icon-plus-label group in a full-width pill reads as one centred lockup. Variants
            // that need to lay their content out themselves set fillWidth and the Row's own
            // start arrangement takes over from here.
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .then(if (style.fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = style.horizontalPadding, vertical = style.verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(style.contentGap),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )

            overlay?.invoke(this)
        }
    }
}
