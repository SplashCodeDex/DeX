package com.dexstudios.dex.network

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

class UdpMulticastManager(
    private val context: Context,
    private val localInfo: RegisterDto,
    private val onDeviceDiscovered: (DiscoveredDevice) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var udpJob: Job? = null
    private var udpSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        udpJob = scope.launch {
            runCatching {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("dex_multicast_lock").apply {
                    setReferenceCounted(true)
                    acquire()
                }

                var canReceive = true
                udpSocket = runCatching {
                    MulticastSocket(DeXPorts.HTTPS)
                }.getOrElse {
                    Timber.w(it, "Failed to bind main discovery port; falling back to ephemeral sending port")
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
            }.onFailure { Timber.e(it, "Multicast loop failed") }
        }
    }

    private fun handleIncomingPacket(packet: DatagramPacket) {
        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
        runCatching {
            val json = JSONObject(msg)
            val fp = json.optString("fingerprint", "")
            if (fp.isEmpty() || fp == localInfo.fingerprint) return

            onDeviceDiscovered(
                DiscoveredDevice(
                    ip = packet.address.hostAddress ?: return,
                    info = RegisterDto(
                        alias = json.optString("alias", "Unknown"),
                        version = json.optString("version", "2.0"),
                        deviceModel = json.optString("deviceModel", "Unknown"),
                        deviceType = json.optString("deviceType", "unknown"),
                        fingerprint = fp,
                        port = json.optInt("port", DeXPorts.HTTPS),
                        quicPort = json.optInt("quicPort", DeXPorts.QUIC),
                        tcpFallbackPort = json.optInt("tcpFallbackPort", DeXPorts.PULL),
                        protocol = json.optString("protocol", "https"),
                        download = json.optBoolean("download", true),
                        identityHash = if (json.has("identityHash")) json.optString("identityHash") else null,
                        googleSub = if (json.has("googleSub")) json.optString("googleSub") else null
                    )
                )
            )
            sendReply(packet)
        }
    }

    private fun getReplyData(): ByteArray {
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
        return replyJson.toString().toByteArray(Charsets.UTF_8)
    }

    private fun broadcastPresence() {
        runCatching {
            val replyData = getReplyData()
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
            val replyData = getReplyData()
            val ucastPacket = DatagramPacket(replyData, replyData.size, packet.address, packet.port)
            DatagramSocket().use { it.send(ucastPacket) }
        }
    }

    fun stop() {
        udpJob?.cancel()
        runCatching { multicastLock?.release() }
        runCatching { udpSocket?.close() }
    }
}
