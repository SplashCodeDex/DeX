package com.dexstudios.dex.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.dexstudios.dex.ui.components.glass.shinyGlare
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.shadow.InnerShadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 3-Tier Detents for the Dynamic Bottom Sheet:
 * - Half: 50% screen height (resting state: device carousel + action button)
 * - High: 80% screen height (media hub: quick actions + recent media grid)
 * - Full: 100% screen height (full immersion: [Media | History] segmented control)
 */
enum class SheetTier(val fraction: Float) {
    Half(0.50f),
    High(0.80f),
    Full(1.00f);
}

enum class SheetExpandedMode {
    Media,
    History;
}

@Composable
fun NavBottomSheet(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    initialTier: SheetTier = SheetTier.Half,
    onDismiss: () -> Unit = {},
    sheetContent: @Composable ColumnScope.(
        expansionFraction: Float,
        currentTier: SheetTier,
        halfHeightDp: Dp,
        expandTo: (SheetTier) -> Unit,
        collapseToHalf: () -> Unit
    ) -> Unit,
    content: @Composable (expansionFraction: Float, paddingValues: PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = constraints.maxHeight.toFloat()

        // Heights mapping for each tier: top edge offset from screen top
        fun tierOffset(tier: SheetTier): Float = totalHeightPx * (1f - tier.fraction)

        val halfOffset = tierOffset(SheetTier.Half)
        val highOffset = tierOffset(SheetTier.High)
        val fullOffset = tierOffset(SheetTier.Full)

        val tierOffsets = listOf(
            SheetTier.Full to fullOffset,
            SheetTier.High to highOffset,
            SheetTier.Half to halfOffset
        )

        // Initialize off-screen (totalHeightPx) and spring up into view on entry
        val animatableOffset = remember(totalHeightPx) {
            Animatable(totalHeightPx)
        }

        val springSpec = remember {
            spring<Float>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }

        fun expandTo(tier: SheetTier) {
            scope.launch {
                animatableOffset.animateTo(tierOffset(tier), springSpec)
            }
        }

        fun collapseToHalf() {
            scope.launch {
                animatableOffset.animateTo(halfOffset, springSpec)
            }
        }

        // Animate up from bottom on initial composition and when resuming from background
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, totalHeightPx) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (totalHeightPx > 0f) {
                        scope.launch {
                            animatableOffset.animateTo(tierOffset(initialTier), springSpec)
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        var isDragging by remember { mutableStateOf(false) }

        // Live expansion fraction (0.0f at Half/50% to 1.0f at Full/100%)
        val expansionFraction by remember {
            derivedStateOf {
                if (halfOffset <= fullOffset) 0f
                else ((halfOffset - animatableOffset.value) / (halfOffset - fullOffset)).coerceIn(0f, 1f)
            }
        }

        // Closest settled tier based on current offset
        val currentTier by remember {
            derivedStateOf {
                val current = animatableOffset.value
                tierOffsets.minByOrNull { abs(it.second - current) }?.first ?: SheetTier.Half
            }
        }

        // Back gesture handler: collapses to 50% if expanded, or smoothly exits downwards if at 50%
        BackHandler(enabled = true) {
            if (expansionFraction > 0.05f) {
                collapseToHalf()
            } else {
                scope.launch {
                    animatableOffset.animateTo(totalHeightPx, springSpec)
                    onDismiss()
                }
            }
        }

        val sheetShape = RoundedCornerShape(
            topStart = (28 * (1f - (expansionFraction * 0.5f))).dp,
            topEnd = (28 * (1f - (expansionFraction * 0.5f))).dp
        )

        // 1. Underlying Main Screen Content
        Box(modifier = Modifier.fillMaxSize()) {
            content(expansionFraction, PaddingValues(bottom = with(density) { (totalHeightPx * 0.5f).toDp() }))

            // Dimming scrim activates smoothly as sheet expands past 50%
            val scrimAlpha = ((expansionFraction - 0.15f) / 0.85f).coerceIn(0f, 1f) * 0.6f
            if (scrimAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(Color.Black)
                )
            }
        }

        // 2. Interactive Click-Outside Dismiss Area (Above the Sheet Top Edge)
        val currentSheetTopDp = with(density) { animatableOffset.value.coerceAtLeast(0f).toDp() }
        if (currentSheetTopDp > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentSheetTopDp)
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (expansionFraction > 0.05f) {
                                collapseToHalf()
                            } else {
                                scope.launch {
                                    animatableOffset.animateTo(totalHeightPx, springSpec)
                                    onDismiss()
                                }
                            }
                        }
                    )
            )
        }

        // Backdrop to sample for the highlighter in the middle of the sheet
        val localSheetBackdrop = rememberLayerBackdrop()

        // 3. Multi-Tier Draggable Sheet Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeightPx.toDp() })
                .offset {
                    IntOffset(0, animatableOffset.value.roundToInt().coerceIn(0, totalHeightPx.roundToInt()))
                }
                .pointerInput(totalHeightPx) {
                    val dismissThresholdPx = with(density) { 60.dp.toPx() }
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            val currentPos = animatableOffset.value
                            if (currentPos >= halfOffset + dismissThresholdPx) {
                                // Dragged past dismiss threshold: animate off-screen and dismiss
                                scope.launch {
                                    animatableOffset.animateTo(totalHeightPx, springSpec)
                                    onDismiss()
                                }
                            } else if (currentPos > halfOffset) {
                                // Small downward pull: spring back up to 50%
                                collapseToHalf()
                            } else {
                                // Snap between 50%, 80%, and 100%
                                val nearest = tierOffsets.minByOrNull { abs(it.second - currentPos) }?.second ?: halfOffset
                                scope.launch {
                                    animatableOffset.animateTo(nearest, springSpec)
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val currentPos = animatableOffset.value
                            val nearest = if (currentPos > halfOffset) halfOffset
                            else (tierOffsets.minByOrNull { abs(it.second - currentPos) }?.second ?: halfOffset)
                            scope.launch {
                                animatableOffset.animateTo(nearest, springSpec)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val currentVal = animatableOffset.value
                            val adjustedDrag = if (currentVal > halfOffset && dragAmount > 0f) {
                                // Elastic resistance when pulling below 50%
                                dragAmount * 0.6f
                            } else {
                                dragAmount
                            }
                            val newTarget = (currentVal + adjustedDrag).coerceIn(fullOffset, totalHeightPx)
                            scope.launch {
                                animatableOffset.snapTo(newTarget)
                            }
                        }
                    )
                }
        ) {
            // Layer 1: Captured Glass Surface (Backdrop + Content)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(localSheetBackdrop)
            ) {
                if (backdrop != null) {
                    LiquidGlassPanel(
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize(),
                        shape = sheetShape,
                        config = LiquidGlassPresets.NavBar.copy(
                            shape = sheetShape,
                            blurRadius = 14.dp,
                            restRefraction = 0.5f,
                            vibrancyEnabled = true,
                            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
                            surfaceTintAlpha = LiquidGlassTokens.DarkTintAlpha,
                        ),
                        content = {}
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(sheetShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Sheet Inner Content
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Clean drag handle at the top
                    DragHandle()

                    // Content receives live fraction, current tier, halfHeightDp, and expand/collapse actions
                    sheetContent(
                        expansionFraction,
                        currentTier,
                        with(density) { (totalHeightPx * 0.5f).toDp() },
                        ::expandTo,
                        ::collapseToHalf
                    )
                }
            }

            // Layer 2: Central Sheet Highlighter
            SheetLiquidGlassHighlighter(
                isInteracting = isDragging,
                backdrop = localSheetBackdrop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(10f)
            )
        }
    }
}

