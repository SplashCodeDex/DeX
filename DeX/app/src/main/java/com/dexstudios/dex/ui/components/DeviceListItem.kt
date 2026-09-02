package com.dexstudios.dex.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.dexstudios.dex.ui.theme.PopInEase
import com.dexstudios.dex.ui.theme.SpatialPhysics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import coil3.size.Size
import com.dexstudios.dex.ui.icons.MaterialSymbols
import androidx.compose.runtime.remember

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexstudios.dex.network.WallpaperState
import androidx.compose.ui.draw.shadow
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.components.glass.LocalBackdrop
import com.kyant.backdrop.drawBackdrop

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
fun DeviceListItem(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isTrusted: Boolean = AuthState.pairedFingerprints.contains(device.info.fingerprint),
    wallpaper: Any? = null, // Allow passing a custom image source
    onButtonClick: () -> Unit = onClick,
    pairingMode: Boolean = false,
    onPinCode: () -> Unit = {},
    onQrCode: () -> Unit = {},
    isConnecting: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val wallpaperRevision by WallpaperState.revision.collectAsStateWithLifecycle()

    val realBattery = device.info.battery
    val isCharging = device.info.isCharging == true

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
            .size(Size(512, 512))
            .crossfade(true)
            .build()
    }

    val cardShape = RoundedCornerShape(40.dp)

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "card-${device.info.fingerprint}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    tween(
                        durationMillis = SpatialPhysics.POP_IN_DURATION_MS,
                        easing = PopInEase
                    )
                },
                renderInOverlayDuringTransition = true
            )
        }
    } else {
        Modifier
    }

    // "Single Physical Card" illusion: Hide the source card in the list when it's transitioning/connected
    val contentAlpha = if (isConnecting && !pairingMode) 0f else 1f
    // Disable interactive fluidity during flight to keep geometry stable
    val shouldApplyFluidity = !isConnecting

    val backdrop = LocalBackdrop.current
    val iconHighlight = LiquidGlassPresets.IconButton.highlight

    Box(
        modifier = modifier
            .width(260.dp)
            .height(300.dp)
            .then(sharedModifier)
            .graphicsLayer { alpha = contentAlpha }
            .then(if (shouldApplyFluidity) Modifier.bubbleFluidity(targetScale = 0.98f, pullFactor = 0.02f) else Modifier)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { cardShape },
                        effects = {},
                        highlight = { iconHighlight }
                    )
                } else Modifier
            )
            .clip(cardShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) {
        // 1. The Background Layer (Wallpaper or Placeholder)
        if (imageRequest != null) {
            val errorPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(image = MaterialSymbols.Devices)
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                error = errorPainter
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

        // 2. Bottom Gradient Dim only for trusted devices (which have wallpapers)
        if (isTrusted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.1f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
            )
        }

        // 3. UI Content Layer
        AnimatedContent(
            targetState = pairingMode,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "device_card_content_morph"
        ) { isPairing ->
            if (isPairing) {
                DeviceCardPairingContent(
                    device = device,
                    onPinCode = onPinCode,
                    onQrCode = onQrCode
                )
            } else {
                DeviceCardUIContent(
                    device = device,
                    isTrusted = isTrusted,
                    realBattery = realBattery,
                    batteryIcon = batteryIcon,
                    onButtonClick = onButtonClick
                )
            }
        }
    }
}

@Composable
private fun DeviceCardUIContent(
    device: DiscoveredDevice,
    isTrusted: Boolean,
    realBattery: Int?,
    batteryIcon: ImageVector,
    onButtonClick: () -> Unit
) {
    // Trusted devices use white text to contrast with the dark gradient dim.
    // Discovered devices use onBackground to match the Scan card.
    val textColor = if (isTrusted) Color.White else MaterialTheme.colorScheme.onBackground
    val subtitleColor = if (isTrusted) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = device.info.alias,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Telemetry beside name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(0.8f)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = textColor
                )
                if (isTrusted && realBattery != null) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = device.info.deviceModel.ifBlank { stringResource(R.string.device_unknown) },
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(20.dp))

        val buttonColors = if (isTrusted) {
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        } else {
            ButtonDefaults.buttonColors()
        }

        DeXButton(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = CircleShape,
            colors = buttonColors
        ) {
            Text(
                text = if (isTrusted) "Send File" else "Connect",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun DeviceCardPairingContent(
    device: DiscoveredDevice,
    onPinCode: () -> Unit,
    onQrCode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Device Identity Header (Smaller version)
        Icon(
            imageVector = MaterialSymbols.Devices,
            contentDescription = null,
            modifier = Modifier.size(28.dp).alpha(0.6f),
            tint = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = device.info.alias,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        // Connection Options
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PairingOptionButton(
                icon = MaterialSymbols.Pin,
                label = stringResource(R.string.connect_option_pin),
                onClick = onPinCode
            )
            PairingOptionButton(
                icon = MaterialSymbols.QrCodeScanner,
                label = stringResource(R.string.connect_option_qr),
                onClick = onQrCode
            )
        }
    }
}

@Composable
private fun PairingOptionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    DeXButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}


