package com.dexstudios.dex.core.network

class JvmHardwareTelemetry : HardwareTelemetry {
    override fun getBatteryLevel(): Int {
        // Desktop battery level requires JNI or parsing WMI/sysfs.
        // For Phase 1, we return -1 (unknown) for desktop.
        return -1
    }

    override fun getWifiInfo(): Pair<String?, Int> {
        // Desktop WiFi SSID requires platform-specific commands (netsh on Windows, airport on macOS).
        // For Phase 1, we return no connection for desktop telemetry.
        return null to HardwareTelemetry.RSSI_INVALID
    }
}
