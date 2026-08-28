package com.dexstudios.dex.core.designsystem.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.designsystem.theme.OverlayPhysics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Semantic style variants for MessageToast.
 */
sealed class ToastVariant {
    object Info : ToastVariant()
    object Success : ToastVariant()
    object Warning : ToastVariant()
    object Error : ToastVariant()
    object Progress : ToastVariant()
    data class Lottie(val lottieJsonAsset: String) : ToastVariant()
}

/**
 * Compact pill-shaped status toast for corner notifications.
 * Wraps content width snugly and vertically centers action buttons / close triggers.
 */
@Composable
fun MessageToast(
    message: String,
    modifier: Modifier = Modifier,
    variant: ToastVariant = ToastVariant.Info,
    iconResource: DrawableResource? = null,
    iconPainter: Painter? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    showCloseButton: Boolean = (actionText == null),
    progress: Float? = null,
    onDismiss: (() -> Unit)? = null,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val badgeBgColor = when (variant) {
        ToastVariant.Info, is ToastVariant.Lottie -> MaterialTheme.colorScheme.primaryContainer
        ToastVariant.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ToastVariant.Warning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        ToastVariant.Error -> MaterialTheme.colorScheme.errorContainer
        ToastVariant.Progress -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconTintColor = when (variant) {
        ToastVariant.Info, is ToastVariant.Lottie -> MaterialTheme.colorScheme.primary
        ToastVariant.Success -> MaterialTheme.colorScheme.primary
        ToastVariant.Warning -> MaterialTheme.colorScheme.error
        ToastVariant.Error -> MaterialTheme.colorScheme.error
        ToastVariant.Progress -> MaterialTheme.colorScheme.primary
    }

    FluidOverlaySurface(
        modifier = modifier,
        targetWidth = OverlayPhysics.TOAST_WIDTH,
        targetHeight = OverlayPhysics.TOAST_HEIGHT,
        showHoverCloseButton = showCloseButton,
        onDismiss = onDismiss,
        onHoverChanged = onHoverChanged,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .height(OverlayPhysics.TOAST_HEIGHT)
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = if (showCloseButton && onDismiss != null) {
                        38.dp
                    } else if (actionText != null) {
                        10.dp
                    } else {
                        16.dp
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Variant Icon / Progress indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center,
            ) {
                if (variant == ToastVariant.Progress && progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (variant is ToastVariant.Lottie) {
                    val jsonString by androidx.compose.runtime.produceState<String?>(initialValue = null, key1 = variant.lottieJsonAsset) {
                        runCatching {
                            com.dexstudios.dex.core.designsystem.generated.resources.Res.readBytes(variant.lottieJsonAsset).decodeToString()
                        }.onSuccess { value = it }
                    }
                    jsonString?.let { currentJson ->
                        val composition by io.github.alexzhirkevich.compottie.rememberLottieComposition {
                            io.github.alexzhirkevich.compottie.LottieCompositionSpec.JsonString(currentJson)
                        }
                        val lottieProgress by io.github.alexzhirkevich.compottie.animateLottieCompositionAsState(
                            composition = composition,
                            iterations = io.github.alexzhirkevich.compottie.Compottie.IterateForever,
                        )
                        androidx.compose.foundation.Image(
                            painter = io.github.alexzhirkevich.compottie.rememberLottiePainter(
                                composition = composition,
                                progress = { lottieProgress },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    val defaultIconResource = when (variant) {
                        ToastVariant.Success -> DeXIcons.Check
                        ToastVariant.Warning, ToastVariant.Error -> DeXIcons.AlertFilled
                        ToastVariant.Info, ToastVariant.Progress, is ToastVariant.Lottie -> DeXIcons.Devices
                    }

                    val effectivePainter = when {
                        iconPainter != null -> iconPainter
                        iconResource != null -> painterResource(iconResource)
                        else -> painterResource(defaultIconResource)
                    }

                    Icon(
                        painter = effectivePainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTintColor,
                    )
                }
            }

            // Snug Toast Message text with weight(1f) to fill width and truncate if long
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Optional trailing action button
            if (actionText != null && onActionClick != null) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .bubbleFluidity(targetScale = 0.94f, pullFactor = 0.05f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onActionClick,
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
