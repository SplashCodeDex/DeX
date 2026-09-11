package com.dexstudios.dex.window.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.network.TransferStateMonitor
import com.dexstudios.dex.window.kinematics.DockCardAnimations

@Composable
fun ActiveTransferDashboard(modifier: Modifier = Modifier) {
    val transfersMap by TransferStateMonitor.activeTransfers.collectAsState()
    val activeTransfers = transfersMap.values.toList()

    AnimatedVisibility(
        visible = activeTransfers.isNotEmpty(),
        enter =
        fadeIn(tween(180)) + slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.58f,
                stiffness = 320f,
            ),
            initialOffsetY = { -40 },
        ) + scaleIn(
            initialScale = 0.90f,
            animationSpec = spring(
                dampingRatio = 0.58f,
                stiffness = 320f,
            ),
        ),
        exit =
        fadeOut(tween(160)) + slideOutVertically(
            animationSpec = spring(
                dampingRatio = 0.70f,
                stiffness = 380f,
            ),
            targetOffsetY = { -40 },
        ) + scaleOut(
            targetScale = 0.92f,
            animationSpec = spring(
                dampingRatio = 0.70f,
                stiffness = 380f,
            ),
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (transfer in activeTransfers) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                            ambientColor = Color.Black.copy(alpha = 0.06f),
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .shinyGlare(shape = RoundedCornerShape(16.dp), intensity = DefaultGlareIntensity)
                        .padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progressPercent = if (transfer.totalFiles > 0) transfer.filesReceived.toFloat() / transfer.totalFiles.toFloat() else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (transfer.isComplete) "Transfer Complete" else "Receiving from ${transfer.senderAlias}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                text = "${transfer.filesReceived} / ${transfer.totalFiles}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressPercent },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        )
                    }
                }
            }
        }
    }
}
