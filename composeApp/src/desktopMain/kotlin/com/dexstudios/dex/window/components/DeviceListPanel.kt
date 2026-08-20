package com.dexstudios.dex.window.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.PaddingValues
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.joe_avatar
import com.dexstudios.dex.core.designsystem.generated.resources.user1_avatar
import com.dexstudios.dex.core.designsystem.generated.resources.user2_avatar
import com.dexstudios.dex.core.designsystem.generated.resources.user3_avatar
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DiscoveredDevice
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * UI presentation model for devices displayed in the floating card.
 */
data class DeviceItemUiModel(
    val id: String,
    val alias: String,
    val modelText: String,
    val ip: String,
    val fingerprint: String,
    val isPaired: Boolean,
    val isActive: Boolean = false,
    val isAdbConnected: Boolean = false,
    val isOnline: Boolean = true,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val wifiBand: String? = null,
    val wifiRssi: Int? = null,
    val isWanPlaceholder: Boolean = false,
    val wanEmail: String? = null,
    val avatarDrawable: DrawableResource? = null,
    val rawDevice: DiscoveredDevice? = null
)

/**
 * WAN placeholder profiles (visual scaffolding for the upcoming WAN cross-email feature).
 */
fun defaultWanPlaceholders(): List<DeviceItemUiModel> = listOf(
    DeviceItemUiModel(
        id = "wan-ama-serwaa",
        alias = "Ama Serwaa",
        modelText = "WAN Device",
        ip = "0.0.0.0",
        fingerprint = "wan-ama-serwaa",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "ama.serwaa@gmail.com"
    ),
    DeviceItemUiModel(
        id = "wan-akua-donkor",
        alias = "Akua Donkor",
        modelText = "WAN Device",
        ip = "0.0.0.0",
        fingerprint = "wan-akua-donkor",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "akua.donkor@gmail.com"
    ),
    DeviceItemUiModel(
        id = "wan-kwame-asante",
        alias = "Kwame Asante",
        modelText = "WAN Device",
        ip = "0.0.0.0",
        fingerprint = "wan-kwame-asante",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "kwame.asante@gmail.com"
    )
)



/**
 * DeviceListPanel:
 * - Section 1: Discovered Devices (UDP discovered, untrusted -> click initiates PIN pairing)
 * - Section 2: Your Devices (Paired trusted devices with live telemetry, battery %, wifi band, and WAN scaffolding)
 * - Right-click context menus with 1:1 action routing
 */
@Composable
fun DeviceListPanel(
    discoveredDevices: List<DeviceItemUiModel>,
    pairedDevices: List<DeviceItemUiModel>,

    onPairDevice: (DeviceItemUiModel) -> Unit,
    onSendFile: (DeviceItemUiModel) -> Unit = {},
    onSendClipboard: (DeviceItemUiModel) -> Unit = {},
    onMirrorScreen: (DeviceItemUiModel) -> Unit = {},
    onConnectAdb: (DeviceItemUiModel) -> Unit = {},
    onDisconnectAdb: (DeviceItemUiModel) -> Unit = {},
    onCopyIp: (String) -> Unit = {},
    onRenameDevice: (DeviceItemUiModel) -> Unit = {},
    onForgetDevice: (DeviceItemUiModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Discovered Devices Section (Only rendered if devices discovered)
        if (discoveredDevices.isNotEmpty()) {
            item(key = "hdr_discovered") {
                Text(
                    text = "Discovered Devices",
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            items(discoveredDevices, key = { "disc_${it.fingerprint.ifBlank { it.ip }}" }) { device ->
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("PIN CODE (Pair)") { onPairDevice(device) },
                            ContextMenuItem("Connect ADB") { onConnectAdb(device) },
                            ContextMenuItem("Copy IP Address") { onCopyIp(device.ip) },
                            ContextMenuItem("Forget Device") { onForgetDevice(device) }
                        )
                    }
                ) {
                    DeviceListItemRow(
                        device = device,
                        onClick = { onPairDevice(device) }
                    )
                }
            }
        }

        // Your Devices Section (Always rendered)
        item(key = "hdr_your_devices") {
            Text(
                text = "Your Devices",
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            )
        }

        // Hardcoded Profile (Joe) & Local Windows Device
        item(key = "profile_joe") {
            ProfileListItemRow(
                modifier = Modifier.animateItem()
            )
        }
        
        item(key = "local_windows") {
            LocalDeviceItemRow(
                modifier = Modifier.animateItem()
            )
        }
        items(pairedDevices, key = { "paired_${it.fingerprint.ifBlank { it.ip }}" }) { device ->
                ContextMenuArea(
                    items = {
                                                buildList {
                            add(ContextMenuItem("Send Clipboard") { onSendClipboard(device) })
                            add(ContextMenuItem("Mirror Screen") { onMirrorScreen(device) })
                            add(ContextMenuItem("Copy IP Address") { onCopyIp(device.ip) })
                            if (device.isAdbConnected) {
                                add(ContextMenuItem("Disconnect ADB") { onDisconnectAdb(device) })
                            } else {
                                add(ContextMenuItem("Connect ADB") { onConnectAdb(device) })
                            }
                            add(ContextMenuItem("Rename / Alias") { onRenameDevice(device) })
                            add(ContextMenuItem("Forget Device") { onForgetDevice(device) })
                        }
                    }
                ) {
                    DeviceListItemRow(
                        device = device,
                        onClick = { onSendFile(device) }
                    )
                }
            }
    }
}

