package com.dexstudios.dex.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.network.DiscoveredDevice

@Composable
fun MainScreenCompact(
    listState: LazyListState,
    consolidatedTrusted: List<DiscoveredDevice>,
    untrustedDevices: List<DiscoveredDevice>,
    search: String,
    showHelpHint: Boolean,
    onTrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onTrustedDeviceLongClick: (DiscoveredDevice) -> Unit,
    onUntrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusBarHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(statusBarHeight + 64.dp))
        }

        if (search.isNotBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Searching: \"$search\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
