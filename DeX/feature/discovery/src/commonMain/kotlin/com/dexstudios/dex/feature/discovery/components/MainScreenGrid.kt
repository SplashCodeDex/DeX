package com.dexstudios.dex.feature.discovery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.generated.resources.*
import com.dexstudios.dex.core.network.DiscoveredDevice

@Composable
fun MainScreenGrid(
    consolidatedTrusted: List<DiscoveredDevice>,
    untrustedDevices: List<DiscoveredDevice>,
    search: String,
    showHelpHint: Boolean,
    onTrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onUntrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onDeviceLongClick: (DiscoveredDevice) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusBarHeight: androidx.compose.ui.unit.Dp = 0.dp
) {

  val gridState = rememberLazyGridState()
  LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 300.dp),
      state = gridState,
      modifier = modifier.fillMaxSize(),
      contentPadding =
          PaddingValues(top = statusBarHeight + 64.dp, start = 16.dp, end = 16.dp, bottom = 88.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val dummyMatchCount =
            listOf("Gaming PC", "Home Server", "Work Laptop").count {
              it.contains(search, ignoreCase = true)
            }
        if (consolidatedTrusted.isNotEmpty() || dummyMatchCount > 0) {

          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = "My Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onBackground)
          }
          items(consolidatedTrusted, key = { it.info.fingerprint }) { device ->
            DeviceListItem(
                device = device,
                isTrusted = true,
                onClick = {},
                onButtonClick = { onTrustedDeviceButtonClick(device) },
                onLongClick = { onDeviceLongClick(device) },
                modifier = Modifier.fillMaxWidth())
          }
          if ("Gaming PC".contains(search, ignoreCase = true)) {

            item {
              DummyDeviceCard(
                  alias = "Gaming PC",
                  model = "Custom Build (RTX 4090)",
                  wallpaper = Res.drawable.wallpaper_gaming)
            }
          }
          if ("Home Server".contains(search, ignoreCase = true)) {

            item {
              DummyDeviceCard(
                  alias = "Home Server",
                  model = "TrueNAS Core",
                  wallpaper = Res.drawable.wallpaper_server)
            }
          }
          if ("Work Laptop".contains(search, ignoreCase = true)) {

            item {
              DummyDeviceCard(
                  alias = "Work Laptop",
                  model = "MacBook Pro M3",
                  wallpaper = Res.drawable.wallpaper_laptop)
            }
          }
        } else if (search.isNotBlank() && untrustedDevices.isEmpty()) {

          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                contentAlignment = Alignment.Center) {
                  Text(
                      text = "No devices matching \"$search\"",
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
          }
        }
        if (untrustedDevices.isNotEmpty() || search.isBlank()) {

          item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Discovered",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.onBackground)
          }
          items(untrustedDevices, key = { it.info.fingerprint }) { device ->
            DeviceListItem(
                device = device,
                isTrusted = false,
                onClick = {},
                onButtonClick = { onUntrustedDeviceButtonClick(device) },
                onLongClick = { onDeviceLongClick(device) },
                modifier = Modifier.fillMaxWidth())
          }
          if (search.isBlank()) {

            item { ScanToAddDeviceCard(showHelpHint = showHelpHint, onScanClick = onScanClick) }
          }
        }
      }
}
