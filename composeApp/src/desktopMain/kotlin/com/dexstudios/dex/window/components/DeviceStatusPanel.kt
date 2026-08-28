package com.dexstudios.dex.window.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.icons.DeXIcons
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.desktop.transfer.DesktopFileSendService
import com.dexstudios.dex.window.DockedWindowStateController
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

enum class ConnectedDeviceType {
    Phone,
    Tablet,
    Laptop,
    Watch,
}

fun resolveDeviceCategory(device: DiscoveredDevice?, fallbackName: String = ""): ConnectedDeviceType {
    val type = (device?.info?.deviceType ?: "").lowercase()
    val model = (device?.info?.deviceModel ?: "").lowercase()
    val alias = (device?.info?.alias ?: fallbackName).lowercase()
    val combined = "$type $model $alias"

    return when {
        combined.contains("watch") || combined.contains("wear") -> ConnectedDeviceType.Watch

        combined.contains("tablet") || combined.contains("tab") || combined.contains("pad") -> ConnectedDeviceType.Tablet

        combined.contains("laptop") || combined.contains("desktop") || combined.contains("pc") ||
            combined.contains("macbook") || combined.contains("book") || combined.contains("computer") ||
            combined.contains("thinkpad") || combined.contains("surface") -> ConnectedDeviceType.Laptop

        else -> ConnectedDeviceType.Phone
    }
}

/**
 * DeviceStatusPanel:
 * Dedicated screen in the floating card displaying the connected device status,
 * live telemetry (battery, Wi-Fi SSID, signal), and Exit-Engine styled Send Files button.
 *
 * Supports Phone, Tablet, Laptop, and Watch dynamic 3D animations.
 * Designed with Apple connection card ergonomics.
 */
