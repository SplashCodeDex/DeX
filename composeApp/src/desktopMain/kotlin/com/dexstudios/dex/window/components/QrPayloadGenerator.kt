package com.dexstudios.dex.window.components

import com.dexstudios.dex.core.network.DeXPorts
import java.net.Inet4Address
import java.net.NetworkInterface

object QrPayloadGenerator {
    fun generateLocalPayload(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val ips = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }

            if (ips.isEmpty()) {
                return "http://127.0.0.1:${DeXPorts.HTTPS}"
            }

            val mainIp = ips.first()
            val extraIps = if (ips.size > 1) ips.drop(1).joinToString(",") else ""

            val payload = StringBuilder("http://$mainIp:${DeXPorts.HTTPS}")
            if (extraIps.isNotEmpty()) {
                payload.append("?ips=$extraIps")
            }
            payload.toString()
        } catch (e: Exception) {
            "http://127.0.0.1:${DeXPorts.HTTPS}"
        }
    }
}
