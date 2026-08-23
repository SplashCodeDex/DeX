package com.dexstudios.dex.window
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_rotate
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close

import com.dexstudios.dex.core.designsystem.generated.resources.Res

import org.jetbrains.compose.resources.painterResource

import androidx.compose.material3.MaterialTheme

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
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import com.dexstudios.dex.core.designsystem.theme.DeXTheme

/**
 * MirrorWindow:
 * Dedicated desktop window for phone screen mirroring.
 * Renders JPEG frame stream received from the phone via WebSocket / MediaProjection.
 */
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

private val mirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun MirrorWindow(
    onClose: () -> Unit,
    peerName: String = "Connected Phone",
    mirrorEngine: com.dexstudios.dex.core.network.IMirrorEngine = org.koin.compose.koinInject()
) {
    val windowState = rememberWindowState(
        size = DpSize(420.dp, 840.dp),
        position = WindowPosition.PlatformDefault
    )

    var isLandscape by remember { mutableStateOf(false) }

    val frameBytes by mirrorEngine.latestFrame.collectAsState()
    val latestFrame = remember(frameBytes) {
        frameBytes?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
    }

    // Live frame aspect ratio drives the landscape window size so the video fills
    // without letterboxing regardless of the phone's orientation/resolution
    val frameAspect = latestFrame?.let { it.width.toFloat() / it.height.toFloat() } ?: 0f
    val landscapeWidth = 840f
    val landscapeHeight = if (frameAspect > 0f) {
        (landscapeWidth / frameAspect).coerceIn(320f, 1200f)
    } else {
        480f
    }

    LaunchedEffect(Unit) {
        com.dexstudios.dex.core.network.server.WebSocketConnectionManager.broadcastToPaired("""{"type":"mirror-start","data":{}}""")
    }

    DisposableEffect(Unit) {
        onDispose {
            mirrorScope.launch {
                com.dexstudios.dex.core.network.server.WebSocketConnectionManager.broadcastToPaired("""{"type":"mirror-stop","data":{}}""")
            }
        }
    }

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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_smartphone),
                                contentDescription = "Mirroring",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Mirroring $peerName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Waiting for display stream...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
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
                                    .background(MaterialTheme.colorScheme.primary)
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
                                windowState.size = if (isLandscape) {
                                    DpSize(landscapeWidth.dp, landscapeHeight.dp)
                                } else {
                                    DpSize(420.dp, 840.dp)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_rotate),
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
                                painter = painterResource(Res.drawable.ic_fluent_close),
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
