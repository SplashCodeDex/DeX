package com.dexstudios.dex.ui.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.network.DiscoveredDevice

@Composable
fun MainScreenGrid(
    consolidatedTrusted: List<DiscoveredDevice>,
    untrustedDevices: List<DiscoveredDevice>,
    search: String,
    showHelpHint: Boolean,
    onTrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onTrustedDeviceLongClick: (DiscoveredDevice) -> Unit,
    onUntrustedDeviceButtonClick: (DiscoveredDevice) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    columns: GridCells = GridCells.Adaptive(minSize = 260.dp),
    statusBarHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = statusBarHeight + 64.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (search.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
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
