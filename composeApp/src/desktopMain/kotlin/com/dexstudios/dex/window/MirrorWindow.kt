package com.dexstudios.dex.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme

/**
 * MirrorWindow:
 * Dedicated desktop window for phone screen mirroring.
 * Renders JPEG frame stream received from the phone via WebSocket / MediaProjection.
 */
@Composable
fun MirrorWindow(
    onClose: () -> Unit,
    peerName: String = "Connected Phone",
    latestFrame: ImageBitmap? = null
) {
    val windowState = rememberWindowState(
        size = DpSize(420.dp, 840.dp),
        position = WindowPosition.PlatformDefault
    )

    var isLandscape by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "DeX Mirror — $peerName",
        alwaysOnTop = false
    ) {
        DeXTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0C13))
            ) {
                if (latestFrame != null) {
                    Image(
                        bitmap = latestFrame,
                        contentDescription = "Screen Mirror",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Waiting for stream / Idle state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(DeXTheme.colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Smartphone,
                                contentDescription = "Mirroring",
                                tint = DeXTheme.colors.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Mirroring $peerName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeXTheme.colors.primaryText
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Waiting for display stream...",
                            fontSize = 13.sp,
                            color = DeXTheme.colors.secondaryText
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        CircularProgressIndicator(
                            color = DeXTheme.colors.secondary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                // Floating Control Overlay (Top Bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(DeXTheme.colors.secondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                if (isLandscape) {
                                    windowState.size = DpSize(840.dp, 480.dp)
                                } else {
                                    windowState.size = DpSize(420.dp, 840.dp)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.History,
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Close,
                                contentDescription = "Disconnect",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
