package com.dexstudios.dex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

private val lenientJson = Json { ignoreUnknownKeys = true }

// One discovery reaction per announcing fingerprint per window. Coalesces the dual-port
// copies of a single announcement (both listeners receive it) and caps any flood.
private const val PeerDiscoveryCooldownMs = 3000L
private const val MaxTrackedPeers = 256

class DesktopUdpService : IDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var httpsJob: Job? = null
    private var legacyJob: Job? = null
    private var broadcastJob: Job? = null
    private var httpsSocket: MulticastSocket? = null
    private var legacySocket: MulticastSocket? = null

    // Persistent ephemeral socket for unicast replies; reused so replying never pays
    // socket allocation or network-interface enumeration per packet.
    private var replySocket: DatagramSocket? = null
    private val lastSeenByFingerprint = ConcurrentHashMap<String, Long>()

    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered
        replySocket = runCatching { DatagramSocket() }.getOrNull()

        httpsJob = scope.launch { startListening(DeXPorts.HTTPS, 0) }
        legacyJob = scope.launch { startListening(28424, 1) }
        startBroadcasting()
    }

    private suspend fun CoroutineScope.startListening(port: Int, socketIndex: Int) {
        while (isActive) {
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket(port).apply {
                    reuseAddress = true
                    val groupAddr = InetSocketAddress(InetAddress.getByName("224.0.0.167"), port)
                    NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                        runCatching {
                            if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                                joinGroup(groupAddr, ni)
                            }
                        }
                    }
                }
                if (socketIndex == 0) httpsSocket = socket else legacySocket = socket

                val buffer = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        handleIncomingPacket(packet)
                    } catch (e: Exception) {
                        if (!isActive) break
                        if (socket.isClosed) break
                    }
                }
            } catch (e: Exception) {
                if (!isActive) break
                kotlinx.coroutines.delay(1000)
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private fun handleIncomingPacket(packet: DatagramPacket) {
        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
        runCatching {
            val json = lenientJson.parseToJsonElement(msg).jsonObject
            val fp = json["fingerprint"]?.jsonPrimitive?.contentOrNull ?: ""
            if (fp.isEmpty() || fp == localInfo?.fingerprint) return

            // Cooldown gate: each announcing fingerprint earns at most one discovery
            // reaction per window. Without this, every reply we send is itself an
            // announcement that other peers answer, and those answers re-trigger us -
            // an unbounded mutual amplification loop that saturates a CPU core.
            val now = System.currentTimeMillis()
            val lastSeen = lastSeenByFingerprint[fp]
            if (lastSeen != null && now - lastSeen < PeerDiscoveryCooldownMs) return
            lastSeenByFingerprint[fp] = now
            if (lastSeenByFingerprint.size > MaxTrackedPeers) pruneStalePeerEntries(now)

            val ip = packet.address.hostAddress ?: return

            onDeviceDiscovered?.invoke(
                DiscoveredDevice(
                    ip = ip,
                    info = RegisterDto(
                        alias = json["alias"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                        version = json["version"]?.jsonPrimitive?.contentOrNull ?: "2.0",
                        deviceModel = json["deviceModel"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                        deviceType = json["deviceType"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                        fingerprint = fp,
                        port = json["port"]?.jsonPrimitive?.intOrNull ?: DeXPorts.HTTPS,
                        quicPort = json["quicPort"]?.jsonPrimitive?.intOrNull ?: DeXPorts.QUIC,
                        tcpFallbackPort = json["tcpFallbackPort"]?.jsonPrimitive?.intOrNull ?: DeXPorts.PULL,
                        protocol = json["protocol"]?.jsonPrimitive?.contentOrNull ?: "https",
                        download = json["download"]?.jsonPrimitive?.booleanOrNull ?: true,
                        identityHash = json["identityHash"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                        googleSub = json["googleSub"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                    ),
                ),
            )
            sendReply(packet)
        }.onFailure { e -> e.printStackTrace() }
    }

    private fun sendReply(packet: DatagramPacket) {
        val info = localInfo ?: return
        val socket = replySocket ?: return
        runCatching {
            val replyJson = buildJsonObject {
                put("alias", info.alias)
                put("version", info.version)
                put("deviceModel", info.deviceModel)
                put("deviceType", info.deviceType)
                put("fingerprint", info.fingerprint)
                put("port", info.port)
                put("quicPort", info.quicPort)
                put("tcpFallbackPort", info.tcpFallbackPort)
                put("protocol", info.protocol)
                put("download", info.download)
                info.identityHash?.let { put("identityHash", it) }
                info.googleSub?.let { put("googleSub", it) }
            }
            val replyData = replyJson.toString().toByteArray(Charsets.UTF_8)

            // Unicast only, straight back to the sender. The announcement itself already
            // reached every group member, so multicasting the answer would re-broadcast
            // into the group and make every member answer again - the discovery echo
            // storm. Peers that missed this exchange learn about us from their own copy
            // of our periodic announcements instead.
            val ucastPacket = DatagramPacket(replyData, replyData.size, packet.address, packet.port)
            synchronized(socket) { socket.send(ucastPacket) }
        }
    }

    private fun pruneStalePeerEntries(now: Long) {
        lastSeenByFingerprint.entries.removeIf { now - it.value > PeerDiscoveryCooldownMs }
    }

    private fun startBroadcasting() {
        broadcastJob = scope.launch {
            while (isActive) {
                val info = localInfo
                if (info != null) {
                    runCatching {
                        val replyJson = buildJsonObject {
                            put("alias", info.alias)
                            put("version", info.version)
                            put("deviceModel", info.deviceModel)
                            put("deviceType", info.deviceType)
                            put("fingerprint", info.fingerprint)
                            put("port", info.port)
                            put("quicPort", info.quicPort)
                            put("tcpFallbackPort", info.tcpFallbackPort)
                            put("protocol", info.protocol)
                            put("download", info.download)
                            info.identityHash?.let { put("identityHash", it) }
                            info.googleSub?.let { put("googleSub", it) }
                        }
                        val replyData = replyJson.toString().toByteArray(Charsets.UTF_8)
                        val mcastPacketHttps = DatagramPacket(replyData, replyData.size, InetAddress.getByName("224.0.0.167"), DeXPorts.HTTPS)
                        val mcastPacketLegacy = DatagramPacket(replyData, replyData.size, InetAddress.getByName("224.0.0.167"), 28424)

                        // 1. Send Multicast
                        val groupAddrHttps = InetSocketAddress(InetAddress.getByName("224.0.0.167"), DeXPorts.HTTPS)
                        val groupAddrLegacy = InetSocketAddress(InetAddress.getByName("224.0.0.167"), 28424)
                        NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                            runCatching {
                                if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                                    runCatching { httpsSocket?.joinGroup(groupAddrHttps, ni) }
                                    runCatching { legacySocket?.joinGroup(groupAddrLegacy, ni) }
                                    httpsSocket?.networkInterface = ni
                                    httpsSocket?.send(mcastPacketHttps)
                                    legacySocket?.networkInterface = ni
                                    legacySocket?.send(mcastPacketLegacy)
                                }
                            }
                        }

                        // 2. Send Directed Broadcasts (Subnet broadcasts)
                        runCatching {
                            val bcastSocket = replySocket ?: return@runCatching
                            bcastSocket.broadcast = true
                            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                                if (ni.isUp && !ni.isLoopback) {
                                    ni.interfaceAddresses.forEach { ia ->
                                        ia.broadcast?.let { bcastAddr ->
                                            runCatching { bcastSocket.send(DatagramPacket(replyData, replyData.size, bcastAddr, DeXPorts.HTTPS)) }
                                            runCatching { bcastSocket.send(DatagramPacket(replyData, replyData.size, bcastAddr, 28424)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    override fun stop() {
        httpsJob?.cancel()
        legacyJob?.cancel()
        broadcastJob?.cancel()
        runCatching { httpsSocket?.close() }
        runCatching { legacySocket?.close() }
        runCatching { replySocket?.close() }
        replySocket = null
        lastSeenByFingerprint.clear()
    }
}
