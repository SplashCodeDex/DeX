package com.example.dex.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dex.R
import com.example.dex.network.AuthState
import com.example.dex.network.DiscoveredDevice

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DeviceListItem(
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isTrusted: Boolean = AuthState.pairedFingerprints.contains(device.info.fingerprint)
) {
    DeXPanel(
        modifier = modifier
            .fillMaxWidth()
            .bubbleFluidity(targetScale = 0.98f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(48.dp), // Deep rounded corners as requested
        shadowRadius = 16.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            // 1. Wallpaper Placeholder (Engine TODO)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(
                        if (device.info.deviceType.lowercase().contains("phone")) R.drawable.ic_smartphone
                        else R.drawable.ic_computer
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).align(Alignment.Center).alpha(0.08f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            1f to MaterialTheme.colorScheme.surface
                        )
                    )
            )

            // 3. Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 24.dp), // 24dp spacing from bottom edge
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.info.alias,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // "Price Tag" Status Bubble
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ) {
                        Text(
                            text = if (isTrusted) "Online" else "Nearby",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = device.info.deviceModel.ifBlank { stringResource(R.string.device_unknown) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceTag(text = if (isTrusted) "Paired" else "Guest")
                    DeviceTag(text = "High Speed")
                    DeviceTag(text = "5GHz")
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Primary Action Button (Pill Shape)
                DeXButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape, // Exact pill shape from reference
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = if (isTrusted) "Send File" else "Connect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTag(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
