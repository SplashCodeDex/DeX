package com.dexstudios.dex.desktop

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Enforcement behind the "Auto-Connect ADB Hotspot" setting ([DeviceConfig.autoAdbHotspotEnabled]).
 *
 * Watches discovery and runs `adb connect` against every newly reachable device while the
 * preference is enabled — joining a phone hotspot surfaces the phone through discovery,
 * which is how "joined the hotspot" is detected without any OS-specific gateway probing.
 *
 * Attempt bookkeeping is keyed by `"fingerprint@ip"`: each address is probed exactly once
 * until it disappears from discovery (or the preference toggles off), so telemetry-driven
 * discovery emissions can never spam reconnect attempts. [AdbManager.connect] self-gates on
 * a bounded TCP probe of port 5555, so unreachable devices cost ~400ms and never hang the
 * collector; results are logged, never surfaced as fake successes.
 */
object AutoAdbHotspotService {
    private var job: Job? = null

    fun start(deviceConfig: DeviceConfig, devicesFlow: StateFlow<Map<String, DiscoveredDevice>>) {
        if (job?.isActive == true) return // Already running — never stack a second collector

        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val attempted = mutableSetOf<String>()
            combine(deviceConfig.autoAdbHotspotEnabledFlow, devicesFlow) { enabled, devices ->
                enabled to devices
            }.collect { (enabled, devices) ->
                if (!enabled) {
                    attempted.clear()
                    return@collect
                }

                // Forget attempts for devices that vanished, so a re-arriving device retries.
                attempted.retainAll(devices.values.mapTo(mutableSetOf()) { it.attemptKey() })

                devices.values.forEach { device ->
                    if (attempted.add(device.attemptKey())) {
                        Logger.i("AutoAdbHotspotService: probing ${device.info.alias} (${device.ip}) for ADB transport")
                        AdbManager.connect(device.ip)
                    }
                }
            }
        }
    }

    private fun DiscoveredDevice.attemptKey(): String = "${info.fingerprint.ifBlank { ip }}@$ip"
}
