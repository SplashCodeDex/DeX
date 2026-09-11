package com.dexstudios.dex.ui.components.island

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop

import com.dexstudios.dex.ui.components.DynamicDismissButton
import com.dexstudios.dex.ui.components.DynamicDismissButtonDefaults
import com.dexstudios.dex.ui.components.DynamicDismissButtonSize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.dexstudios.dex.ui.util.Formatters
import android.net.Uri
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.ExpandedSelectionDispatchContent
import com.dexstudios.dex.ui.components.MediaThumbnailDisc

/**
 * Living dynamic pill button that directly inherits all expandable and non-expandable properties
 * from [DynamicPillButton] for the media tray selection counter action.
 *
 * Apple Dynamic Island 3-Tier Architecture:
 * 1. [DynamicPillStage.Collapsed] (0.dp): At rest when no items are selected ([selectedCount] == 0).
 * 2. [DynamicPillStage.Compact] (56.dp): When items are selected ([selectedCount] > 0) but a sibling
 *    (Profile, History, Devices) takes center stage or during sheet scroll. Morphs down to a 56.dp circular capsule
 *    displaying the latest selected media thumbnail disc + floating odometer count badge with elastic pulse.
 * 3. [DynamicPillStage.Expanded] (180.dp - 220.dp): Full stadium pill with stacked circular previews (up to 3 + overflow badge),
 *    total payload size telemetry, dedicated Send action button, and centralized [DynamicDismissButton] ('✕').
 *    - Tapping preview stack directly opens In-Island Inspection mode in the Big Island.
 *    - Tapping telemetry expands to standard Big Island gallery.
 *    - Tapping Send immediately dispatches to the primary/active target device.
 * 4. [DynamicPillStage.FullIsland] (~152.dp / ~248.dp): Big Island with pinned synchronized thumbnail strip,
 *    swipeable center carousel with rich semantic cards, and persistent bottom target device chips.
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
    onLongPress: (() -> Unit)? = null,
    totalSizeBytes: Long = 0L,
    backdrop: Backdrop? = null,
    isSiblingExpanded: Boolean = false,
    selectedUris: List<Uri> = emptyList(),
    devices: List<DiscoveredDevice> = emptyList(),
    onSendToDevice: (DiscoveredDevice) -> Unit = {},
    onPairDevice: () -> Unit = {},
    onRemoveUri: (Uri) -> Unit = {},
    onDirectInspect: ((Int) -> Unit)? = null,
    isBigIslandExpanded: Boolean = false,
    onBigIslandExpandedChange: (Boolean) -> Unit = {},
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
    val haptic = LocalHapticFeedback.current

    // In-Island Preview Inspection index state (null = standard gallery ~152dp; non-null = inspection ~248dp)
    var previewingIndex by remember { mutableStateOf<Int?>(null) }

    // Floating count badge elastic pulse swell (1.25x overshoot) on each count change
    val badgePulse = remember { Animatable(1f) }
    LaunchedEffect(selectedCount) {
        if (selectedCount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            badgePulse.snapTo(1.25f)
            badgePulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.40f,
                    stiffness = 380f
                )
            )
        } else {
            previewingIndex = null
        }
    }

    // Preserve last positive count so content does not flash to 0 during the collapse/exit spring
    var lastPositiveCount by remember { mutableIntStateOf(if (selectedCount > 0) selectedCount else 1) }
    if (selectedCount > 0) {
        lastPositiveCount = selectedCount
    }
    val displayCount = if (selectedCount > 0) selectedCount else lastPositiveCount

    // Preserve last positive total size during the collapse spring
    var lastPositiveSize by remember { mutableLongStateOf(if (totalSizeBytes > 0L) totalSizeBytes else 0L) }
    if (totalSizeBytes > 0L) {
        lastPositiveSize = totalSizeBytes
    }
    val displaySize = if (totalSizeBytes > 0L) totalSizeBytes else lastPositiveSize
    val formattedSize = remember(displaySize) {
        if (displaySize > 0L) Formatters.formatBytes(displaySize) else ""
    }

    val rowSpace = (totalAvailableWidthDp - 12.dp - 16.dp).coerceAtLeast(0.dp)
    val fullAvailableCenterWidth = (rowSpace - 132.dp).coerceAtLeast(140.dp)
    val resolvedExpandedWidth = fullAvailableCenterWidth.coerceIn(180.dp, 220.dp)
    val resolvedFullIslandWidth = if (dimensions.fullIslandWidth != Dp.Unspecified) dimensions.fullIslandWidth else rowSpace
    val resolvedFullIslandHeight = if (previewingIndex != null && selectedUris.isNotEmpty()) 248.dp else 152.dp

    val activeDimensions = remember(dimensions, resolvedExpandedWidth, resolvedFullIslandWidth, resolvedFullIslandHeight) {
        dimensions.copy(
            collapsedWidth = 0.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = resolvedExpandedWidth,
            expandedHeight = 56.dp,
            fullIslandWidth = resolvedFullIslandWidth,
            fullIslandHeight = resolvedFullIslandHeight
        )
    }

    val surfaceTint = MaterialTheme.colorScheme.primary
    val contentTint = MaterialTheme.colorScheme.onPrimary

    val glassConfig = LiquidGlassPresets.NavBar.copy(
        shape = RoundedCornerShape(28.dp),
        surfaceTint = surfaceTint,
        surfaceTintAlpha = 0.85f,
    ).withShadowProperties(LiquidGlassShadowProperties.Expanded)

    val profileIslandGlassConfig = LiquidGlassPresets.ProfileIsland.copy(
        shape = RoundedCornerShape(48.dp),
        surfaceTint = surfaceTint,
        surfaceTintAlpha = 0.85f,
    ).withShadowProperties(LiquidGlassShadowProperties.Expanded)

    // Apple Dynamic Island 4-Tier Spatial Regulation:
    val counterStage = when {
        selectedCount == 0 -> DynamicPillStage.Collapsed
        isBigIslandExpanded -> DynamicPillStage.FullIsland
        isSiblingExpanded -> DynamicPillStage.Compact
        isExpanded -> DynamicPillStage.Expanded
        else -> DynamicPillStage.Compact
    }

    DynamicPillButton(
        stage = counterStage,
        onStageChange = { newStage ->
            when (newStage) {
                DynamicPillStage.FullIsland -> {
                    onBigIslandExpandedChange(true)
                    onExpandedChange(true)
                }
                DynamicPillStage.Expanded -> {
                    previewingIndex = null
                    onBigIslandExpandedChange(false)
                    onExpandedChange(true)
                }
                DynamicPillStage.Compact, DynamicPillStage.Collapsed -> {
                    previewingIndex = null
                    onBigIslandExpandedChange(false)
                    onExpandedChange(false)
                }
            }
        },
        dimensions = activeDimensions,
        motion = motion,
        fluidity = fluidity,
        expandedFluidityConfig = expandedFluidityConfig,
        shadows = shadows,
        colors = colors,
        contentBlur = contentBlur,
        collapsedGlassConfig = glassConfig,
        compactGlassConfig = glassConfig,
        expandedGlassConfig = glassConfig,
        fullIslandGlassConfig = profileIslandGlassConfig,
        backdrop = backdrop,
        dismissOnOutsideTap = dismissOnOutsideTap,
        expansionAnchor = ExpansionAnchor.Start,
        anticipation = anticipation,
        modifier = modifier,
        collapsedContent = {
            // Tier 0: Resting collapsed state at 0 width
            Box(modifier = Modifier.fillMaxSize())
        },
        compactContent = {
            // Tier 1: Apple compact 56.dp circular capsule (Latest media thumbnail disc + floating odometer count badge)
            val latestUri = selectedUris.lastOrNull()
            @OptIn(ExperimentalFoundationApi::class)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandedChange(true)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            previewingIndex = null
                            onBigIslandExpandedChange(true)
                            onLongPress?.invoke()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Center media thumbnail disc
                if (latestUri != null) {
                    MediaThumbnailDisc(
                        uri = latestUri,
                        size = 38.dp,
                        contentTint = contentTint,
                        borderWidth = 1.dp,
                        borderColor = Color.White.copy(alpha = 0.40f)
                    )
                } else {
                    Icon(
                        imageVector = MaterialSymbols.Send,
                        contentDescription = "Send",
                        tint = contentTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Top-right floating count badge with elastic pulse
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .graphicsLayer {
                            scaleX = badgePulse.value
                            scaleY = badgePulse.value
                        }
                        .height(20.dp)
                        .widthIn(min = 20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, Color.White.copy(alpha = 0.70f), CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RollingOdometerText(
                        count = displayCount,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        motion = motion
                    )
                }
            }
        },
        expandedContent = { collapse ->
            // Tier 2: Middle stadium pill (~180-220dp) with stacked preview thumbnails, telemetry, Send, and '×'
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Stacked circular preview discs (up to 3 items + overflow badge)
                val previewUris = selectedUris.take(3)
                val stackWidth = if (previewUris.isEmpty()) 30.dp else (30.dp + ((previewUris.size - 1) * 14).dp + if (displayCount > 3) 20.dp else 0.dp)
                Box(
                    modifier = Modifier
                        .size(width = stackWidth, height = 32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                previewingIndex = 0
                                onBigIslandExpandedChange(true)
                                onDirectInspect?.invoke(0)
                            }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    previewUris.forEachIndexed { index, uri ->
                        Box(
                            modifier = Modifier
                                .padding(start = (index * 14).dp)
                                .zIndex((4 - index).toFloat())
                        ) {
                            MediaThumbnailDisc(
                                uri = uri,
                                size = 30.dp,
                                borderWidth = 1.dp,
                                borderColor = Color.White.copy(alpha = 0.50f)
                            )
                        }
                    }
                    if (displayCount > 3) {
                        Box(
                            modifier = Modifier
                                .padding(start = (previewUris.size * 14).dp)
                                .zIndex(5f)
                                .height(20.dp)
                                .widthIn(min = 20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, Color.White.copy(alpha = 0.60f), CircleShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${displayCount - 3}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Center: Selection telemetry label (tapping opens standard Big Island gallery)
                @OptIn(ExperimentalFoundationApi::class)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                previewingIndex = null
                                onBigIslandExpandedChange(true)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                previewingIndex = null
                                onBigIslandExpandedChange(true)
                                onLongPress?.invoke()
                            }
                        )
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingOdometerText(
                            count = displayCount,
                            color = contentTint,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            motion = motion
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        val itemSuffix = if (displayCount != 1) "s" else ""
                        Text(
                            text = "item$itemSuffix",
                            color = contentTint,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    if (formattedSize.isNotEmpty()) {
                        Text(
                            text = formattedSize,
                            color = contentTint.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right: Dedicated Send action button + Dismiss button ('×')
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(contentTint.copy(alpha = 0.18f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSend
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Send,
                            contentDescription = "Send",
                            tint = contentTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DynamicDismissButton(
                        onClick = {
                            collapse()
                            onClear()
                        },
                        size = DynamicDismissButtonSize.Medium,
                        colors = DynamicDismissButtonDefaults.colors(
                            containerColor = contentTint.copy(alpha = 0.14f),
                            contentColor = contentTint
                        )
                    )
                }
            }
        },
        fullIslandContent = { collapse ->
            // Tier 3: Apple Dynamic Island Big Board displaying inspection carousel and destination devices
            ExpandedSelectionDispatchContent(
                selectedUris = selectedUris,
                totalSizeBytes = displaySize,
                devices = devices,
                onSendToDevice = { device ->
                    onSendToDevice(device)
                    collapse()
                },
                onDismiss = {
                    previewingIndex = null
                    onBigIslandExpandedChange(false)
                },
                onPairDevice = onPairDevice,
                onRemoveUri = onRemoveUri,
                previewingIndex = previewingIndex,
                onPreviewingIndexChange = { previewingIndex = it }
            )
        }
    )
}
