package com.dexstudios.dex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.spatialMenuEnter
import com.dexstudios.dex.ui.theme.spatialMenuExit
import com.kyant.backdrop.Backdrop

/**
 * A floating bubble card that appears on long-press of a [device].
 */
@Composable
fun DeviceContextMenu(
    device: DiscoveredDevice,
    isTrusted: Boolean,
    backdrop: Backdrop,
    onSendFile: () -> Unit,
    onPair: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    val transitionState = remember { MutableTransitionState(false) }
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { transitionState.targetState = true }

    fun dismiss() {
        transitionState.targetState = false
    }

    LaunchedEffect(transitionState.targetState) {
        if (transitionState.targetState) hasOpened = true
    }

    LaunchedEffect(transitionState.currentState, transitionState.isIdle) {
        if (hasOpened && transitionState.isIdle && !transitionState.targetState) onDismiss()
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = spatialMenuEnter(),
        exit = spatialMenuExit(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassPanel(
                    backdrop = backdrop, // Sample the underlying screen
                    config = LiquidGlassPresets.DynamicIsland,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        DeviceContextMenuHeader(
                            device = device,
                            isTrusted = isTrusted
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )

                        if (isTrusted) {
                            DeviceContextMenuItem(
                                icon = MaterialSymbols.Send,
                                label = stringResource(R.string.device_send_file),
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = { onSendFile(); dismiss() }
                            )
                            DeviceContextMenuItem(
                                icon = MaterialSymbols.Tune,
                                label = stringResource(R.string.device_forget),
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { onForget(); dismiss() }
                            )
                        } else {
                            DeviceContextMenuItem(
                                icon = MaterialSymbols.Devices,
                                label = stringResource(R.string.device_pair),
                                tint = MaterialTheme.colorScheme.onSurface,
                                onClick = { onPair(); dismiss() }
                            )
                        }

                        DeviceContextMenuItem(
                            icon = MaterialSymbols.Computer,
                            label = stringResource(
                                if (showDetails) R.string.device_details_hide
                                else R.string.device_details
                            ),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = { showDetails = !showDetails }
                        )

                        if (showDetails) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            DeviceContextMenuDetails(device = device)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceContextMenuHeader(
    device: DiscoveredDevice,
    isTrusted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MaterialSymbols.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = device.info.alias,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isTrusted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text(
                            text = stringResource(R.string.device_paired),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = device.info.deviceModel.ifBlank { stringResource(R.string.device_unknown) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceContextMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DeviceContextMenuDetails(device: DiscoveredDevice) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DeviceDetailRow(stringResource(R.string.device_ip), device.ip)
        DeviceDetailRow(stringResource(R.string.device_port), device.info.port.toString())
        DeviceDetailRow(stringResource(R.string.device_protocol), device.info.protocol)
        DeviceDetailRow(stringResource(R.string.device_version), device.info.version)
        DeviceDetailRow(stringResource(R.string.device_fingerprint), device.info.fingerprint)
    }
}

@Composable
private fun DeviceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
