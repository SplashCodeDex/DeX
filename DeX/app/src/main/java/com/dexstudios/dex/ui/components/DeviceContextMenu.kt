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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.theme.spatialMenuEnter
import com.dexstudios.dex.ui.theme.spatialMenuExit

/**
 * A floating bubble card that appears on long-press of a [device].
 *
 * Kept presentational: all actions are hoisted up to the caller so this stays
 * reusable across screens. The card anchors to the bottom for thumb-friendly
 * reach and pops in using the app-wide spatial physics. Entry and exit are both
 * animated: [onDismiss] is invoked only after the exit animation completes, so
 * the caller can unmount this overlay without cutting the exit short.
 */
@Composable
fun DeviceContextMenu(
    device: DiscoveredDevice,
    isTrusted: Boolean,
    onSendFile: () -> Unit,
    onPair: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    // Drives both the pop-in (mount) and pop-out (dismiss) transitions. The
    // content stays composed while [targetState] is false so the exit animation
    // can play; onDismiss is fired only once the exit is idle (finished).
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
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            DeXPanel(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
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
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (isTrusted) {
                        DeviceContextMenuItem(
                            icon = ImageVector.vectorResource(R.drawable.ic_send),
                            label = stringResource(R.string.device_send_file),
                            onClick = { onSendFile(); dismiss() }
                        )
                        DeviceContextMenuItem(
                            icon = ImageVector.vectorResource(R.drawable.ic_tune_outlined),
                            label = stringResource(R.string.device_forget),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { onForget(); dismiss() }
                        )
                    } else {
                        DeviceContextMenuItem(
                            icon = ImageVector.vectorResource(R.drawable.ic_devices_outlined),
                            label = stringResource(R.string.device_pair),
                            onClick = { onPair(); dismiss() }
                        )
                    }

                    DeviceContextMenuItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_computer),
                        label = stringResource(
                            if (showDetails) R.string.device_details_hide
                            else R.string.device_details
                        ),
                        onClick = { showDetails = !showDetails }
                    )

                    if (showDetails) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_computer),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isTrusted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
    tint: Color = MaterialTheme.colorScheme.onSurface
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
