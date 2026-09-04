package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.protocol.FieldNames
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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

private val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

// We still use java.net.DatagramSocket for the manual probe fallback since both targets are JVM.
// If this ever targets iOS, we would expect/actual the manual probe as well.

/** Wire DTO -> domain model (network boundary only; the domain never sees transport types). */
private fun DiscoveredDevice.toDomain() = com.dexstudios.dex.core.domain.discovery.ObservedDevice(
    ip = ip,
    info = com.dexstudios.dex.core.domain.discovery.DiscoveredDeviceInfo(
        alias = info.alias,
        version = info.version,
        deviceModel = info.deviceModel,
        deviceType = info.deviceType,
        fingerprint = info.fingerprint,
        port = info.port,
        quicPort = info.quicPort,
        tcpFallbackPort = info.tcpFallbackPort,
        protocol = info.protocol,
        download = info.download,
        identityHash = info.identityHash,
        googleSub = info.googleSub,
        battery = info.battery,
        isCharging = info.isCharging,
        wifiBand = info.wifiBand,
        wifiSsid = info.wifiSsid,
    ),
    lastSeenMillis = lastSeenTimestamp,
    viaWan = viaWan,
    viaRoster = viaRoster,
)

/** Domain model -> wire DTO (bridge back for legacy consumers until plan 030 unifies them). */
private fun com.dexstudios.dex.core.domain.discovery.ObservedDevice.toWire() = DiscoveredDevice(
    ip = ip,
    info = RegisterDto(
        alias = info.alias,
        version = info.version,
        deviceModel = info.deviceModel,
        deviceType = info.deviceType,
        fingerprint = info.fingerprint,
        port = info.port,
        quicPort = info.quicPort,
        tcpFallbackPort = info.tcpFallbackPort,
        protocol = info.protocol,
        download = info.download,
        identityHash = info.identityHash,
        googleSub = info.googleSub,
        battery = info.battery,
        isCharging = info.isCharging,
        wifiBand = info.wifiBand,
        wifiSsid = info.wifiSsid,
    ),
    lastSeenTimestamp = lastSeenMillis,
    viaWan = viaWan,
    viaRoster = viaRoster,
)

class DiscoveryEngine(private val deviceConfig: DeviceConfig, private val discoveryServices: List<IDiscoveryService>, private val httpClient: HttpClient) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cleanupJob: Job? = null
    private var identityWatchJob: Job? = null

    // The observed-device state machine lives in the domain (plan 028); this adapter maps
    // wire DTOs into domain models and owns the platform feeds (beacons, probe, identity
    // re-advertisement). The registry's semantics are pinned by DeviceRegistryTest.
    private val registry = com.dexstudios.dex.core.domain.discovery.DeviceRegistry(
        scope = scope,
        nowMillis = { System.currentTimeMillis() },
    )

    private val _devices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredDevice>> = _devices.asStateFlow()

    init {
        // Bridge the domain registry's state to the legacy wire-typed flow. The collector
        // covers registry-internal mutations (sweeps, roster merges done directly on the
        // registry); the adapter's own write paths ALSO refresh synchronously (see
        // [syncFromRegistry]) so legacy inline-assertion callers never race the bridge.
        scope.launch {
            registry.devices.collect { observed ->
                _devices.value = observed.mapValues { (_, d) -> d.toWire() }
            }
        }
    }

    /** Synchronous write-through: legacy callers read `devices.value` immediately after writes. */
    private fun syncFromRegistry() {
        _devices.value = registry.devices.value.mapValues { (_, d) -> d.toWire() }
    }

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
        registry.start()
        val info = localInfo
        discoveryServices.forEach { it.start(info) { device -> addDevice(device) } }

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
        registry.addDevice(device.toDomain())
        syncFromRegistry()
    }

    /** Updates live battery, charging, and Wi-Fi telemetry for an active device. */
    fun updateTelemetry(fingerprint: String, battery: Int? = null, isCharging: Boolean? = null, wifiSsid: String? = null) {
        registry.updateTelemetry(fingerprint, battery, isCharging, wifiSsid)
        syncFromRegistry()
    }

    fun stopDiscovery() {
        discoveryServices.forEach { it.stop() }
        registry.stop()
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

                        val fp = json[FieldNames.FINGERPRINT]?.jsonPrimitive?.content ?: ""
                        val alias = json[FieldNames.ALIAS]?.jsonPrimitive?.content ?: "PC Engine"
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
