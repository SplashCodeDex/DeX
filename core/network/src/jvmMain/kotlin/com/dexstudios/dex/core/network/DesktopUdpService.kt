package com.dexstudios.dex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

class DesktopUdpService : IDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var httpsJob: Job? = null
    private var legacyJob: Job? = null
    private var httpsSocket: MulticastSocket? = null
    private var legacySocket: MulticastSocket? = null

    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered

        httpsJob = scope.launch { startListening(DeXPorts.HTTPS, 0) }
        legacyJob = scope.launch { startListening(28424, 1) }
    }

    private fun CoroutineScope.startListening(port: Int, socketIndex: Int) {
        runCatching {
            val socket = MulticastSocket(port).apply {
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
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                handleIncomingPacket(packet)
            }
        }
    }

    private fun handleIncomingPacket(packet: DatagramPacket) {
        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
        runCatching {
            val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(msg).jsonObject
            val fp = json["fingerprint"]?.jsonPrimitive?.contentOrNull ?: ""
            if (fp.isEmpty() || fp == localInfo?.fingerprint) return

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
                        googleSub = json["googleSub"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                    )
                )
            )
            sendReply(packet)
        }
    }

    private fun sendReply(packet: DatagramPacket) {
        val info = localInfo ?: return
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
            val ucastPacket = DatagramPacket(replyData, replyData.size, packet.address, packet.port)

            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                runCatching {
                    if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                        httpsSocket?.networkInterface = ni
                        httpsSocket?.send(mcastPacketHttps)
                        legacySocket?.networkInterface = ni
                        legacySocket?.send(mcastPacketLegacy)
                    }
                }
            }
            runCatching { DatagramSocket().use { it.send(ucastPacket) } }
        }
    }

    override fun stop() {
        httpsJob?.cancel()
        legacyJob?.cancel()
        runCatching { httpsSocket?.close() }
        runCatching { legacySocket?.close() }
    }
}

