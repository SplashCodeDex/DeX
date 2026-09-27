package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.buttons.DeXButton
import com.dexstudios.dex.core.designsystem.components.buttons.DeXButtonDefaults
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * AirDrop-style Alert Dialog (inspired by Screenshot 2).
 *
 * Provides:
 * - App icon header with optional badge
 * - Bold title + descriptive message
 * - Generous rounded preview slot (photo, document, or custom graphic)
 * - Dual rounded pill buttons (Decline & Accept) with [bubbleFluidity] press squish
 * - Loading indicator support during action execution
 */
@Composable
fun AlertDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconResource: DrawableResource? = null,
    iconPainter: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badgeResource: DrawableResource? = null,
    badgePainter: Painter? = null,
    previewContent: @Composable (() -> Unit)? = null,
    negativeButtonText: String = "Decline",
    positiveButtonText: String = "Accept",
    isPositiveActionLoading: Boolean = false,
    isNegativeActionLoading: Boolean = false,
    onNegativeAction: () -> Unit,
    onPositiveAction: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
) {
    FluidOverlaySurface(
        modifier = modifier,
        targetWidth = OverlayPhysics.ALERT_DIALOG_WIDTH,
        showHoverCloseButton = false,
        onDismiss = onDismiss,
        onHoverChanged = onHoverChanged,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Icon + optional badge + Title / Message
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Leading Icon Badge with secondary overlapping avatar badge
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val painter = when {
                        iconPainter != null -> iconPainter
                        iconResource != null -> painterResource(iconResource)
                        else -> null
                    }
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = iconTint,
                        )
                    }

                    val effectiveBadgePainter = when {
                        badgePainter != null -> badgePainter
                        badgeResource != null -> painterResource(badgeResource)
                        else -> null
                    }

                    if (effectiveBadgePainter != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = effectiveBadgePainter,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape),
                                tint = Color.Unspecified,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Preview Thumbnail Slot (if supplied)
            if (previewContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    previewContent()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Dual Pills (Decline = surfaceVariant, Accept = primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Negative Action (Decline)
                AlertActionButton(
                    text = negativeButtonText,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    isLoading = isNegativeActionLoading,
                    onClick = onNegativeAction,
                    modifier = Modifier.weight(1f),
                )

                // Positive Action (Accept)
                AlertActionButton(
                    text = positiveButtonText,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    isLoading = isPositiveActionLoading,
                    onClick = onPositiveAction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun AlertActionButton(text: String, containerColor: Color, contentColor: Color, isLoading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DeXButton(
        onClick = onClick,
        modifier = modifier,
        enabled = !isLoading,
        style = remember {
            DeXButtonDefaults.stadium.copy(
                fluidity = DynamicFluidityConfig(enabled = true, pressScale = 0.95f, pullFactor = 0.05f),
            )
        },
        containerColor = containerColor,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
