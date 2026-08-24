package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
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

/** Latest registered punch endpoint for a fingerprint, or null when stale/absent (5-min TTL). */
fun getPunchEndpoint(fingerprint: String): PunchEntry? {
    val entry = punchEndpoints[fingerprint] ?: return null
    if (System.currentTimeMillis() - entry.ts > 5 * 60 * 1000L) {
        punchEndpoints.remove(fingerprint)
        return null
    }
    return entry
}

fun Route.deviceRoutes(discoveryEngine: DiscoveryEngine?) {
    get("/punch/endpoint") {
        val fingerprint = call.request.queryParameters["fingerprint"]
        val remoteIp = call.request.origin.remoteHost
        val remotePort = call.request.origin.remotePort

        if (!fingerprint.isNullOrEmpty() && remoteIp.isNotEmpty() && remotePort > 0) {
            // Registration must come from the fingerprint's own PROVEN session, otherwise any
            // LAN peer could poison the rendezvous table and redirect NAT-punch transfers.
            if (!WebSocketConnectionManager.isTrusted(fingerprint)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

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
