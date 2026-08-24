package com.dexstudios.dex.core.network.services

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DeXPorts
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket
import java.net.URI

class DesktopUpnpService(private val httpClient: HttpClient, private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp = _publicIp.asStateFlow()

    private val SSDP_MULTICAST = "239.255.255.250"
    private val SSDP_PORT = 1900
    private val IGD_SERVICES = listOf(
        "urn:schemas-upnp-org:service:WANIPConnection:2",
        "urn:schemas-upnp-org:service:WANIPConnection:1",
        "urn:schemas-upnp-org:service:WANPPPConnection:1",
    )

    private data class IgdInfo(val controlUrl: String, val serviceType: String, val routerIp: String)

    /** Gateway seen at the last successful [configureAsync]; drives shutdown-time cleanup. */
    @Volatile
    private var lastKnownIgd: IgdInfo? = null

    /**
     * WAN reachability is a core product behavior and is always on: maps the router ports
     * so same-account phones can reach this PC from cellular networks.
     */
    fun configureAsync() {
        scope.launch { configureMappings() }
    }

    private suspend fun configureMappings() {
        try {
            val igd = discoverIgdAsync() ?: run {
                Logger.i("[UPNP] No UPnP Internet Gateway Device found.")
                return
            }
            lastKnownIgd = igd

            // Delete stale mappings first (also sweeps leaks from older sessions)
            deletePortMapping(igd, DeXPorts.HTTPS, "TCP")
            deletePortMapping(igd, DeXPorts.QUIC, "UDP")
            deletePortMapping(igd, DeXPorts.OAUTH_CALLBACK, "TCP")

            // Add active mappings
            addPortMapping(igd, DeXPorts.HTTPS, "TCP")
            addPortMapping(igd, DeXPorts.QUIC, "UDP")
            addPortMapping(igd, DeXPorts.OAUTH_CALLBACK, "TCP")

            val externalIp = getExternalIp(igd)
            if (externalIp != null) {
                Logger.i("[UPNP] Public IP: $externalIp")
                _publicIp.value = externalIp
            }
        } catch (e: Exception) {
            Logger.i("[UPNP] Configure failed: ${e.message}")
        }
    }

    /**
     * Best-effort removal of the mappings added by [configureAsync] so exiting the app does
     * not leave ports 48424/TCP, 48423/UDP and 48425/TCP open on the router forever.
     *
     * Uses the IGD cached at configuration time; silently skips when no gateway was seen
     * this session (leaked mappings from older sessions are swept by the next
     * configureAsync's delete-before-add). Bounded-blocking — safe for the shutdown
     * coordinator, which runs it on a side thread with a join deadline.
     */
    fun releaseMappedPorts(timeoutMillis: Long = 1_500L) {
        val igd = lastKnownIgd ?: return
        runBlocking {
            withTimeoutOrNull(timeoutMillis) {
                deletePortMapping(igd, DeXPorts.HTTPS, "TCP")
                deletePortMapping(igd, DeXPorts.QUIC, "UDP")
                deletePortMapping(igd, DeXPorts.OAUTH_CALLBACK, "TCP")
            }
        }
        lastKnownIgd = null
    }

    private suspend fun discoverIgdAsync(): IgdInfo? = withContext(Dispatchers.IO) {
        val socket = MulticastSocket(null)
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(0))
        val groupAddr = InetSocketAddress(InetAddress.getByName(SSDP_MULTICAST), SSDP_PORT)
        java.net.NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
            runCatching {
                if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                    socket.joinGroup(groupAddr, ni)
                }
            }
        }
        socket.soTimeout = 3000

        val search = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 2
            ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1


        """.trimIndent().replace("\n", "\r\n")

        val sendData = search.toByteArray()
        val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName(SSDP_MULTICAST), SSDP_PORT)

        try {
            socket.send(packet)
        } catch (e: Exception) {
            return@withContext null
        }

        val buffer = ByteArray(16384)
        val receivePacket = DatagramPacket(buffer, buffer.size)
        val deadline = System.currentTimeMillis() + 4000

        while (System.currentTimeMillis() < deadline) {
            try {
                socket.receive(receivePacket)
                val text = String(receivePacket.data, 0, receivePacket.length)
                val location = text.lines()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                    ?.substring("LOCATION:".length)?.trim()

                if (!location.isNullOrEmpty()) {
                    val routerIp = receivePacket.address.hostAddress
                    val control = findIgdControlAsync(location)
                    if (control != null) {
                        socket.close()
                        return@withContext control.copy(routerIp = routerIp)
                    }
                }
            } catch (e: Exception) {
                // Timeout or error
                break
            }
        }
        socket.close()
        null
    }

    private suspend fun findIgdControlAsync(deviceDescriptionUrl: String): IgdInfo? {
        try {
            val response = httpClient.get(deviceDescriptionUrl).bodyAsText()

            // Fast basic XML parsing via Regex to avoid heavy XML libs for this simple task
            for (service in IGD_SERVICES) {
                if (response.contains(service)) {
                    val controlUrlRegex = "<controlURL>(.*?)</controlURL>".toRegex(RegexOption.IGNORE_CASE)
                    val match = controlUrlRegex.find(response)
                    if (match != null) {
                        val controlPath = match.groupValues[1]
                        return IgdInfo(resolveUrl(deviceDescriptionUrl, controlPath), service, "")
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private suspend fun addPortMapping(igd: IgdInfo, port: Int, protocol: String) {
        val localIp = getLocalIpForRoute(igd.routerIp) ?: return

        val body = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
            <u:AddPortMapping xmlns:u="${igd.serviceType}">
            <NewRemoteHost></NewRemoteHost>
            <NewExternalPort>$port</NewExternalPort>
            <NewProtocol>$protocol</NewProtocol>
            <NewInternalPort>$port</NewInternalPort>
            <NewInternalClient>$localIp</NewInternalClient>
            <NewEnabled>1</NewEnabled>
            <NewPortMappingDescription>DeX $protocol $port</NewPortMappingDescription>
            <NewLeaseDuration>0</NewLeaseDuration>
            </u:AddPortMapping>
            </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val response = httpClient.post(igd.controlUrl) {
                header("SOAPACTION", "\"${igd.serviceType}#AddPortMapping\"")
                setBody(body)
            }
            Logger.i("[UPNP] Port mapping $protocol $port -> $localIp: ${if (response.status.isSuccess()) "OK" else "HTTP ${response.status.value}"}")
        } catch (e: Exception) {
            Logger.i("[UPNP] Port mapping $protocol $port failed: ${e.message}")
        }
    }

    private suspend fun deletePortMapping(igd: IgdInfo, port: Int, protocol: String) {
        val body = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>
            <u:DeletePortMapping xmlns:u="${igd.serviceType}">
            <NewRemoteHost></NewRemoteHost>
            <NewExternalPort>$port</NewExternalPort>
            <NewProtocol>$protocol</NewProtocol>
            </u:DeletePortMapping>
            </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            httpClient.post(igd.controlUrl) {
                header("SOAPACTION", "\"${igd.serviceType}#DeletePortMapping\"")
                setBody(body)
            }
        } catch (e: Exception) { }
    }

    private suspend fun getExternalIp(igd: IgdInfo): String? {
        val body = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body><u:GetExternalIPAddress xmlns:u="${igd.serviceType}"></u:GetExternalIPAddress></s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val response = httpClient.post(igd.controlUrl) {
                header("SOAPACTION", "\"${igd.serviceType}#GetExternalIPAddress\"")
                setBody(body)
            }
            if (!response.status.isSuccess()) return null
            val xml = response.bodyAsText()
            val regex = "<NewExternalIPAddress>(.*?)</NewExternalIPAddress>".toRegex(RegexOption.IGNORE_CASE)
            val ip = regex.find(xml)?.groupValues?.get(1)?.trim()
            return if (ip.isNullOrEmpty() || ip == "0.0.0.0") null else ip
        } catch (e: Exception) {
            return null
        }
    }

    private fun getLocalIpForRoute(routerIp: String): String? = try {
        val probe = Socket()
        probe.connect(InetSocketAddress(routerIp, SSDP_PORT), 1000)
        val addr = probe.localAddress.hostAddress
        probe.close()
        addr
    } catch (e: Exception) {
        null
    }

    private fun resolveUrl(baseUrl: String, controlPath: String): String = try {
        val baseUri = URI(baseUrl)
        baseUri.resolve(controlPath).toString()
    } catch (e: Exception) {
        controlPath
    }
}
