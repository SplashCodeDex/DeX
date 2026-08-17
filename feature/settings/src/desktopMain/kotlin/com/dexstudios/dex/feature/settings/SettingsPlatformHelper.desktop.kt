package com.dexstudios.dex.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dexstudios.dex.network.DeviceConfig

actual class SettingsPlatformHelper {
    actual val appVersion: String = "Desktop 2.0.0"
    actual fun signInWithGoogle(deviceConfig: DeviceConfig) {
        // Desktop implementation for Google Sign-In
    }

    actual fun getDeviceName(): String {
        return "Desktop" // Provide a sensible default or fetch from OS
    }

    actual fun requestIgnoreBatteryOptimizations() {
        // No-op on desktop
    }

    actual fun addQuickSettingsTile() {
        // No-op on desktop
    }

    actual val isIgnoringBatteryOptimizations: Boolean
        get() = true

    actual val canAddQuickSettingsTile: Boolean
        get() = false
}

@Composable
actual fun rememberSettingsPlatformHelper(): SettingsPlatformHelper {
    return remember { SettingsPlatformHelper() }
}

