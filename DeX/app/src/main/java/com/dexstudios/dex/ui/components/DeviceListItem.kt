package com.dexstudios.dex.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.AuthState
import com.dexstudios.dex.network.DiscoveredDevice

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import com.dexstudios.dex.ui.icons.MaterialSymbols
import androidx.compose.runtime.remember

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.network.WallpaperState

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DeviceListItem(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isTrusted: Boolean = AuthState.pairedFingerprints.contains(device.info.fingerprint),
    wallpaper: Any? = null, // Allow passing a custom image source
    onButtonClick: () -> Unit = onClick
) {
    val context = LocalContext.current
    val wallpaperRevision by WallpaperState.revision.collectAsStateWithLifecycle()

    val realBattery = device.info.battery
    val isCharging = device.info.isCharging == true
    val realWifiBand = device.info.wifiBand?.ifBlank { "5GHz" } ?: "5GHz"

    val batteryIcon = remember(realBattery, isCharging) {
        if (isCharging) {
            MaterialSymbols.BatteryCharging
        } else if (realBattery != null) {
            when {
                realBattery <= 15 -> MaterialSymbols.Battery1
                realBattery <= 35 -> MaterialSymbols.Battery2
                realBattery <= 50 -> MaterialSymbols.Battery3
                realBattery <= 70 -> MaterialSymbols.Battery4
                realBattery <= 85 -> MaterialSymbols.Battery5
                else -> MaterialSymbols.BatteryFull
            }
        } else {
            MaterialSymbols.BatteryFull
        }
    }

    // Dynamic Wallpaper Resolution: Fetch 480p desktop wallpaper from PC endpoint ONLY when paired (isTrusted)
    val pairedToken = AuthState.pairedTokens[device.info.fingerprint] ?: ""
    val resolvedWallpaper = remember(wallpaper, device.ip, device.info.port, device.info.protocol, isTrusted, wallpaperRevision) {
        if (wallpaper != null) {
            wallpaper
        } else if (isTrusted && device.ip.isNotBlank() && device.ip != "0.0.0.0" && device.info.port > 0) {
            val protocol = device.info.protocol.ifBlank { "https" }
            val host = if (device.ip.contains(":")) "[${device.ip}]" else device.ip
            "$protocol://$host:${device.info.port}/api/dex/wallpaper?rev=$wallpaperRevision&token=$pairedToken&fingerprint=${device.info.fingerprint}"
        } else {
            null
        }
    }

    val imageRequest = remember(resolvedWallpaper, context) {
        if (resolvedWallpaper == null) return@remember null
        ImageRequest.Builder(context)
            .data(resolvedWallpaper)
            .crossfade(true)
            .build()
    }

    val cardShape = RoundedCornerShape(48.dp)

    DeXPanel(
        modifier = modifier
            .bubbleFluidity(targetScale = 0.98f)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape, // Deep rounded corners as requested
        shadowRadius = 16.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
            // 1. Wallpaper or Placeholder
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Devices,
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .offset(y = (-32).dp)
                            .alpha(0.15f),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 2. Glassy Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            0.6f to MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 24.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.info.alias,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = device.info.deviceModel.ifBlank { stringResource(R.string.device_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags Row (Updated with Live Telemetry & Pairing Security Scoping)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeviceIconTag(icon = MaterialSymbols.Wifi)
                    if (isTrusted && realBattery != null) {
                        DeviceIconTag(icon = batteryIcon)
                        DeviceTag(text = realWifiBand)
                        DeviceTag(text = "$realBattery%")
                        DeviceTag(text = "Paired")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                DeXButton(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = if (isTrusted) "Send File" else "Connect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIconTag(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).size(18.dp)
        )
    }
}

@Composable
private fun DeviceTag(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
