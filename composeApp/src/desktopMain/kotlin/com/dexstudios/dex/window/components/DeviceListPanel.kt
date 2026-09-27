package com.dexstudios.dex.window.components
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.components.buttons.DeXButton
import com.dexstudios.dex.core.designsystem.components.buttons.DeXButtonDefaults
import com.dexstudios.dex.core.designsystem.components.buttons.DeXButtonShadow
import com.dexstudios.dex.core.designsystem.components.glass.DefaultGlareIntensity
import com.dexstudios.dex.core.designsystem.components.glass.shinyGlare
import com.dexstudios.dex.core.designsystem.components.island.DynamicFluidityConfig
import com.dexstudios.dex.core.designsystem.components.island.DynamicMotionConfig
import com.dexstudios.dex.core.designsystem.components.lottie.DevicesMorphAnimation
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_arrow_back
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery1
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery2
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery4
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_charging
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_full
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_computer
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_smartphone
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_wifi
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
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
    val rawDevice: DiscoveredDevice? = null,
)

/**
 * DeviceListPanel:
 * - Section 1: Discovered Devices (UDP discovered, untrusted -> click initiates PIN pairing)
 * - Section 2: Your Devices (Paired trusted devices with live telemetry, battery %, and wifi band)
 * - Right-click context menus with 1:1 action routing
 */
@Composable
fun DeviceListPanel(
    discoveredDevices: List<DeviceItemUiModel>,
    pairedDevices: List<DeviceItemUiModel>,
    isPanelVisible: Boolean = true,
    onPairDevice: (DeviceItemUiModel) -> Unit,
    onViewDeviceStatus: (DeviceItemUiModel?) -> Unit = {},
    onSendFile: (DeviceItemUiModel) -> Unit = {},
    onSendClipboard: (DeviceItemUiModel) -> Unit = {},
    onMirrorScreen: (DeviceItemUiModel) -> Unit = {},
    onConnectAdb: (DeviceItemUiModel) -> Unit = {},
    onDisconnectAdb: (DeviceItemUiModel) -> Unit = {},
    onCopyIp: (String) -> Unit = {},
    onForgetDevice: (DeviceItemUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (discoveredDevices.isEmpty() && pairedDevices.isEmpty()) {
            item(key = "empty_state") {
                DeviceEmptyState(
                    isVisible = isPanelVisible,
                )
            }
        }

        // Discovered Devices Section (Only rendered if devices discovered)
        if (discoveredDevices.isNotEmpty()) {
            item(key = "hdr_discovered") {
                Text(
                    text = "Discovered Devices",
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
            }

            items(discoveredDevices, key = { "disc_${it.fingerprint.ifBlank { it.ip }}" }) { device ->
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("Pair with PIN") { onPairDevice(device) },
                            ContextMenuItem("Connect ADB") { onConnectAdb(device) },
                            ContextMenuItem("Copy IP Address") { onCopyIp(device.ip) },
                            ContextMenuItem("Forget Device") { onForgetDevice(device) },
                        )
                    },
                ) {
                    DeviceListItemRow(
                        device = device,
                        onClick = { onPairDevice(device) },
                    )
                }
            }
        }

        // Your Devices Section
        if (pairedDevices.isNotEmpty()) {
            item(key = "hdr_your_devices") {
                Text(
                    text = "Your Devices",
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                )
            }
        }

        items(pairedDevices, key = { "paired_${it.fingerprint.ifBlank { it.ip }}" }) { device ->
            ContextMenuArea(
                items = {
                    buildList {
                        add(ContextMenuItem("Device Status") { onViewDeviceStatus(device) })
                        add(ContextMenuItem("Send Clipboard") { onSendClipboard(device) })
                        add(ContextMenuItem("Mirror Screen") { onMirrorScreen(device) })
                        add(ContextMenuItem("Copy IP Address") { onCopyIp(device.ip) })
                        if (device.isAdbConnected) {
                            add(ContextMenuItem("Disconnect ADB") { onDisconnectAdb(device) })
                        } else {
                            add(ContextMenuItem("Connect ADB") { onConnectAdb(device) })
                        }
                        add(ContextMenuItem("Forget Device") { onForgetDevice(device) })
                    }
                },
            ) {
                DeviceListItemRow(
                    device = device,
                    onClick = { onSendFile(device) },
                )
            }
        }
    }
}

