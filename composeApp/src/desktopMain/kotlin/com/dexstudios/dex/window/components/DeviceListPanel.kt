package com.dexstudios.dex.window.components

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
 * Default mock WAN scaffolding profiles matching WPF legacy parity with genuine avatar images.
 */
fun defaultWanPlaceholders(): List<DeviceItemUiModel> = listOf(
    DeviceItemUiModel(
        id = "wan_user_1",
        alias = "Ama Serwaa",
        modelText = "Phone (WAN)",
        ip = "wan.dex.net",
        fingerprint = "wan_fp_1",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "user2@dex.net",
        avatarDrawable = Res.drawable.user1_avatar
    ),
    DeviceItemUiModel(
        id = "wan_user_2",
        alias = "Akua Donkor",
        modelText = "Phone (WAN)",
        ip = "wan.dex.net",
        fingerprint = "wan_fp_2",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "user1@dex.net",
        avatarDrawable = Res.drawable.user2_avatar
    ),
    DeviceItemUiModel(
        id = "wan_user_3",
        alias = "Kwame Asante",
        modelText = "Phone (WAN)",
        ip = "wan.dex.net",
        fingerprint = "wan_fp_3",
        isPaired = true,
        isOnline = false,
        isWanPlaceholder = true,
        wanEmail = "user3@dex.net",
        avatarDrawable = Res.drawable.user3_avatar
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
    wanPlaceholders: List<DeviceItemUiModel> = defaultWanPlaceholders(),
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Discovered Devices Section (Only rendered if devices discovered)
        if (discoveredDevices.isNotEmpty()) {
            item(key = "hdr_discovered") {
                Text(
                    text = "Discovered Devices",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeXTheme.colors.secondaryText,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
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
                fontWeight = FontWeight.SemiBold,
                color = DeXTheme.colors.secondaryText,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
            )
        }

        if (pairedDevices.isEmpty() && wanPlaceholders.isEmpty()) {
            item(key = "empty_paired") {
                Text(
                    text = "No paired devices yet",
                    fontSize = 12.sp,
                    color = DeXTheme.colors.secondaryText.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        } else {
            items(pairedDevices, key = { "paired_${it.fingerprint.ifBlank { it.ip }}" }) { device ->
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("Send Clipboard") { onSendClipboard(device) },
                            ContextMenuItem("Mirror Screen") { onMirrorScreen(device) },
                            ContextMenuItem("Copy IP Address") { onCopyIp(device.ip) },
                            ContextMenuItem("Connect ADB") { onConnectAdb(device) },
                            ContextMenuItem("Disconnect ADB") { onDisconnectAdb(device) },
                            ContextMenuItem("Rename / Alias") { onRenameDevice(device) },
                            ContextMenuItem("Forget Device") { onForgetDevice(device) }
                        )
                    }
                ) {
                    DeviceListItemRow(
                        device = device,
                        onClick = { onSendFile(device) }
                    )
                }
            }

            // WAN Scaffolding Profiles
            items(wanPlaceholders, key = { "wan_${it.id}" }) { device ->
                DeviceListItemRow(
                    device = device,
                    onClick = { /* WAN Scaffolding - non-interactive */ }
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
        isHovered -> DeXTheme.colors.accent
        else -> Color.Transparent
    }

    val alpha = if (device.isOnline) 1.0f else 0.5f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                            Modifier.background(DeXTheme.colors.accent)
                        } else if (device.isOnline) {
                            Modifier.background(DeXTheme.colors.secondary)
                        } else {
                            Modifier
                                .border(1.5.dp, DeXTheme.colors.secondaryText.copy(alpha = 0.5f), CircleShape)
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
                        tint = if (device.isOnline) Color.Black else DeXTheme.colors.secondaryText,
                        modifier = Modifier.size(20.dp)
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
                        .background(DeXTheme.colors.secondary)
                        .border(2.dp, DeXTheme.colors.accent, CircleShape)
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DeXTheme.colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = device.wanEmail ?: device.modelText.ifBlank { device.ip },
                    fontSize = 12.sp,
                    color = DeXTheme.colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (device.isOnline) {
                    if (!device.wifiBand.isNullOrBlank()) {
                        Icon(
                            imageVector = MaterialSymbols.Wifi,
                            contentDescription = "WiFi",
                            tint = DeXTheme.colors.secondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = device.wifiBand,
                            fontSize = 11.sp,
                            color = DeXTheme.colors.secondaryText
                        )
                    }

                    if (device.batteryPercent != null) {
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
                            tint = DeXTheme.colors.secondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${device.batteryPercent}%",
                            fontSize = 11.sp,
                            color = DeXTheme.colors.secondaryText
                        )
                    }
                } else if (!device.isWanPlaceholder) {
                    Text(
                        text = "• Offline",
                        fontSize = 11.sp,
                        color = DeXTheme.colors.secondaryText.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
