package com.dexstudios.dex.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.ui.components.DeviceListItem

@Composable
fun MainScreenCompact(
    listState: LazyListState,
    consolidatedTrusted: List<DiscoveredDevice>,
    untrustedDevices: List<DiscoveredDevice>,
    search: String,
    isHighlighted: Boolean = false,
    showHelpHint: Boolean,
    onTrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onUntrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onDeviceLongClick: (DiscoveredDevice) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusBarHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (isHighlighted) 2f else 0f),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. "My Devices" Section (Horizontal Carousel)
        val dummyMatchCount = listOf("Gaming PC", "Home Server", "Work Laptop").count { it.contains(search, ignoreCase = true) }
        if (consolidatedTrusted.isNotEmpty() || dummyMatchCount > 0) {
            item {
                Column(modifier = Modifier.padding(top = statusBarHeight + 64.dp, bottom = 8.dp)) {
                    Text(
                        text = "My Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(consolidatedTrusted, key = { it.info.fingerprint }) { device ->
                            DeviceListItem(
                                modifier = Modifier.width(300.dp),
                                device = device,
                                isTrusted = true,
                                isHighlighted = isHighlighted,
                                onClick = { },
                                onButtonClick = { onTrustedDeviceButtonClick(device) },
                                onLongClick = { onDeviceLongClick(device) }
                            )
                        }

                        if ("Gaming PC".contains(search, ignoreCase = true)) {
                            item {
                                DummyDeviceCard(
                                    alias = "Gaming PC",
                                    model = "Custom Build (RTX 4090)",
                                    wallpaper = R.drawable.wallpaper_gaming,
                                    isHighlighted = isHighlighted
                                )
                            }
                        }
                        if ("Home Server".contains(search, ignoreCase = true)) {
                            item {
                                DummyDeviceCard(
                                    alias = "Home Server",
                                    model = "TrueNAS Core",
                                    wallpaper = R.drawable.wallpaper_server,
                                    isHighlighted = isHighlighted
                                )
                            }
                        }
                        if ("Work Laptop".contains(search, ignoreCase = true)) {
                            item {
                                DummyDeviceCard(
                                    alias = "Work Laptop",
                                    model = "MacBook Pro M3",
                                    wallpaper = R.drawable.wallpaper_laptop,
                                    isHighlighted = isHighlighted
                                )
                            }
                        }
                    }
                }
            }
        } else if (search.isNotBlank() && untrustedDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarHeight + 120.dp, bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No devices matching \"$search\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item { Spacer(Modifier.height(statusBarHeight + 64.dp)) }
        }

        // 2. "Discovered" Section Title
        if (untrustedDevices.isNotEmpty() || search.isBlank()) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "Discovered",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(untrustedDevices, key = { it.info.fingerprint }) { device ->
                            DeviceListItem(
                                modifier = Modifier.width(300.dp),
                                device = device,
                                isTrusted = false,
                                isHighlighted = isHighlighted,
                                onClick = { },
                                onButtonClick = { onUntrustedDeviceButtonClick(device) },
                                onLongClick = { onDeviceLongClick(device) }
                            )
                        }

                        if (search.isBlank()) {
                            item {
                                ScanToAddDeviceCard(
                                    showHelpHint = showHelpHint,
                                    onScanClick = onScanClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
