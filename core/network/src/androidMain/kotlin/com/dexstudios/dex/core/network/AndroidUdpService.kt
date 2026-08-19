package com.dexstudios.dex.core.network

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber
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

class AndroidUdpService(
    private val context: Context
) : IDiscoveryService {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var udpJob: Job? = null
    private var udpSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var localInfo: RegisterDto? = null
    private var onDeviceDiscovered: ((DiscoveredDevice) -> Unit)? = null

    override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
        this.localInfo = localInfo
        this.onDeviceDiscovered = onDeviceDiscovered

        udpJob = scope.launch {
            runCatching {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("dex_multicast_lock").apply {
                    setReferenceCounted(true)
                    acquire()
                }

                udpSocket = MulticastSocket(DeXPorts.HTTPS).apply {
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
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    handleIncomingPacket(packet)
                }
            }.onFailure { Timber.e(it, "Multicast loop failed") }
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
            val mcastPacket = DatagramPacket(replyData, replyData.size, InetAddress.getByName("224.0.0.167"), DeXPorts.HTTPS)
            val ucastPacket = DatagramPacket(replyData, replyData.size, packet.address, packet.port)

            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                runCatching {
                    if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                        udpSocket?.networkInterface = ni
                        udpSocket?.send(mcastPacket)
                    }
                }
            }
            runCatching { DatagramSocket().use { it.send(ucastPacket) } }
        }
    }

    override fun stop() {
        udpJob?.cancel()
        runCatching { multicastLock?.release() }
        runCatching { udpSocket?.close() }
    }
}

