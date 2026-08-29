package com.dexstudios.dex.core.network

import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

// We still use java.net.DatagramSocket for the manual probe fallback since both targets are JVM.
// If this ever targets iOS, we would expect/actual the manual probe as well.

class DiscoveryEngine(private val deviceConfig: DeviceConfig, private val discoveryServices: List<IDiscoveryService>, private val httpClient: HttpClient) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cleanupJob: Job? = null
    private var identityWatchJob: Job? = null

    private val _devices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredDevice>> = _devices.asStateFlow()

    private val seenDevices = ConcurrentHashMap<String, DiscoveredDevice>()

    val localInfo: RegisterDto
        get() = RegisterDto(
            alias = deviceConfig.alias.ifBlank { getPlatformDeviceName() },
            version = "2.0",
            deviceModel = getPlatformDeviceModel(),
            deviceType = getPlatformDeviceType(),
            fingerprint = deviceConfig.fingerprint,
            port = DeXPorts.HTTPS,
            protocol = "https",
            // The desktop hosts the LocalSend v2 receiver (ShareRoutes) — senders may push
            // directly to us. Phones advertise download=false and are served via pull instead.
            download = true,
            // SECURITY: identityHash / googleSub are bearer credentials for auto-trust and
            // must NEVER be advertised. Discovery beacons and GET /info are readable by any
            // LAN peer; leaking them here let an attacker claim same-account trust. Identity
            // is now proven via the identity-challenge/identity-proof exchange on /ws.
            identityHash = null,
            googleSub = null,
        )

    fun startDiscovery() {
        val info = localInfo
        discoveryServices.forEach { it.start(info) { device -> addDevice(device) } }

        cleanupJob = scope.launch {
            while (isActive) {
                delay(10.seconds)
                val now = System.currentTimeMillis()
                _devices.update { map ->
                    map.filterKeys { fp ->
                        (seenDevices[fp]?.lastSeenTimestamp ?: 0L).let { now - it < 20000 }
                    }
                }
                seenDevices.entries.removeIf { it.key !in _devices.value }
            }
        }

        identityWatchJob = scope.launch {
            var lastAdvertised = Triple(deviceConfig.email, deviceConfig.googleSub, deviceConfig.alias)
            combine(deviceConfig.emailFlow, deviceConfig.googleSubFlow, deviceConfig.aliasFlow) { email, sub, alias ->
                Triple(email, sub, alias)
            }
                .drop(1)
                .collectLatest { triple ->
                    if (triple != lastAdvertised) {
                        lastAdvertised = triple
                        val newInfo = localInfo
                        discoveryServices.forEach { it.stop() }
                        discoveryServices.forEach { it.start(newInfo) { device -> addDevice(device) } }
                    }
                }
        }
    }

    fun addDevice(device: DiscoveredDevice) {
        if (seenDevices.size >= 100 && !seenDevices.containsKey(device.info.fingerprint)) {
            val oldest = seenDevices.minByOrNull { it.value.lastSeenTimestamp }
            if (oldest != null) {
                seenDevices.remove(oldest.key)
            }
        }

        val existing = seenDevices[device.info.fingerprint]
        seenDevices[device.info.fingerprint] = device

        val changed = existing == null ||
            existing.ip != device.ip ||
            existing.info != device.info ||
            existing.viaWan != device.viaWan ||
            existing.viaRoster != device.viaRoster
        if (!changed) return

        _devices.update { map ->
            if (map.size >= 100 && !map.containsKey(device.info.fingerprint)) {
                val oldestFp = map.minByOrNull { it.value.lastSeenTimestamp }?.key
                if (oldestFp != null) {
                    (map - oldestFp) + (device.info.fingerprint to device)
                } else {
                    map + (device.info.fingerprint to device)
                }
            } else {
                map + (device.info.fingerprint to device)
            }
        }
    }

    /** Updates live battery, charging, and Wi-Fi telemetry for an active device. */
    fun updateTelemetry(fingerprint: String, battery: Int? = null, isCharging: Boolean? = null, wifiSsid: String? = null) {
        val existing = seenDevices[fingerprint] ?: _devices.value[fingerprint] ?: return
        val updatedInfo = existing.info.copy(
            battery = battery ?: existing.info.battery,
            isCharging = isCharging ?: existing.info.isCharging,
            wifiSsid = wifiSsid ?: existing.info.wifiSsid,
        )
        val updatedDevice = existing.copy(
            info = updatedInfo,
            lastSeenTimestamp = System.currentTimeMillis(),
        )
        addDevice(updatedDevice)
    }

    fun stopDiscovery() {
        discoveryServices.forEach { it.stop() }
        cleanupJob?.cancel()
        identityWatchJob?.cancel()
        // Kill the owning scope too (manual-probe launches included) — shutdown-only API.
        scope.cancel()
    }

    fun sendManualDiscovery(ip: String, port: Int = DeXPorts.HTTPS) {
        scope.launch {
            // 1. Dual-port UDP Probing
            runCatching {
                val replyJson = buildJsonObject {
                    put("alias", localInfo.alias)
                    put("version", localInfo.version)
                    put("deviceModel", localInfo.deviceModel)
                    put("deviceType", localInfo.deviceType)
                    put("fingerprint", localInfo.fingerprint)
                    put("port", localInfo.port)
                    put("quicPort", localInfo.quicPort)
                    put("tcpFallbackPort", localInfo.tcpFallbackPort)
                    put("protocol", localInfo.protocol)
                    put("download", localInfo.download)
                    put("identityHash", localInfo.identityHash)
                    localInfo.googleSub?.let { put("googleSub", it) }
                }
                val data = replyJson.toString().toByteArray(Charsets.UTF_8)
                val targetPorts = setOf(port, DeXPorts.HTTPS)
                DatagramSocket().use { ds ->
                    for (p in targetPorts) {
                        ds.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), p))
                    }
                }
            }

            // 2. Direct HTTPS / HTTP REST Probe Fallback (fail fast: LAN probe must never hang the caller)
            for (scheme in listOf("https", "http")) {
                val success = runCatching {
                    val response: HttpResponse = httpClient.get("$scheme://$ip:$port/api/localsend/v2/info") {
                        timeout { requestTimeoutMillis = 2_500 }
                    }
                    if (response.status.value == 200) {
                        val responseText = response.bodyAsText()
                        val json = lenientJson.parseToJsonElement(responseText).jsonObject

                        val fp = json["fingerprint"]?.jsonPrimitive?.content ?: ""
                        val alias = json["alias"]?.jsonPrimitive?.content ?: "PC Engine"
                        if (fp.isNotBlank()) {
                            val dto = RegisterDto(
                                alias = alias,
                                version = json["version"]?.jsonPrimitive?.contentOrNull ?: "2.0",
                                deviceModel = json["deviceModel"]?.jsonPrimitive?.contentOrNull ?: "Windows PC",
                                deviceType = json["deviceType"]?.jsonPrimitive?.contentOrNull ?: "desktop",
                                fingerprint = fp,
                                port = json["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: port,
                                quicPort = json["quicPort"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: DeXPorts.QUIC,
                                tcpFallbackPort = json["tcpFallbackPort"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: DeXPorts.PULL,
                                protocol = json["protocol"]?.jsonPrimitive?.contentOrNull ?: scheme,
                                download = json["download"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                                    ?: (json["download"]?.jsonPrimitive?.booleanOrNull ?: true),
                                identityHash = json["identityHash"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                                googleSub = json["googleSub"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                            )
                            addDevice(DiscoveredDevice(ip = ip, info = dto))
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }.getOrDefault(false)
                if (success) break
            }
        }
    }
}
