package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

@Composable
fun LiquidToastNotification(
    title: String,
    message: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    acceptText: String = "Accept",
    dismissText: String = "Dismiss",
) {
    LiquidGlassPanel(
        backdrop = backdrop,
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(24.dp),
        config = LiquidGlassPresets.Dialog.copy(
            shape = RoundedCornerShape(24.dp),
            surfaceTint = MaterialTheme.colorScheme.surfaceVariant,
            surfaceTintAlpha = 0.25f,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                // Accept Button (Primary Blue)
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = acceptText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }

                // Dismiss Button (Liquid Glass / Secondary)
                LiquidGlassPanel(
                    backdrop = backdrop,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    shape = CircleShape,
                    config = LiquidGlassPresets.IconButton.copy(
                        shape = CircleShape,
                        surfaceTint = MaterialTheme.colorScheme.onSurface,
                        surfaceTintAlpha = 0.1f, // Light tint for button contrast
                        shadowRadius = 0.dp,
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dismissText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
