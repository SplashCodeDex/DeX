package com.dexstudios.dex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.RegisterDto
import com.dexstudios.dex.ui.theme.DeXTheme

@Preview(showBackground = true)
@Composable
fun DeviceListItemUntrustedPreview() {
    DeXTheme {
        val device = DiscoveredDevice(
            ip = "192.168.1.100",
            info = RegisterDto(
                alias = "Gaming PC",
                version = "1.0",
                deviceModel = "Windows Desktop",
                deviceType = "PC",
                fingerprint = "fake_fingerprint",
                port = 8080,
                protocol = "http",
                download = true,
                battery = 85,
                isCharging = false,
                wifiBand = "5GHz"
            )
        )
        DeviceListItem(
            device = device,
            onClick = {},
            isTrusted = false
        )
    }
}
