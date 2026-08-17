package com.dexstudios.dex.core.network

/**
 * Dedicated, centralized hardware telemetry abstraction for retrieving device state.
 */
interface HardwareTelemetry {
    /** Current battery percentage, or -1 when unavailable. */
    fun getBatteryLevel(): Int

    /** Current WiFi network: (SSID, RSSI dBm). SSID is null and RSSI -127 when not connected. */
    fun getWifiInfo(): Pair<String?, Int>

    companion object {
        const val RSSI_INVALID = -127
    }
}