@Composable
fun DeviceStatusPanel(
    controller: DockedWindowStateController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    overrideCategory: ConnectedDeviceType? = null,
    onBrowseFiles: (DiscoveredDevice?) -> Unit = {},
    discoveryEngine: DiscoveryEngine = koinInject(),
    deviceConfig: DeviceConfig = koinInject(),
    fileSender: DesktopFileSendService = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val devicesMap by discoveryEngine.devices.collectAsState()
    val pairedFingerprints by AuthState.pairedFingerprints.collectAsState()

    // Resolve connected device
    val connectedFps = WebSocketConnectionManager.connectedFingerprints()
    val activeDevice = remember(devicesMap, pairedFingerprints, connectedFps) {
        val connectedMatch = devicesMap.values.firstOrNull { it.info.fingerprint in connectedFps }
        connectedMatch
            ?: devicesMap.values.firstOrNull { pairedFingerprints.contains(it.info.fingerprint) }
            ?: devicesMap.values.firstOrNull()
    }

    var activeCategory by remember(overrideCategory, activeDevice) {
        mutableStateOf(overrideCategory ?: resolveDeviceCategory(activeDevice))
    }

    val defaultDemoName = when (activeCategory) {
        ConnectedDeviceType.Phone -> "Galaxy S24 Ultra"
        ConnectedDeviceType.Tablet -> "Galaxy Tab S9 Ultra"
        ConnectedDeviceType.Laptop -> "Galaxy Book 4 Pro"
        ConnectedDeviceType.Watch -> "Galaxy Watch 6"
    }

    val deviceName = activeDevice?.info?.alias?.ifBlank { null }
        ?: activeDevice?.info?.fingerprint?.take(8)
        ?: defaultDemoName

    val batteryPercent = activeDevice?.info?.battery ?: when (activeCategory) {
        ConnectedDeviceType.Phone -> 85
        ConnectedDeviceType.Tablet -> 90
        ConnectedDeviceType.Laptop -> 92
        ConnectedDeviceType.Watch -> 78
    }
    val wifiSsid = activeDevice?.info?.wifiSsid ?: "HomeNetwork_5G"
    val isCharging = activeDevice?.info?.isCharging == true

    // Lottie connection animation dynamically loaded per device category
    var lottieJson by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeCategory) {
        val jsonPath = when (activeCategory) {
            ConnectedDeviceType.Phone -> "files/device_connected.json"
            ConnectedDeviceType.Tablet -> "files/tablet_connected.json"
            ConnectedDeviceType.Laptop -> "files/laptop_connected.json"
            ConnectedDeviceType.Watch -> "files/watch_connected.json"
        }
        runCatching {
            lottieJson = Res.readBytes(jsonPath).decodeToString()
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // === 1. Top Header: Centered Device Name + Close 'X' Button at Top-Right ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = deviceName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            val closeInteraction = remember { MutableInteractionSource() }
            val isCloseHovered by closeInteraction.collectIsHoveredAsState()
            val closeScale by animateFloatAsState(
                targetValue = if (isCloseHovered) 1.1f else 1.0f,
                animationSpec = tween(120),
                label = "closeScale",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = closeScale
                        scaleY = closeScale
                    }
                    .bubbleFluidity(targetScale = 0.92f, pullFactor = 0.05f)
                    .clip(CircleShape)
                    .background(
                        if (isCloseHovered) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DeXIcons.Close),
                    contentDescription = "Close",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // === 2. Big Centered Device Animation (Click to cycle demo devices) ===
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        activeCategory = when (activeCategory) {
                            ConnectedDeviceType.Phone -> ConnectedDeviceType.Tablet
                            ConnectedDeviceType.Tablet -> ConnectedDeviceType.Laptop
                            ConnectedDeviceType.Laptop -> ConnectedDeviceType.Watch
                            ConnectedDeviceType.Watch -> ConnectedDeviceType.Phone
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val shadowWidth = when (activeCategory) {
                ConnectedDeviceType.Phone -> 105.dp
                ConnectedDeviceType.Tablet -> 125.dp
                ConnectedDeviceType.Laptop -> 155.dp
                ConnectedDeviceType.Watch -> 95.dp
            }
            val shadowOffsetY = when (activeCategory) {
                ConnectedDeviceType.Laptop -> 84.dp
                ConnectedDeviceType.Watch -> 94.dp
                else -> 98.dp
            }

            // Subtle floating contact shadow
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = shadowOffsetY)
                    .size(width = shadowWidth, height = 16.dp),
            ) {
                val shadowColor = Color.Black
                drawOval(
                    color = shadowColor.copy(alpha = 0.04f),
                    size = size,
                )
                drawOval(
                    color = shadowColor.copy(alpha = 0.08f),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                    size = Size(size.width * 0.76f, size.height * 0.68f),
                )
                drawOval(
                    color = shadowColor.copy(alpha = 0.13f),
                    topLeft = Offset(size.width * 0.24f, size.height * 0.28f),
                    size = Size(size.width * 0.52f, size.height * 0.44f),
                )
            }

            val json = lottieJson
            if (json != null) {
                val composition by rememberLottieComposition {
                    LottieCompositionSpec.JsonString(json)
                }
                val animSpeed = when (activeCategory) {
                    ConnectedDeviceType.Laptop -> 0.55f
                    else -> 0.70f
                }
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations = Compottie.IterateForever,
                    speed = animSpeed,
                )
                val painter = rememberLottiePainter(
                    composition = composition,
                    progress = { progress },
                )
                val animScale = when (activeCategory) {
                    ConnectedDeviceType.Watch -> 1.15f
                    ConnectedDeviceType.Tablet -> 1.05f
                    ConnectedDeviceType.Laptop -> 1.05f
                    ConnectedDeviceType.Phone -> 1.0f
                }
                Image(
                    painter = painter,
                    contentDescription = "Connected Device",
                    modifier = Modifier
                        .size(185.dp)
                        .graphicsLayer {
                            scaleX = animScale
                            scaleY = animScale
                        },
                )
            } else {
                Icon(
                    painter = painterResource(DeXIcons.Smartphone),
                    contentDescription = "Device",
                    modifier = Modifier.size(110.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // === 3. Battery & Wi-Fi Telemetry (Container-less, Stacked Icon + Value) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Battery Status Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val batteryIcon = when {
                    isCharging -> DeXIcons.BatteryCharging
                    batteryPercent >= 80 -> DeXIcons.BatteryFull
                    batteryPercent >= 50 -> DeXIcons.Battery4
                    batteryPercent >= 25 -> DeXIcons.Battery2
                    else -> DeXIcons.Battery1
                }
                Icon(
                    painter = painterResource(batteryIcon),
                    contentDescription = "Battery",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "$batteryPercent%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Wi-Fi Status Column (Consistent Layout & Ellipsis Truncation)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(DeXIcons.Wifi),
                    contentDescription = "Wi-Fi",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = wifiSsid,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
            }
        }

        // === 4. Bottom Action: Exit Engine-Styled Send Files Pill ===
        val sendInteraction = remember { MutableInteractionSource() }
        val sendHovered by sendInteraction.collectIsHoveredAsState()
        val sendHoverScale by animateFloatAsState(
            targetValue = if (sendHovered) 1.05f else 1.0f,
            animationSpec = DockCardAnimations.HoverSpec,
            label = "sendHoverScale",
        )

        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val targetSendBg = if (isDark) {
            if (sendHovered) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        } else {
            Color.White
        }

        val sendBtnBgColor by animateColorAsState(
            targetValue = targetSendBg,
            animationSpec = DockCardAnimations.LinearColorSpec,
            label = "sendBtnBg",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .height(40.dp)
                .graphicsLayer {
                    scaleX = sendHoverScale
                    scaleY = sendHoverScale
                }
                .bubbleFluidity(targetScale = 0.95f, pullFactor = 0.05f)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(30.dp),
                    spotColor = Color.Black.copy(alpha = 0.2f),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                )
                .clip(RoundedCornerShape(30.dp))
                .background(sendBtnBgColor)
                .shinyGlare(shape = RoundedCornerShape(30.dp))
                .clickable(
                    interactionSource = sendInteraction,
                    indication = null,
                    onClick = {
                        val fp = activeDevice?.info?.fingerprint ?: return@clickable
                        coroutineScope.launch(Dispatchers.IO) {
                            fileSender.setPreferredTarget(fp)
                            val picked = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                runCatching {
                                    controller.isModalDialogOpen = true
                                    try {
                                        val holder = arrayOfNulls<List<File>>(1)
                                        EventQueue.invokeAndWait {
                                            val dialog = FileDialog(null as Frame?, "Send files to $deviceName", FileDialog.LOAD)
                                            dialog.isMultipleMode = true
                                            dialog.isVisible = true
                                            holder[0] = dialog.files.toList()
                                        }
                                        holder[0].orEmpty()
                                    } finally {
                                        controller.isModalDialogOpen = false
                                    }
                                }.getOrDefault(emptyList())
                            }
                            if (picked.isNotEmpty()) {
                                fileSender.sendFiles(picked, fp)
                            }
                        }
                    },
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(DeXIcons.Send),
                    contentDescription = "Send Files",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Send Files",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
