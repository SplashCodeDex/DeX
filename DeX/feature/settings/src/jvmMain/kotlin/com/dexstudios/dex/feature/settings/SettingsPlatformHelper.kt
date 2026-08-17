package com.dexstudios.dex.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dexstudios.dex.core.network.DeviceConfig

actual class SettingsPlatformHelper {
    actual fun signInWithGoogle(deviceConfig: DeviceConfig) {
        // Desktop implementation
    }
    actual fun getDeviceName(): String {
        return System.getProperty("user.name") ?: "Desktop PC"
    }
    actual fun requestIgnoreBatteryOptimizations() {
        // Not applicable on Desktop
    }
    actual fun addQuickSettingsTile() {
        // Not applicable on Desktop
    }
    
    actual val appVersion: String
        get() = "1.0.0"
        
    actual val isIgnoringBatteryOptimizations: Boolean
        get() = true
        
    actual val canAddQuickSettingsTile: Boolean
        get() = false
}

@Composable
actual fun rememberSettingsPlatformHelper(): SettingsPlatformHelper {
    return remember { SettingsPlatformHelper() }
}
