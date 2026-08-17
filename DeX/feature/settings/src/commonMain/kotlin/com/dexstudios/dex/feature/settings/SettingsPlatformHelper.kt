package com.dexstudios.dex.feature.settings

import androidx.compose.runtime.Composable
import com.dexstudios.dex.core.network.DeviceConfig

expect class SettingsPlatformHelper {
    fun signInWithGoogle(deviceConfig: DeviceConfig)
    fun getDeviceName(): String
    fun requestIgnoreBatteryOptimizations()
    fun addQuickSettingsTile()
    
    val appVersion: String
    val isIgnoringBatteryOptimizations: Boolean
    val canAddQuickSettingsTile: Boolean
}

@Composable
expect fun rememberSettingsPlatformHelper(): SettingsPlatformHelper


