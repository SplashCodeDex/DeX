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
import androidx.compose.runtime.mutableLongStateOf
import com.dexstudios.dex.ui.util.Formatters
import android.net.Uri
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.ExpandedSelectionDispatchContent

/**
 * Living dynamic pill button that directly inherits all expandable and non-expandable properties
 * from [DynamicPillButton] for the media tray selection counter action.
 *
 * Apple Dynamic Island 3-Tier Architecture:
 * 1. [DynamicPillStage.Collapsed] (0.dp): At rest when no items are selected ([selectedCount] == 0).
 * 2. [DynamicPillStage.Compact] (56.dp): When items are selected ([selectedCount] > 0) but a sibling
 *    (Profile, History, Devices) takes center stage. Morphs down to a streamlined 56.dp circular capsule
 *    displaying Send icon + rolling count badge, preserving spatial harmony without disappearing.
 * 3. [DynamicPillStage.Expanded] (160.dp - 210.dp): Full stadium pill with Send action, total payload
 *    size telemetry, and centralized [DynamicDismissButton] ('✕') when items are selected and no sibling is expanded.
 * 4. Preserves last non-zero count and size during the collapse spring so content never flashes or pops.
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

    // Mechanical haptic tick on each count increment / decrement
    LaunchedEffect(selectedCount) {
        if (selectedCount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    val resolvedExpandedWidth = fullAvailableCenterWidth.coerceIn(160.dp, 210.dp)
    val resolvedFullIslandWidth = if (dimensions.fullIslandWidth != Dp.Unspecified) dimensions.fullIslandWidth else rowSpace

    val activeDimensions = remember(dimensions, resolvedExpandedWidth, resolvedFullIslandWidth) {
        dimensions.copy(
            collapsedWidth = 0.dp,
            collapsedHeight = 56.dp,
            compactWidth = 56.dp,
            compactHeight = 56.dp,
            expandedWidth = resolvedExpandedWidth,
            expandedHeight = 56.dp,
            fullIslandWidth = resolvedFullIslandWidth,
            fullIslandHeight = dimensions.fullIslandHeight
        )
    }

    val surfaceTint = MaterialTheme.colorScheme.primary
    val contentTint = MaterialTheme.colorScheme.onPrimary

    val glassConfig = LiquidGlassPresets.NavBar.copy(
        shape = CircleShape,
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
                    onBigIslandExpandedChange(false)
                    onExpandedChange(true)
                }
                DynamicPillStage.Compact, DynamicPillStage.Collapsed -> {
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
            // Tier 1: Apple compact 56.dp circular capsule (Send icon + rolling odometer count)
            @OptIn(ExperimentalFoundationApi::class)
            Row(
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
                            onBigIslandExpandedChange(true)
                            onLongPress?.invoke()
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = MaterialSymbols.Send,
                    contentDescription = "Send",
                    tint = contentTint,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                RollingOdometerText(
                    count = displayCount,
                    color = contentTint,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    motion = motion
                )
            }
        },
        expandedContent = { collapse ->
            // Tier 2: Full stadium pill with Send action + payload telemetry + centralized DynamicDismissButton
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                @OptIn(ExperimentalFoundationApi::class)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSend,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBigIslandExpandedChange(true)
                                onLongPress?.invoke()
                            }
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Send (",
                                color = contentTint,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (formattedSize.isNotEmpty()) 14.sp else 15.sp,
                                maxLines = 1
                            )
                            RollingOdometerText(
                                count = displayCount,
                                color = contentTint,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (formattedSize.isNotEmpty()) 14.sp else 15.sp,
                                motion = motion
                            )
                            Text(
                                text = ")",
                                color = contentTint,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (formattedSize.isNotEmpty()) 14.sp else 15.sp,
                                maxLines = 1
                            )
                        }
                        if (formattedSize.isNotEmpty()) {
                            Text(
                                text = formattedSize,
                                color = contentTint.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
        },
        fullIslandContent = { collapse ->
            // Tier 3: Apple Dynamic Island Big Pill displaying selected items preview and destination devices
            ExpandedSelectionDispatchContent(
                selectedUris = selectedUris,
                totalSizeBytes = displaySize,
                devices = devices,
                onSendToDevice = { device ->
                    onSendToDevice(device)
                    collapse()
                },
                onDismiss = {
                    onBigIslandExpandedChange(false)
                },
                onPairDevice = onPairDevice
            )
        }
    )
}
