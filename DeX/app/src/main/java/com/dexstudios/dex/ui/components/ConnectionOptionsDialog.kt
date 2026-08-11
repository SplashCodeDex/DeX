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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
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
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassPanel(
                backdrop = backdrop,
                config = LiquidGlassPresets.DynamicIsland,
                shape = RoundedCornerShape(40.dp),
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
                    Text(
                        text = stringResource(R.string.connect_device_title, device.info.alias),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    ConnectionOptionItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_devices_outlined),
                        label = stringResource(R.string.connect_option_pin),
                        onClick = { onPinCode(); dismiss() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ConnectionOptionItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_qr_code_scanner),
                        label = stringResource(R.string.connect_option_qr),
                        onClick = { onQrCode(); dismiss() }
                    )
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.1f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
