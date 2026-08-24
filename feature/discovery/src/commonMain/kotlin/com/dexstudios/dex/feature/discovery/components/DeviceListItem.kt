package com.dexstudios.dex.feature.discovery.components
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.designsystem.components.DeXButton
import com.dexstudios.dex.core.designsystem.components.DeXPanel
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.*
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery1
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery2
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery3
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery4
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery5
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_charging
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_battery_full
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_devices
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_wifi
import com.dexstudios.dex.core.network.DiscoveredDevice
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceListItem(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isTrusted: Boolean = AuthState.pairedFingerprints.value.contains(device.info.fingerprint),
    wallpaper: org.jetbrains.compose.resources.DrawableResource? = null,
    onButtonClick: () -> Unit = onClick,
) {
    val realBattery = device.info.battery
    val isCharging = device.info.isCharging == true
    val realWifiBand = device.info.wifiBand?.ifBlank { "5GHz" } ?: "5GHz"

    val batteryIcon =
        if (isCharging) {
            painterResource(Res.drawable.ic_fluent_battery_charging)
        } else if (realBattery != null) {
            when {
                realBattery <= 15 -> painterResource(Res.drawable.ic_fluent_battery1)
                realBattery <= 35 -> painterResource(Res.drawable.ic_fluent_battery2)
                realBattery <= 50 -> painterResource(Res.drawable.ic_fluent_battery3)
                realBattery <= 70 -> painterResource(Res.drawable.ic_fluent_battery4)
                realBattery <= 85 -> painterResource(Res.drawable.ic_fluent_battery5)
                else -> painterResource(Res.drawable.ic_fluent_battery_full)
            }
        } else {
            painterResource(Res.drawable.ic_fluent_battery_full)
        }

    val cardShape = RoundedCornerShape(48.dp)

    DeXPanel(
        modifier = modifier
            .bubbleFluidity(targetScale = 0.98f)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        shadowRadius = 16.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
            // 1. Wallpaper or Placeholder
            if (wallpaper != null) {
                Image(
                    painter = painterResource(wallpaper),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_devices),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .offset(y = (-32).dp)
                            .alpha(0.15f),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 2. Glassy Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            0.6f to MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            1f to MaterialTheme.colorScheme.surface,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 24.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = device.info.alias,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = device.info.deviceModel.ifBlank { stringResource(Res.string.device_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeviceIconTag(icon = painterResource(Res.drawable.ic_fluent_wifi))
                    if (isTrusted && realBattery != null) {
                        DeviceIconTag(icon = batteryIcon)
                        DeviceTag(text = realWifiBand)
                        DeviceTag(text = "$realBattery%")
                        DeviceTag(text = "Paired")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                DeXButton(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        text = if (isTrusted) "Send File" else "Connect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIconTag(icon: androidx.compose.ui.graphics.painter.Painter) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun DeviceTag(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