@Composable
private fun DeviceListItemRow(
    device: DeviceItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBg = when {
        isPressed || isHovered || device.isActive -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val alpha = if (device.isOnline) 1.0f else 0.5f

    // WPF Kinematics
    val transX by animateDpAsState(
        targetValue = when {
            isPressed -> 12.dp
            isHovered -> 6.dp
            else -> 0.dp
        },
        animationSpec = DockCardPhysics.ElasticDpSpec
    )
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = if (isPressed) androidx.compose.animation.core.tween(100) else androidx.compose.animation.core.tween(300, easing = DockCardPhysics.HoverEase)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .graphicsLayer {
                translationX = transX.toPx()
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f).alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
        // 38x38dp Leading Circle Glyph with Sub-Dot Indicator
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .then(
                        if (device.avatarDrawable != null) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        } else if (device.isOnline) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier
                                .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                                .background(Color.Transparent)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (device.avatarDrawable != null) {
                    Image(
                        painter = painterResource(device.avatarDrawable),
                        contentDescription = device.alias,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (device.isWanPlaceholder) MaterialSymbols.AccountCircle else MaterialSymbols.Smartphone,
                        contentDescription = device.alias,
                        tint = if (device.isOnline) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 12x12dp Online Indicator (Bottom Right)
            if (device.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Device Telemetry Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = device.alias,
                fontSize = 15.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val topSpacing = if (device.isPaired && !device.isWanPlaceholder) 2.dp else 0.dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = topSpacing)
            ) {
                val subFontSize = if (device.isPaired) 12.sp else 13.sp
                Text(
                    text = device.wanEmail ?: device.modelText.ifBlank { device.ip },
                    fontSize = subFontSize,
                    lineHeight = subFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )

                if (device.isOnline) {
                    if (!device.wifiBand.isNullOrBlank()) {
                        Icon(
                            imageVector = MaterialSymbols.Wifi,
                            contentDescription = "WiFi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp).size(12.dp)
                        )
                        Text(
                            text = device.wifiBand,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (device.isOnline && device.batteryPercent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .alpha(0.8f)
            ) {
                val batteryIcon = when {
                    device.isCharging -> MaterialSymbols.BatteryCharging
                    device.batteryPercent >= 80 -> MaterialSymbols.BatteryFull
                    device.batteryPercent >= 50 -> MaterialSymbols.Battery4
                    device.batteryPercent >= 20 -> MaterialSymbols.Battery2
                    else -> MaterialSymbols.Battery1
                }
                Icon(
                    imageVector = batteryIcon,
                    contentDescription = "Battery",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp).size(12.dp)
                )
                Text(
                    text = "${device.batteryPercent}%",
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
}

@Composable
private fun ProfileListItemRow(
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBg = when {
        isPressed || isHovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val transX by animateDpAsState(
        targetValue = when {
            isPressed -> 6.dp
            isHovered -> 6.dp
            else -> 0.dp
        },
        animationSpec = DockCardPhysics.ElasticDpSpec
    )
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = if (isPressed) androidx.compose.animation.core.tween(100) else androidx.compose.animation.core.tween(300, easing = DockCardPhysics.HoverEase)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .graphicsLayer {
                translationX = transX.toPx()
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.joe_avatar),
                contentDescription = "Profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // 12x12dp indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Profile Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DeXStudios",
                fontSize = 15.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "dexify@dex.net",
                fontSize = 13.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Chevron
        Icon(
            imageVector = MaterialSymbols.ArrowBack,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 8.dp)
                .graphicsLayer { rotationZ = 180f }
        )
    }
}

@Composable
private fun LocalDeviceItemRow(
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBg = when {
        isPressed || isHovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val transX by animateDpAsState(
        targetValue = when {
            isPressed -> 12.dp
            isHovered -> 6.dp
            else -> 0.dp
        },
        animationSpec = DockCardPhysics.ElasticDpSpec
    )
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = if (isPressed) androidx.compose.animation.core.tween(100) else androidx.compose.animation.core.tween(300, easing = DockCardPhysics.HoverEase)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .graphicsLayer {
                translationX = transX.toPx()
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Windows Icon
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MaterialSymbols.Computer,
                contentDescription = "Windows",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Device Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Windows",
                fontSize = 15.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
