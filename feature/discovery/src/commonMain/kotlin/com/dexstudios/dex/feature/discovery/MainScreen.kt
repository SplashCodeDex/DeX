package com.dexstudios.dex.feature.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.designsystem.components.DeXScrollbar
import com.dexstudios.dex.feature.discovery.components.MainScreenCompact
import com.dexstudios.dex.feature.discovery.components.MainScreenGrid

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    isCompact: Boolean = false,
    trustedDevices: List<DiscoveredDevice> = emptyList(),
    untrustedDevices: List<DiscoveredDevice> = emptyList(),
    isBluetoothEnabled: Boolean = false,
    isWifiEnabled: Boolean = false,
    isDiscoverable: Boolean = true,
    onToggleDiscoverable: (Boolean) -> Unit = {},
    onRefreshDiscovery: () -> Unit = {},
    onPickFiles: () -> Unit = {},
    onPickFolder: () -> Unit = {},
    onShowConnectionOptions: () -> Unit = {}
) {
    // Determine which layout to use
    Box(modifier = modifier.fillMaxSize()) {
        if (isCompact) {
            MainScreenCompact(
                listState = listState,
                consolidatedTrusted = trustedDevices,
                untrustedDevices = untrustedDevices,
                search = "",
                showHelpHint = false,
                onTrustedDeviceButtonClick = { onPickFiles() },
                onUntrustedDeviceButtonClick = { onPickFiles() },
                onDeviceLongClick = {},
                onScanClick = onRefreshDiscovery,
                modifier = modifier
            )
        } else {
            MainScreenGrid(
                consolidatedTrusted = trustedDevices,
                untrustedDevices = untrustedDevices,
                search = "",
                showHelpHint = false,
                onTrustedDeviceButtonClick = { onPickFiles() },
                onUntrustedDeviceButtonClick = { onPickFiles() },
                onDeviceLongClick = {},
                onScanClick = onRefreshDiscovery,
                modifier = modifier
            )
        }

        DeXScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