/**
 * Geometry for a device list row. A row rather than a button, so it stays transparent at rest,
 * keeps its own lateral WPF kinematics on the caller modifier, and hides the glare rim until it
 * is actually hovered or active.
 */
private val deviceRowStyle = DeXButtonDefaults.dense.copy(
    shape = RoundedCornerShape(12.dp),
    minWidth = 0.dp,
    minHeight = 0.dp,
    horizontalPadding = 16.dp,
    verticalPadding = 10.dp,
    shadow = DeXButtonShadow.None,
    hoverScale = 1.08f,
    glareOnHoverOnly = true,
    fillWidth = true,
    // The row owns its own width; the surface has to match it rather than hug the text.
    fillSurface = true,
    contentGap = 0.dp,
)

@Composable
private fun DeviceListItemRow(device: DeviceItemUiModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val alpha = if (device.isOnline) 1.0f else 0.5f

    // WPF Kinematics: the row slides out laterally rather than lifting, which is why the lateral
    // offset stays on the caller modifier instead of using the primitive's hover lift.
    val transX by animateDpAsState(
        targetValue = if (isHovered) 6.dp else 0.dp,
        animationSpec = DockCardPhysics.ElasticDpSpec,
    )

    DeXButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .graphicsLayer { translationX = transX.toPx() },
        style = deviceRowStyle.copy(shadow = if (device.isActive) DeXButtonShadow.Low else DeXButtonShadow.None),
        containerColor = Color.Transparent,
        hoverContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        interactionSource = interactionSource,
    ) {
        Row(modifier = Modifier.weight(1f).alpha(alpha), verticalAlignment = Alignment.CenterVertically) {
            // 38x38dp Leading Circle Glyph with Sub-Dot Indicator
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .then(
                            if (device.isOnline) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                                    .background(Color.Transparent)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_smartphone),
                        contentDescription = device.alias,
                        tint = if (device.isOnline) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // 12x12dp Online Indicator (Bottom Right)
                if (device.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Device Telemetry Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = device.alias,
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val topSpacing = if (device.isPaired) 2.dp else 0.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = topSpacing),
                ) {
                    val subFontSize = if (device.isPaired) 12.sp else 13.sp
                    Text(
                        text = device.modelText.ifBlank { device.ip },
                        fontSize = subFontSize,
                        lineHeight = subFontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )

                    if (device.isOnline) {
                        if (!device.wifiBand.isNullOrBlank()) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_fluent_wifi),
                                contentDescription = "WiFi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp).size(12.dp),
                            )
                            Text(
                                text = device.wifiBand,
                                fontSize = 12.sp,
                                lineHeight = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .alpha(0.8f),
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
                        modifier = Modifier.padding(end = 4.dp).size(12.dp),
                    )
                    Text(
                        text = "${device.batteryPercent}%",
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Empty state for the dock's device list: the shared DevicesMorph loop, plus the label under it.
 *
 * The animation's ritardando choreography, its 8 MB of keyframes and the "do not load while the
 * card is collapsed" rule all live in [DevicesMorphAnimation] now — this dock and Android's
 * carousel draw the same loop from the same source, which is how the two stopped drifting.
 *
 * @param isVisible the dock card's expanded state; drives the animation's own [enabled] gate.
 */
@Composable
private fun DeviceEmptyState(isVisible: Boolean) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            DevicesMorphAnimation(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                enabled = isVisible,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Searching for devices...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
