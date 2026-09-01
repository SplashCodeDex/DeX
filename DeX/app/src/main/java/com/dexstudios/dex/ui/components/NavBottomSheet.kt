package com.dexstudios.dex.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassTokens
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.shadow.Shadow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 3-Tier Detents for the Anchored Dynamic Floating Card:
 * - Half: 50% screen height with 8dp floating gap
 * - High: 80% screen height with 5dp floating gap
 * - Full: 100% fullscreen with 0dp gap
 */
enum class SheetTier(val fraction: Float) {
    Half(0.50f),
    High(0.80f),
    Full(1.00f);
}

enum class SheetExpandedMode {
    Photos,
    Audio,
    Files,
    History;
}

@Composable
fun NavBottomSheet(
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    initialTier: SheetTier = SheetTier.Half,
    dragEnabled: Boolean = true,
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        val halfGapPx = with(density) { 6.dp.toPx() }
        val highGapPx = with(density) { 3.dp.toPx() }

        // Heights for each tier (card height from anchored bottom)
        val halfHeightPx = (totalHeightPx * 0.50f) - halfGapPx
        val highHeightPx = (totalHeightPx * 0.80f) - highGapPx
        val fullHeightPx = totalHeightPx

        val tierHeights = listOf(
            SheetTier.Half to halfHeightPx,
            SheetTier.High to highHeightPx,
            SheetTier.Full to fullHeightPx
        )

        fun tierHeight(tier: SheetTier): Float = when (tier) {
            SheetTier.Half -> halfHeightPx
            SheetTier.High -> highHeightPx
            SheetTier.Full -> fullHeightPx
        }

        // Active animated height of the anchored floating card
        val animatableHeight = remember(totalHeightPx) {
            Animatable(0f)
        }

        val springSpec = remember {
            spring<Float>(
                dampingRatio = 0.74f,
                stiffness = 350f
            )
        }

        // Closest settled tier based on current active height
        val currentTier by remember {
            derivedStateOf {
                val current = animatableHeight.value
                tierHeights.minByOrNull { abs(it.second - current) }?.first ?: SheetTier.Half
            }
        }

        fun expandTo(tier: SheetTier, triggerHaptic: Boolean = false) {
            if (triggerHaptic) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            scope.launch {
                animatableHeight.animateTo(tierHeight(tier), springSpec)
            }
        }

        fun collapseToHalf(triggerHaptic: Boolean = false) {
            if (triggerHaptic) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            scope.launch {
                animatableHeight.animateTo(halfHeightPx, springSpec)
            }
        }

        // Morph up from 0 to initial tier height on entry or when app resumes
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, totalHeightPx) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (totalHeightPx > 0f) {
                        scope.launch {
                            animatableHeight.animateTo(tierHeight(initialTier), springSpec)
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
        var dragStartTier by remember { mutableStateOf(SheetTier.Half) }

        // Live expansion fraction (0.0f at Half/50% to 1.0f at Full/100%)
        val expansionFraction by remember {
            derivedStateOf {
                if (fullHeightPx <= halfHeightPx) 0f
                else ((animatableHeight.value - halfHeightPx) / (fullHeightPx - halfHeightPx)).coerceIn(0f, 1f)
            }
        }

        // Dynamic floating margin: 6dp at 50%, 3dp at 80%, 0dp at 100% fullscreen
        val currentGap by remember {
            derivedStateOf {
                val h = animatableHeight.value
                when {
                    h <= halfHeightPx -> 6.dp
                    h <= highHeightPx -> {
                        val f = if (highHeightPx > halfHeightPx) ((h - halfHeightPx) / (highHeightPx - halfHeightPx)).coerceIn(0f, 1f) else 0f
                        (6f - 3f * f).dp // 6dp -> 3dp
                    }
                    else -> {
                        val f = if (fullHeightPx > highHeightPx) ((h - highHeightPx) / (fullHeightPx - highHeightPx)).coerceIn(0f, 1f) else 0f
                        (3f - 3f * f).dp // 3dp -> 0dp
                    }
                }
            }
        }

        // Dynamic corner radius: 4 rounded corners at 50% & 80%, seamless top corners at 100% fullscreen
        val sheetShape by remember {
            derivedStateOf {
                val h = animatableHeight.value
                when {
                    h <= highHeightPx -> {
                        val f = if (highHeightPx > 0f) (h / highHeightPx).coerceIn(0f, 1f) else 0f
                        val r = (44f - 8f * f).dp // approx 39dp at 50% -> 36dp at 80%
                        RoundedCornerShape(r)
                    }
                    else -> {
                        val f = if (fullHeightPx > highHeightPx) ((h - highHeightPx) / (fullHeightPx - highHeightPx)).coerceIn(0f, 1f) else 0f
                        val topR = (36f - 8f * f).dp // 36dp at 80% -> 28dp at 100%
                        val bottomR = (36f * (1f - f)).dp // 36dp at 80% -> 0dp at 100% fullscreen
                        RoundedCornerShape(
                            topStart = topR,
                            topEnd = topR,
                            bottomStart = bottomR,
                            bottomEnd = bottomR
                        )
                    }
                }
            }
        }

        // Android 14+ Predictive Back Gesture Handling
        PredictiveBackHandler(enabled = dragEnabled) { progressFlow ->
            val startHeight = animatableHeight.value
            val isExpanded = expansionFraction > 0.05f
            try {
                progressFlow.collect { backEvent ->
                    val progress = backEvent.progress
                    if (isExpanded) {
                        // Smoothly interpolate down toward 50% resting height
                        val targetH = startHeight - (progress * (startHeight - halfHeightPx))
                        animatableHeight.snapTo(targetH)
                    } else {
                        // Predictive scale-down and slide down below 50%
                        val targetH = halfHeightPx * (1f - progress * 0.40f)
                        animatableHeight.snapTo(targetH)
                    }
                }
                // Gesture committed
                if (isExpanded) {
                    collapseToHalf()
                } else {
                    scope.launch {
                        animatableHeight.animateTo(0f, springSpec)
                        onDismiss()
                    }
                }
            } catch (_: CancellationException) {
                // Gesture cancelled by user
                if (isExpanded) {
                    scope.launch {
                        animatableHeight.animateTo(startHeight, springSpec)
                    }
                } else {
                    collapseToHalf()
                }
            }
        }

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

        // 2. Interactive Click-Outside Dismiss Area (Above the Anchored Floating Card)
        val currentH = animatableHeight.value
        val isBelowHalf = currentH < halfHeightPx
        val pullDownProgress = if (isBelowHalf && halfHeightPx > 0f) {
            ((halfHeightPx - currentH) / halfHeightPx).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Below 50%: hold layout height at halfHeightPx so internal elements don't get crushed or clipped,
        // and apply physics-based translation and scale-down (opaque solid card without fading).
        val cardHeight = if (isBelowHalf) halfHeightPx else currentH
        val cardTranslationY = pullDownProgress * (halfHeightPx * 0.95f)
        val cardScale = 1f - (pullDownProgress * 0.16f)
        val cardAlpha = 1f

        val currentCardTopPx = totalHeightPx - (if (isBelowHalf) (halfHeightPx - cardTranslationY) else currentH)
        val topAreaHeightDp = with(density) { currentCardTopPx.coerceAtLeast(0f).toDp() }
        if (topAreaHeightDp > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topAreaHeightDp)
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (dragEnabled) {
                                if (expansionFraction > 0.05f) {
                                    collapseToHalf()
                                } else {
                                    scope.launch {
                                        animatableHeight.animateTo(0f, springSpec)
                                        onDismiss()
                                    }
                                }
                            }
                            // When drag is disabled the click is consumed (no-op): the sheet stays locked
                        }
                    )
            )
        }

        // 3. True Anchored Floating Card: 50% (8dp gap) -> 80% (5dp gap) -> 100% (fullscreen 0dp gap)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = currentGap, end = currentGap, bottom = currentGap)
                .fillMaxWidth()
                .height(with(density) { cardHeight.coerceAtLeast(0f).toDp() })
                .graphicsLayer {
                    translationY = cardTranslationY
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                }
                .shadow(
                    elevation = 16.dp,
                    shape = sheetShape,
                    ambientColor = Color.Black.copy(alpha = 0.20f),
                    spotColor = Color.Black.copy(alpha = 0.30f),
                    clip = false
                )
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(totalHeightPx, dragEnabled) {
                    val dismissThresholdPx = with(density) { 50.dp.toPx() }
                    val flingVelocityThresholdPx = with(density) { 350.dp.toPx() }
                    val dragCommitThresholdPx = with(density) { 36.dp.toPx() }
                    val maxOverscrollPx = with(density) { 40.dp.toPx() }
                    val maxAllowedHeightPx = fullHeightPx + maxOverscrollPx
                    val velocityTracker = VelocityTracker()

                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragStartTier = currentTier
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            isDragging = false
                            val velocityY = velocityTracker.calculateVelocity().y // + is down, - is up
                            val h = animatableHeight.value

                            // 1. Fast downward fling (> 350 dp/s)
                            if (velocityY > flingVelocityThresholdPx) {
                                if (h <= halfHeightPx + with(density) { 30.dp.toPx() }) {
                                    // Fast fling down from near or below 50%: dismiss immediately with haptic tick
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        animatableHeight.animateTo(0f, springSpec)
                                        onDismiss()
                                    }
                                } else {
                                    // Fling down from 100% or 80%: collapse directly to 50% without intermediate stall
                                    collapseToHalf(triggerHaptic = false)
                                }
                            }
                            // 2. Fast upward fling (< -350 dp/s)
                            else if (velocityY < -flingVelocityThresholdPx) {
                                val nextTier = if (dragStartTier == SheetTier.Half) {
                                    if (h > highHeightPx) SheetTier.Full else SheetTier.High
                                } else {
                                    SheetTier.Full
                                }
                                expandTo(nextTier, triggerHaptic = (nextTier != dragStartTier))
                            }
                            // 3. Position-based settling (effortless directional commit)
                            else {
                                if (h <= halfHeightPx - dismissThresholdPx) {
                                    // Dragged down past dismiss threshold
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        animatableHeight.animateTo(0f, springSpec)
                                        onDismiss()
                                    }
                                } else if (h < halfHeightPx) {
                                    // Small downward pull below 50%: spring back up to 50%
                                    collapseToHalf(triggerHaptic = false)
                                } else if (h > fullHeightPx) {
                                    // Overscroll released above 100%: spring back to 100%
                                    expandTo(SheetTier.Full, triggerHaptic = false)
                                } else when (dragStartTier) {
                                    SheetTier.Full -> {
                                        // From 100%: swiping down commits to 50% if pulled down by threshold
                                        if (h <= fullHeightPx - dragCommitThresholdPx) {
                                            collapseToHalf(triggerHaptic = false)
                                        } else {
                                            expandTo(SheetTier.Full, triggerHaptic = false)
                                        }
                                    }
                                    SheetTier.High -> {
                                        // From 80%: swiping down commits to 50%, swiping up commits to 100%
                                        if (h <= highHeightPx - dragCommitThresholdPx) {
                                            collapseToHalf(triggerHaptic = true)
                                        } else if (h >= highHeightPx + dragCommitThresholdPx) {
                                            expandTo(SheetTier.Full, triggerHaptic = true)
                                        } else {
                                            expandTo(SheetTier.High, triggerHaptic = false)
                                        }
                                    }
                                    SheetTier.Half -> {
                                        // From 50%: swiping up commits to 80% (or 100% if dragged high)
                                        if (h >= fullHeightPx - dragCommitThresholdPx) {
                                            expandTo(SheetTier.Full, triggerHaptic = true)
                                        } else if (h >= halfHeightPx + dragCommitThresholdPx) {
                                            expandTo(SheetTier.High, triggerHaptic = true)
                                        } else {
                                            collapseToHalf(triggerHaptic = false)
                                        }
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val h = animatableHeight.value
                            val nearest = if (h < halfHeightPx) halfHeightPx
                            else if (h > fullHeightPx) fullHeightPx
                            else (tierHeights.minByOrNull { abs(it.second - h) }?.second ?: halfHeightPx)
                            scope.launch {
                                animatableHeight.animateTo(nearest, springSpec)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val h = animatableHeight.value
                            // Dragging up is negative (increases height), dragging down is positive (decreases height)
                            val deltaHeight = -dragAmount
                            val adjustedDelta = when {
                                h < halfHeightPx && deltaHeight < 0f -> deltaHeight * 0.55f // Below 50% elastic resistance
                                h > fullHeightPx && deltaHeight > 0f -> deltaHeight * 0.28f // Above 100% overscroll resistance
                                else -> deltaHeight
                            }
                            val newTargetH = (h + adjustedDelta).coerceIn(0f, maxAllowedHeightPx)
                            scope.launch {
                                animatableHeight.snapTo(newTargetH)
                            }
                        }
                    )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val isDark = isSystemInDarkTheme()
            val sheetBgColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White
            val glassPreset = LiquidGlassPresets.ProfileIconButton

            val sheetSurfaceModifier = if (backdrop != null) {
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { sheetShape },
                        effects = { /* Pure highlight only, no distortion/blur */ },
                        highlight = { glassPreset.highlight },
                        shadow = {
                            Shadow(
                                radius = 24.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.45f else 0.16f),
                                offset = DpOffset(0.dp, (-4).dp)
                            )
                        },
                        innerShadow = { glassPreset.innerShadow },
                        onDrawSurface = {
                            drawRect(sheetBgColor)
                        }
                    )
            } else {
                Modifier
                    .fillMaxSize()
                    .clip(sheetShape)
                    .background(sheetBgColor)
            }

            // Card Surface: Anchored Card with Authentic LiquidGlass Specular Glare
            Box(
                modifier = sheetSurfaceModifier
            ) {
                // Card Inner Content
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Clean drag handle at the top of the card
                    DragHandle()

                    // Content receives live fraction, current tier, halfHeightDp, and expand/collapse actions
                    sheetContent(
                        expansionFraction,
                        currentTier,
                        with(density) { halfHeightPx.toDp() },
                        ::expandTo,
                        ::collapseToHalf
                    )
                }
            }
        }
    }
}

/**
 * Clean drag handle at the top of the card.
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
