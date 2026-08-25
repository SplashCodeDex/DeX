package com.dexstudios.dex.window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_rotate
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private val mirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun MirrorWindow(onClose: () -> Unit, peerName: String = "Connected Phone", mirrorEngine: com.dexstudios.dex.core.network.IMirrorEngine = org.koin.compose.koinInject()) {
    val windowState = rememberWindowState(
        size = DpSize(420.dp, 840.dp),
        position = WindowPosition.PlatformDefault,
    )

    var isLandscape by remember { mutableStateOf(false) }

    val frameBytes by mirrorEngine.latestFrame.collectAsState()

    // Decode off the UI thread: Image.makeFromEncoded on every incoming frame during
    // composition stalled composition at phone frame rates. A malformed frame keeps the
    // last good bitmap instead of crashing composition (silent by design: a hostile or
    // glitchy stream would otherwise spam one log line per frame).
    val latestFrame by produceState<ImageBitmap?>(initialValue = null, key1 = frameBytes) {
        // Local capture: delegated properties cannot be smart-cast inside the producer.
        val bytes = frameBytes
        if (bytes == null) {
            value = null
        } else {
            val decoded = withContext(Dispatchers.Default) {
                runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            }
            if (decoded != null) {
                value = decoded
            }
        }
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
        alwaysOnTop = false,
    ) {
        DeXTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Video letterbox stays pure black in both themes (cinema standard);
                    // the old purple-tinted hex was a dark-theme-only artifact.
                    .background(Color.Black),
            ) {
                // Local capture: delegated property cannot be smart-cast to non-null.
                val currentFrame = latestFrame
                if (currentFrame != null) {
                    Image(
                        bitmap = currentFrame,
                        contentDescription = "Screen Mirror",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // Waiting for stream / Idle state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_smartphone),
                                contentDescription = "Mirroring",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Mirroring $peerName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Waiting for display stream...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }

                // Floating Control Overlay (Top Bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
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
                                .background(Color.Black.copy(alpha = 0.65f)),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_rotate),
                                contentDescription = "Rotate",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_close),
                                contentDescription = "Disconnect",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
