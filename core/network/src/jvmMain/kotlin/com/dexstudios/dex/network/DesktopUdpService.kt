package com.dexstudios.dex.network

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

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

class DesktopUdpService : IDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var udpJob: Job? = null
    private var udpSocket: MulticastSocket? = null

    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered

        udpJob = scope.launch {
            runCatching {
                var canReceive = true
                udpSocket = runCatching {
                    MulticastSocket(DeXPorts.HTTPS)
                }.getOrElse {
                    System.err.println("Failed to bind main discovery port; falling back to ephemeral sending port: ${it.message}")
                    canReceive = false
                    MulticastSocket(0)
                }.apply {
                    reuseAddress = true
                    val groupAddr = InetSocketAddress(InetAddress.getByName("224.0.0.167"), DeXPorts.HTTPS)
                    NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                        runCatching {
                            if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                                joinGroup(groupAddr, ni)
                            }
                        }
                    }
                }

                val buffer = ByteArray(2048)

                scope.launch {
                    while (isActive) {
                        broadcastPresence()
                        kotlinx.coroutines.delay(2000)
                    }
                }

                while (isActive) {
                    if (!canReceive) {
                        kotlinx.coroutines.delay(5000)
                        continue
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    handleIncomingPacket(packet)
                }
            }
        }
    }

    private fun handleIncomingPacket(packet: DatagramPacket) {
        val msg = String(packet.data, 0, packet.length)
        runCatching {
            val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(msg).jsonObject
            val fp = json["fingerprint"]?.jsonPrimitive?.content ?: ""
            if (fp.isEmpty() || fp == localInfo?.fingerprint) return

            val ip = packet.address.hostAddress ?: return

            onDeviceDiscovered?.invoke(
                DiscoveredDevice(
                    ip = ip,
                    info = RegisterDto(
                        alias = json["alias"]?.jsonPrimitive?.content ?: "Unknown",
                        version = json["version"]?.jsonPrimitive?.content ?: "2.0",
                        deviceModel = json["deviceModel"]?.jsonPrimitive?.content ?: "Unknown",
                        deviceType = json["deviceType"]?.jsonPrimitive?.content ?: "unknown",
                        fingerprint = fp,
                        port = json["port"]?.jsonPrimitive?.intOrNull ?: DeXPorts.HTTPS,
                        quicPort = json["quicPort"]?.jsonPrimitive?.intOrNull ?: DeXPorts.QUIC,
                        tcpFallbackPort = json["tcpFallbackPort"]?.jsonPrimitive?.intOrNull ?: DeXPorts.PULL,
                        protocol = json["protocol"]?.jsonPrimitive?.content ?: "https",
                        download = json["download"]?.jsonPrimitive?.booleanOrNull ?: true,
                        identityHash = json["identityHash"]?.jsonPrimitive?.content?.ifBlank { null },
                        googleSub = json["googleSub"]?.jsonPrimitive?.content?.ifBlank { null }
                    )
                )
            )
            sendReply(packet)
        }
    }

    private fun getReplyData(): ByteArray? {
        val info = localInfo ?: return null
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
        return replyJson.toString().toByteArray(Charsets.UTF_8)
    }

    private fun broadcastPresence() {
        runCatching {
            val replyData = getReplyData() ?: return
            val mcastPacket = DatagramPacket(replyData, replyData.size, InetAddress.getByName("224.0.0.167"), DeXPorts.HTTPS)
            
            // 1. Multicast
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                runCatching {
                    if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                        udpSocket?.networkInterface = ni
                        udpSocket?.send(mcastPacket)
                    }
                }
            }

            // 2. Directed Subnet Broadcasts & Gateway Pings
            DatagramSocket().use { ds ->
                ds.broadcast = true
                NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                    runCatching {
                        if (ni.isUp && !ni.isLoopback) {
                            ni.interfaceAddresses.forEach { ia ->
                                val bcast = ia.broadcast
                                if (bcast != null) {
                                    runCatching { ds.send(DatagramPacket(replyData, replyData.size, bcast, DeXPorts.HTTPS)) }
                                }
                                val addrBytes = ia.address.address
                                if (addrBytes.size == 4) {
                                    addrBytes[3] = 1.toByte()
                                    runCatching { ds.send(DatagramPacket(replyData, replyData.size, InetAddress.getByAddress(addrBytes), DeXPorts.HTTPS)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendReply(packet: DatagramPacket) {
        broadcastPresence()
        runCatching {
            val replyData = getReplyData() ?: return
            val ucastPacket = DatagramPacket(replyData, replyData.size, packet.address, packet.port)
            DatagramSocket().use { it.send(ucastPacket) }
        }
    }

    override fun stop() {
        udpJob?.cancel()
        runCatching { udpSocket?.close() }
    }
}
