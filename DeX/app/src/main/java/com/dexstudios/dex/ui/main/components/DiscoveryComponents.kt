package com.dexstudios.dex.ui.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.RegisterDto
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.DeviceListItem
import com.dexstudios.dex.ui.components.DeXButton
import com.dexstudios.dex.ui.components.DeXPanel
import com.dexstudios.dex.ui.components.bubbleFluidity
import com.dexstudios.dex.ui.components.glass.LiquidGlassPanel
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScanToAddDeviceCard(
    showHelpHint: Boolean,
    onScanClick: () -> Unit
) {
    var showHelpContent by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(48.dp)
    val localBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .width(300.dp)
            .height(340.dp)
            .bubbleFluidity(targetScale = 0.97f, pullFactor = 0.05f)
            .clip(cardShape)
    ) {
        // 1. The Captured Layer (Background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(localBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // 2. The Glass Panel (provides glare and shadow)
        LiquidGlassPanel(
            backdrop = localBackdrop,
            shape = cardShape,
            modifier = Modifier.fillMaxSize(),
            config = LiquidGlassPresets.ShinyCard.copy(shadowRadius = 4.dp)
        ) {
            ScanCardContent(
                showHelpContent = showHelpContent,
                showHelpHint = showHelpHint,
                onScanClick = onScanClick,
                onToggleHelp = { showHelpContent = it }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScanCardContent(
    showHelpContent: Boolean,
    showHelpHint: Boolean,
    onScanClick: () -> Unit,
    onToggleHelp: (Boolean) -> Unit
) {
    AnimatedContent(
        targetState = showHelpContent,
        transitionSpec = {
            (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 0.95f))
        },
        label = "scan_card_content"
    ) { isHelp ->
        if (isHelp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.discovery_help_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoveryHelpStep(number = "1", text = stringResource(R.string.discovery_help_step1))
                    DiscoveryHelpStep(number = "2", text = stringResource(R.string.discovery_help_step2))
                    DiscoveryHelpStep(number = "3", text = stringResource(R.string.discovery_help_step3))
                }

                Spacer(modifier = Modifier.weight(1f))

                DeXButton(
                    onClick = { onToggleHelp(false) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.discovery_help_close), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon centered in the top area
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.QrCodeScanner,
                            contentDescription = "Scan",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Scan to add Device",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = showHelpHint) { onToggleHelp(true) },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showHelpHint,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "hint_text"
                    ) { hintActive ->
                        if (hintActive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = MaterialSymbols.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.discovery_help_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                text = "QRCode must be triggered from PC",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                DeXButton(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape
                ) {
                    Text("Scan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun DiscoveryHelpStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DummyDeviceCard(alias: String, model: String, wallpaper: Any) {
    val dummyDevice = remember(alias, model) {
        DiscoveredDevice(
            ip = "0.0.0.0",
            info = RegisterDto(
                alias = alias,
                version = "1.0",
                deviceModel = model,
                deviceType = "pc",
                fingerprint = alias,
                port = 0,
                protocol = "https",
                download = true
            )
        )
    }
    DeviceListItem(
        device = dummyDevice,
        onClick = {}, // Do nothing as requested
        modifier = Modifier.width(300.dp),
        isTrusted = true,
        wallpaper = wallpaper
    )
}
