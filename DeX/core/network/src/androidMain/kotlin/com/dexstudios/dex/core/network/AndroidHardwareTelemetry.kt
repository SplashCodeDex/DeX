package com.dexstudios.dex.core.network

import android.content.Context
import android.net.wifi.WifiManager

class AndroidHardwareTelemetry(private val context: Context) : HardwareTelemetry {
    override fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager ?: return -1
        val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level >= 0) level else -1
    }

    override fun getWifiInfo(): Pair<String?, Int> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null to HardwareTelemetry.RSSI_INVALID
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo ?: return null to HardwareTelemetry.RSSI_INVALID
        val ssid = info.ssid?.trim()?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return ssid to info.rssi
    }
}
