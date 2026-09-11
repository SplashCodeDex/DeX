package com.dexstudios.dex.ui.components.island

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.remember
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
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LiquidGlassShadowProperties
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.Backdrop

/**
 * Living stadium action pill that dynamically expands beside the navbar pill whenever items
 * are selected in the media picker tray.
 *
 * Features:
 * 1. Direction-aware anticipation physics ([ExpansionAnchor.Start]) with +32dp nudge & 1.25x swell over 70ms.
 * 2. Overshoot spring bounds expansion ([DynamicMotionConfig.Default]).
 * 3. Tactile fluid touch response ([bubbleFluidity]).
 * 4. Dual action: Main pill area triggers [onSend]; dedicated circular dismiss icon triggers [onClear].
 * 5. Transient optical blur on count increments/decrements for analog odometer fluidity.
 * 6. Responsive spatial adaptation: automatically compacts to icon-only capsule when sibling is expanded.
 */
@Composable
fun SelectedItemsCounterPill(
    selectedCount: Int,
    onSend: () -> Unit,
    onClear: () -> Unit,
    totalAvailableWidthDp: Dp,
    backdrop: Backdrop?,
    isSiblingExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (selectedCount <= 0) return

    val haptic = LocalHapticFeedback.current
    val pillShape = CircleShape
    val height = DynamicDimensions.SelectionCounterPill.expandedHeight // 56.dp

    val rowSpace = (totalAvailableWidthDp - 12.dp - 16.dp).coerceAtLeast(0.dp)
    // Left navbar pill (56dp) + gap (10dp) + roller (56dp) + gap (10dp) = 132dp
    val fullAvailableCenterWidth = (rowSpace - 132.dp).coerceAtLeast(140.dp)
    val standardExpandedWidth = fullAvailableCenterWidth.coerceIn(160.dp, 210.dp)
    val compactWidth = 56.dp

    val targetWidth = if (isSiblingExpanded) compactWidth else standardExpandedWidth

    val anticipationState = rememberExpandingAnticipationPhysics(
        isExpanded = selectedCount > 0,
        triggerKey = selectedCount > 0,
        anchor = ExpansionAnchor.Start,
        motion = DynamicMotionConfig.Default,
        fluidity = DynamicFluidityConfig.Default,
    )
    val shouldExpandBounds = anticipationState.canExpandBounds

    val animatedWidth by animateDpAsState(
        targetValue = if (shouldExpandBounds) targetWidth else compactWidth,
        animationSpec = DynamicMotionConfig.Default.resolveSpringSpec(selectedCount > 0),
        label = "selectionCounterPillWidth"
    )

    val surfaceTint = MaterialTheme.colorScheme.primary
    val contentTint = MaterialTheme.colorScheme.onPrimary

    val glassConfig = LiquidGlassPresets.NavBar.copy(
        shape = pillShape,
        surfaceTint = surfaceTint,
        surfaceTintAlpha = 0.85f,
    ).withShadowProperties(LiquidGlassShadowProperties.Expanded)

    val blurMod = Modifier.transientContentBlur(
        trigger = selectedCount,
        config = DynamicContentBlurConfig.Default,
        motion = DynamicMotionConfig.Default,
    )

    Box(
        modifier = modifier
            .size(animatedWidth, height)
            .expandingAnticipation(anticipationState)
            .clip(pillShape)
            .bubbleFluidity(config = DynamicFluidityConfig.Default),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend()
            },
            backdrop = backdrop,
            config = glassConfig,
            width = animatedWidth,
            height = height,
            modifier = Modifier.size(animatedWidth, height)
        ) {
            if (isSiblingExpanded || animatedWidth < 120.dp) {
                // Compact mode: Send icon with mini count badge
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(pillShape)
                        .then(blurMod),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Send,
                        contentDescription = "Send",
                        tint = contentTint,
                        modifier = Modifier.size(22.dp)
                    )
                    // Small badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 10.dp)
                            .size(16.dp)
                            .background(contentTint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedCount > 99) "99+" else selectedCount.toString(),
                            color = surfaceTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            } else {
                // Full stadium pill: [ (Send) Send (3) | (✕) ]
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(pillShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Send click area
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
                            text = "Send ($selectedCount)",
                            color = contentTint,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = blurMod
                        )
                    }

                    // Divider & Clear Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        }
    }
}
