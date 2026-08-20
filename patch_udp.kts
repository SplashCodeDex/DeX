import java.io.File

val file = File("W:/CodeDeX/DeX/core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/DesktopUdpService.kt")
var content = file.readText()

val broadcastLoop = """
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

                        // 2. Send Directed Broadcasts (Subnet broadcasts)
                        runCatching {
                            DatagramSocket().use { bcastSocket ->
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
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }
"""

if (!content.contains("startBroadcasting()")) {
    content = content.replace("private var legacyJob: Job? = null", "private var legacyJob: Job? = null\n    private var broadcastJob: Job? = null")
    content = content.replace("legacyJob = scope.launch { startListening(28424, 1) }", "legacyJob = scope.launch { startListening(28424, 1) }\n        startBroadcasting()")
    content = content.replace("override fun stop() {", broadcastLoop.trim() + "\n\n    override fun stop() {")
    content = content.replace("legacyJob?.cancel()", "legacyJob?.cancel()\n        broadcastJob?.cancel()")
    file.writeText(content)
    println("Patched!")
}
