package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Visual expansion state for the Dynamic Island style Notification Banner.
 */
enum class BannerMorphState {
    Compact, // 340x48dp compact pill
    Expanded, // 420x164dp rich card with preview & side-by-side progress/cancel
}

/**
 * Apple Dynamic Island / Live Activity style notification banner.
 *
 * Morphs seamlessly between [BannerMorphState.Compact] and [BannerMorphState.Expanded] with spring kinematics.
 */
@Composable
fun NotificationBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    iconResource: DrawableResource? = null,
    iconPainter: Painter? = null,
    iconTint: Color? = null,
    iconBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    progress: Float? = null, // 0.0f to 1.0f or null for none
    initialMorphState: BannerMorphState = BannerMorphState.Compact,
    allowInteractiveMorph: Boolean = true,
    trailingPreview: @Composable (() -> Unit)? = null,
    expandedContent: @Composable (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var morphState by remember(initialMorphState) { mutableStateOf(initialMorphState) }

    val targetWidth: Dp = when (morphState) {
        BannerMorphState.Compact -> OverlayPhysics.BANNER_COMPACT_WIDTH
        BannerMorphState.Expanded -> OverlayPhysics.BANNER_EXPANDED_WIDTH
    }

    val targetHeight: Dp = when (morphState) {
        BannerMorphState.Compact -> OverlayPhysics.BANNER_COMPACT_HEIGHT
        BannerMorphState.Expanded -> OverlayPhysics.BANNER_EXPANDED_HEIGHT
    }

    FluidOverlaySurface(
        modifier = modifier,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        showHoverCloseButton = false, // Tap to morph or swipe to dismiss
        onDismiss = onDismiss,
        onHoverChanged = onHoverChanged,
        onClick = {
            if (allowInteractiveMorph) {
                morphState = when (morphState) {
                    BannerMorphState.Compact -> BannerMorphState.Expanded
                    BannerMorphState.Expanded -> BannerMorphState.Compact
                }
            }
            onClick?.invoke()
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = morphState,
                transitionSpec = {
                    fadeIn(OverlayPhysics.EnterFloatSpring) togetherWith fadeOut(OverlayPhysics.ExitTween)
                },
                label = "bannerMorphContent",
            ) { state ->
                when (state) {
                    BannerMorphState.Compact -> {
                        // Collapsed Compact Pill (340x48dp): Leading progress spinner/icon, title + progress, trailing Cancel button
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 10.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (progress != null) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    )
                                }
                            } else {
                                BannerIconBadge(
                                    iconResource = iconResource,
                                    iconPainter = iconPainter,
                                    iconTint = iconTint,
                                    backgroundColor = iconBackgroundColor,
                                    size = 28.dp,
                                )
                            }

                            val compactText = if (progress != null) {
                                "$title • ${(progress * 100).toInt()}%"
                            } else if (subtitle != null) {
                                "$title • $subtitle"
                            } else {
                                title
                            }

                            Text(
                                text = compactText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            if (onActionClick != null) {
                                Box(
                                    modifier = Modifier
                                        .bubbleFluidity(targetScale = 0.93f, pullFactor = 0.04f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            onActionClick()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else if (badgeText != null) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    BannerMorphState.Expanded -> {
                        // Expanded Rich Card (420x164dp): Compact preview, aligned metrics, progress bar side-by-side with Cancel button
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Top Section: Details on the left, reduced compact preview on the right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Left details column
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // Top Row: Origin Badge + Status Pill
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        BannerIconBadge(
                                            iconResource = iconResource,
                                            iconPainter = iconPainter,
                                            iconTint = iconTint,
                                            backgroundColor = iconBackgroundColor,
                                            size = 28.dp,
                                        )
                                        if (badgeText != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }

                                    // Hero Title
                                    Text(
                                        text = title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    // Subtitle / Speed metric
                                    if (subtitle != null) {
                                        Text(
                                            text = subtitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                // Right visual preview (Reduced compact thumbnail)
                                if (trailingPreview != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 88.dp, height = 72.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                    ) {
                                        trailingPreview()
                                    }
                                }
                            }

                            // Bottom Section: Progress bar and Cancel button side-by-side
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                if (expandedContent != null) {
                                    expandedContent()
                                } else if (onActionClick != null) {
                                    Box(
                                        modifier = Modifier
                                            .bubbleFluidity(targetScale = 0.93f, pullFactor = 0.04f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                            .clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                onActionClick()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerIconBadge(iconResource: DrawableResource?, iconPainter: Painter?, iconTint: Color?, backgroundColor: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        val painter = when {
            iconPainter != null -> iconPainter
            iconResource != null -> painterResource(iconResource)
            else -> null
        }
        if (painter != null) {
            androidx.compose.material3.Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                tint = iconTint ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
