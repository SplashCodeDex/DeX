package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.PairingEngine
import com.dexstudios.dex.auth.PairingState
import com.dexstudios.dex.core.network.DeviceManager
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.security.CertificateGenerator
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class PunchResponse(val ip: String, val port: Int)

data class PunchEntry(val ip: String, val port: Int, val ts: Long)
val punchEndpoints = ConcurrentHashMap<String, PunchEntry>()

fun Route.deviceRoutes(discoveryEngine: DiscoveryEngine?, pairingEngine: PairingEngine?) {
    get("/punch/endpoint") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        val remoteIp = call.request.origin.remoteHost
        val remotePort = call.request.origin.remotePort

        if (!fingerprint.isNullOrEmpty() && remoteIp.isNotEmpty() && remotePort > 0) {
            punchEndpoints[fingerprint] = PunchEntry(remoteIp, remotePort, System.currentTimeMillis())

            val cutoff = System.currentTimeMillis() - (5 * 60 * 1000L)
            val stale = punchEndpoints.filter { it.value.ts < cutoff }.keys
            for (k in stale) punchEndpoints.remove(k)

            call.respond(PunchResponse(remoteIp, remotePort))
        } else {
            call.respond(HttpStatusCode.BadRequest)
        }
    }
    route("/api/localsend/v2") {
        get("/info") {
            if (discoveryEngine != null) {
                call.respond(discoveryEngine.localInfo)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }

        post("/register") {
            if (discoveryEngine != null) {
                try {
                    val dto = call.receive<RegisterDto>()
                    val ip = call.request.local.remoteHost
                    discoveryEngine.addDevice(DiscoveredDevice(ip = ip, info = dto, viaWan = false, viaRoster = false))
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest)
                }
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }
    }
}