/**
 * Clean drag handle at the top of the sheet.
 */
@Composable
private fun DragHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

/**
 * Signature Liquid Glass Highlighter positioned in the middle of the sheet.
 */
@Composable
fun SheetLiquidGlassHighlighter(
    isInteracting: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    lensHeight: Dp = 190.dp,
    lensAmount: Dp = 100.dp,
    restRefraction: Float = 0.11f,
) {
    val highlighterWidth by animateDpAsState(
        targetValue = if (isInteracting) 140.dp else 98.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "sheetHlW"
    )
    val highlighterHeight by animateDpAsState(
        targetValue = if (isInteracting) 90.dp else 62.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "sheetHlH"
    )

    val animatedLensHeight by animateDpAsState(
        targetValue = if (isInteracting) lensHeight else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetLensH"
    )
    val animatedLensAmount by animateDpAsState(
        targetValue = if (isInteracting) lensAmount else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetLensA"
    )
    val animatedRefraction by animateFloatAsState(
        targetValue = if (isInteracting) restRefraction else 0.20f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetRefr"
    )

    val animatedShadow by animateDpAsState(
        targetValue = if (isInteracting) 30.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetShadow"
    )
    val animatedBlur by animateDpAsState(
        targetValue = if (isInteracting) 0.50.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetBlur"
    )

    val animatedInnerShadowRadius by animateDpAsState(
        targetValue = if (isInteracting) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetInnerShadowR"
    )
    val animatedInnerShadowAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 0.45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "sheetInnerShadowA"
    )

    val animatedTint by animateColorAsState(
        targetValue = if (isInteracting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "sheetTint"
    )
    val animatedTintAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 0.15f else 0.20f,
        animationSpec = tween(300),
        label = "sheetTintA"
    )

    val pillShape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .bubbleFluidity(targetScale = 1.08f, pullFactor = 0.08f)
    ) {
        LiquidGlassPanel(
            backdrop = backdrop,
            modifier = Modifier
                .size(highlighterWidth, highlighterHeight)
                .shinyGlare(
                    shape = pillShape,
                    intensity = if (isInteracting) LiquidGlassTokens.GlareFactor else LiquidGlassTokens.GlareRestAlpha,
                ),
            shape = pillShape,
            config = LiquidGlassPresets.IconButton.copy(
                blurRadius = animatedBlur,
                lensHeight = animatedLensHeight,
                lensAmount = animatedLensAmount,
                surfaceTint = animatedTint,
                surfaceTintAlpha = animatedTintAlpha,
                restRefraction = animatedRefraction,
                shadowRadius = animatedShadow,
                depthEffect = true,
                highlight = LiquidGlassPresets.IconButton.highlight.copy(
                    alpha = if (isInteracting) LiquidGlassTokens.GlareFactor else LiquidGlassTokens.GlareRestAlpha
                ),
                innerShadow = InnerShadow(
                    radius = animatedInnerShadowRadius,
                    color = Color.Black.copy(alpha = animatedInnerShadowAlpha),
                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp),
                )
            ),
            content = {}
        )
    }
}
