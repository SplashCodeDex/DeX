package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.ui.components.glass.LiquidGlassConfig
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop

/**
 * Living dynamic pill button that inherits all expandable and non-expandable properties
 * directly from [DynamicPillButton] for the media tray selection counter action.
 *
 * Features:
 * 1. Fluid, direction-aware entry & exit animation using [DynamicMotionConfig] overshoot springs
 *    anchored at the leading edge ([ExpansionAnchor.Start] / [TransformOrigin.Center]).
 * 2. In collapsed state: 56.dp circular capsule displaying the Send icon with count badge.
 * 3. In expanded state: Stadium pill displaying "Send ($count)" + Clear (✕) dismiss button.
 * 4. Preserves last non-zero count during exit animation so content never flashes or pops.
 */
@Composable
fun SelectedItemsCounterPill(
    selectedCount: Int,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    totalAvailableWidthDp: Dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isSiblingExpanded: Boolean = false,
    dimensions: DynamicDimensions = DynamicDimensions.SelectionCounterPill,
    motion: DynamicMotionConfig = DynamicMotionConfig.Default,
    fluidity: DynamicFluidityConfig = DynamicFluidityConfig.Default,
    expandedFluidityConfig: ExpandedBubbleFluidityConfig = ExpandedBubbleFluidityConfig.Inherit,
    shadows: DynamicShadowVariants = DynamicShadowVariants.Default,
    colors: DynamicColorVariants = DynamicColorVariants.Default,
    contentBlur: DynamicContentBlurConfig = DynamicContentBlurConfig.Default,
    anticipation: DynamicAnticipationConfig = DynamicAnticipationConfig.Default,
    dismissOnOutsideTap: Boolean = true,
) {
    // Preserve last positive count so content does not flash to 0 during the collapse/exit spring
    var lastPositiveCount by remember { mutableIntStateOf(if (selectedCount > 0) selectedCount else 1) }
    if (selectedCount > 0) {
        lastPositiveCount = selectedCount
    }
    val displayCount = if (selectedCount > 0) selectedCount else lastPositiveCount

    val rowSpace = (totalAvailableWidthDp - 12.dp - 16.dp).coerceAtLeast(0.dp)
    val fullAvailableCenterWidth = (rowSpace - 132.dp).coerceAtLeast(140.dp)
    val resolvedExpandedWidth = fullAvailableCenterWidth.coerceIn(160.dp, 210.dp)

    val activeDimensions = remember(dimensions, resolvedExpandedWidth) {
        dimensions.copy(expandedWidth = resolvedExpandedWidth)
    }

    val surfaceTint = MaterialTheme.colorScheme.primary
    val contentTint = MaterialTheme.colorScheme.onPrimary

    val glassConfig = LiquidGlassPresets.NavBar.copy(
        shape = CircleShape,
        surfaceTint = surfaceTint,
        surfaceTintAlpha = 0.85f,
    ).withShadowProperties(LiquidGlassShadowProperties.Expanded)

    // Dynamic Entry & Exit Spring Specifications matching DynamicMotionConfig
    val enterSpring = spring<Float>(
        dampingRatio = motion.expandDampingRatio,
        stiffness = motion.stiffness
    )
    val exitSpring = spring<Float>(
        dampingRatio = motion.collapseDampingRatio,
        stiffness = motion.stiffness
    )
    val enterWidthSpring = spring<IntSize>(
        dampingRatio = motion.expandDampingRatio,
        stiffness = motion.stiffness
    )
    val exitWidthSpring = spring<IntSize>(
        dampingRatio = motion.collapseDampingRatio,
        stiffness = motion.stiffness
    )

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = fadeIn(animationSpec = tween(durationMillis = 140)) +
                scaleIn(
                    initialScale = 0.65f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                    animationSpec = enterSpring
                ) +
                expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = enterWidthSpring
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
               scaleOut(
                   targetScale = 0.65f,
                   transformOrigin = TransformOrigin(0f, 0.5f),
                   animationSpec = exitSpring
               ) +
               shrinkHorizontally(
                   shrinkTowards = Alignment.Start,
                   animationSpec = exitWidthSpring
               ),
        modifier = modifier
    ) {
        DynamicPillButton(
            isExpanded = isExpanded && !isSiblingExpanded,
            onExpandedChange = onExpandedChange,
            dimensions = activeDimensions,
            motion = motion,
            fluidity = fluidity,
            expandedFluidityConfig = expandedFluidityConfig,
            shadows = shadows,
            colors = colors,
            contentBlur = contentBlur,
            collapsedGlassConfig = glassConfig,
            expandedGlassConfig = glassConfig,
            backdrop = backdrop,
            dismissOnOutsideTap = dismissOnOutsideTap,
            expansionAnchor = ExpansionAnchor.Start,
            anticipation = anticipation,
            collapsedContent = {
                // Collapsed state: Send icon + counter badge
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Send,
                        contentDescription = "Send",
                        tint = contentTint,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 10.dp)
                            .size(16.dp)
                            .background(contentTint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (displayCount > 99) "99+" else displayCount.toString(),
                            color = surfaceTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            },
            expandedContent = { collapse ->
                // Expanded state: Send action + Clear (✕) button
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSend
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Send,
                            contentDescription = "Send",
                            tint = contentTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send ($displayCount)",
                            color = contentTint,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    collapse()
                                    onClear()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Close,
                            contentDescription = "Clear Selection",
                            tint = contentTint.copy(alpha = 0.80f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        )
    }
}
