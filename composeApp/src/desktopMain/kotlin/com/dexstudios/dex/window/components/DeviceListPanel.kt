package com.dexstudios.dex.window.components
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_charging
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_full
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery4
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery2
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery1
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_account_circle
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_computer
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_wifi

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

    val cardBg = when {
        isHovered || device.isActive -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val alpha = if (device.isOnline) 1.0f else 0.5f

    // WPF Kinematics
    val transX by animateDpAsState(
        targetValue = if (isHovered) 6.dp else 0.dp,
        animationSpec = DockCardPhysics.ElasticDpSpec
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1.0f,
        animationSpec = androidx.compose.animation.core.tween(300, easing = DockCardPhysics.HoverEase)
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
            .bubbleFluidity()
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
                        painter = if (device.isWanPlaceholder) painterResource(Res.drawable.ic_fluent_account_circle) else painterResource(Res.drawable.ic_fluent_smartphone),
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
                            painter = painterResource(Res.drawable.ic_fluent_wifi),
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
                    device.isCharging -> painterResource(Res.drawable.ic_fluent_battery_charging)
                    device.batteryPercent >= 80 -> painterResource(Res.drawable.ic_fluent_battery_full)
                    device.batteryPercent >= 50 -> painterResource(Res.drawable.ic_fluent_battery4)
                    device.batteryPercent >= 20 -> painterResource(Res.drawable.ic_fluent_battery2)
                    else -> painterResource(Res.drawable.ic_fluent_battery1)
                }
                Icon(
                    painter = batteryIcon,
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


