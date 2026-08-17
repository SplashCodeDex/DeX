package com.dexstudios.dex.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlin.time.Duration.Companion.seconds
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

class DiscoveryEngine(
    private val deviceConfig: DeviceConfig,
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cleanupJob: Job? = null
    private var identityWatchJob: Job? = null

    private val _devices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredDevice>> = _devices.asStateFlow()

    // Side-map: always holds the latest seen payload + timestamp per fingerprint so the
    // TTL cleanup stays accurate even when we skip a StateFlow emission for duplicate payloads.
    private val seenDevices = ConcurrentHashMap<String, DiscoveredDevice>()

    private var nsdManagerHelper: NsdManagerHelper? = null
    private var udpManager: UdpMulticastManager? = null

    private fun getDeviceName(): String {
        return runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
                ?: android.provider.Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }.getOrNull() ?: android.os.Build.MODEL ?: "Android Device"
    }

    private val localInfo: RegisterDto
        get() = RegisterDto(
            alias = deviceConfig.alias.ifBlank { getDeviceName() },
            version = "2.0",
            deviceModel = android.os.Build.MODEL ?: "Android",
            deviceType = "mobile",
            fingerprint = deviceConfig.fingerprint,
            port = DeXPorts.HTTPS,
            protocol = "https",
            // This device no longer hosts a LocalSend receiver; pushes arrive via the PC's WebSocket
            download = false,
            identityHash = deviceConfig.identityHash,
            googleSub = deviceConfig.googleSub.ifBlank { null }
        )

    fun startDiscovery() {
        Timber.i("Starting DiscoveryEngine (NSD + UDP Multicast)...")
        nsdManagerHelper = NsdManagerHelper(context, localInfo) { device -> addDevice(device) }.apply { start() }
        udpManager = UdpMulticastManager(context, localInfo) { device -> addDevice(device) }.apply { start() }

        cleanupJob = scope.launch {
            while (isActive) {
                delay(10.seconds)
                val now = System.currentTimeMillis()
                _devices.update { map ->
                    map.filterKeys { fp ->
                        // Read timestamp from the side-map — it stays accurate even for
                        // deduplicated broadcasts that skipped a _devices emission.
                        (seenDevices[fp]?.lastSeenTimestamp ?: 0L).let { now - it < 20000 }
                    }
                }
                // Prune side-map entries that have been evicted from _devices
                seenDevices.entries.removeIf { it.key !in _devices.value }
            }
        }

        // Re-advertise whenever the trusted identity or alias changes
        // so the LAN advertisement always carries the current identityHash/googleSub/alias.
        identityWatchJob = scope.launch {
            var lastAdvertised = Triple(deviceConfig.email, deviceConfig.googleSub, deviceConfig.alias)
            combine(deviceConfig.emailFlow, deviceConfig.googleSubFlow, deviceConfig.aliasFlow) { email, sub, alias ->
                Triple(email, sub, alias)
            }
                .drop(1)
                .collectLatest { triple ->
                    if (triple != lastAdvertised) {
                        lastAdvertised = triple
                        Timber.i("Trusted identity or alias changed; re-advertising NSD + UDP")
                        nsdManagerHelper?.stop()
                        nsdManagerHelper = NsdManagerHelper(context, localInfo) { device -> addDevice(device) }.apply { start() }
                        udpManager?.stop()
                        udpManager = UdpMulticastManager(context, localInfo) { device -> addDevice(device) }.apply { start() }
                    }
                }
        }
    }

    fun addDevice(device: DiscoveredDevice) {
        // Task 18: Cap discovered devices to prevent OutOfMemory / DoS from discovery storms
        if (seenDevices.size >= 100 && !seenDevices.containsKey(device.info.fingerprint)) {
            val oldest = seenDevices.minByOrNull { it.value.lastSeenTimestamp }
            if (oldest != null) {
                seenDevices.remove(oldest.key)
            }
        }

        val existing = seenDevices[device.info.fingerprint]
        // Always bump the timestamp in the side-map so the TTL cleanup stays accurate
        seenDevices[device.info.fingerprint] = device

        // Skip StateFlow emission when the payload is identical — periodic rebroadcasts
        // from the same device were causing a full-screen recomposition storm.
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

    fun stopDiscovery() {
        Timber.i("Stopping DiscoveryEngine...")
        nsdManagerHelper?.stop()
        udpManager?.stop()
        cleanupJob?.cancel()
        identityWatchJob?.cancel()
    }

    fun sendManualDiscovery(ip: String, port: Int = DeXPorts.HTTPS) {
        scope.launch {
            // 1. Dual-port UDP Probing (dynamic port + default 48424 port)
            runCatching {
                val replyJson = JSONObject().apply {
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

            // 2. Direct HTTP REST Probe Fallback (solves AP isolation & UDP blockades)
            runCatching {
                val url = java.net.URL("http://$ip:$port/api/localsend/v2/info")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    val fp = json.optString("fingerprint")
                    val alias = json.optString("alias", "PC Engine")
                    if (fp.isNotBlank()) {
                        val dto = RegisterDto(
                            alias = alias,
                            version = json.optString("version", "2.0"),
                            deviceModel = json.optString("deviceModel", "Windows PC"),
                            deviceType = json.optString("deviceType", "desktop"),
                            fingerprint = fp,
                            port = json.optInt("port", port),
                            quicPort = json.optInt("quicPort", DeXPorts.QUIC),
                            tcpFallbackPort = json.optInt("tcpFallbackPort", DeXPorts.PULL),
                            protocol = json.optString("protocol", "https"),
                            download = false,
                            identityHash = json.optString("identityHash").ifBlank { null },
                            googleSub = json.optString("googleSub").ifBlank { null }
                        )
                        addDevice(DiscoveredDevice(ip = ip, info = dto))
                    }
                }
            }
        }
    }
}
