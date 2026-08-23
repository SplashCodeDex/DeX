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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.spatialMenuEnter
import com.dexstudios.dex.ui.theme.spatialMenuExit
import com.kyant.backdrop.Backdrop

@Composable
fun ConnectionOptionsDialog(
    device: DiscoveredDevice,
    backdrop: Backdrop,
    onPinCode: () -> Unit,
    onQrCode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                DeXGlareCard(
                    shape = RoundedCornerShape(48.dp),
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.connect_device_title, device.info.alias),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        ConnectionOptionItem(
                            icon = MaterialSymbols.Pin,
                            label = stringResource(R.string.connect_option_pin),
                            onClick = { onPinCode(); dismiss() }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ConnectionOptionItem(
                            icon = MaterialSymbols.QrCodeScanner,
                            label = stringResource(R.string.connect_option_qr),
                            onClick = { onQrCode(); dismiss() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    DeXButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
