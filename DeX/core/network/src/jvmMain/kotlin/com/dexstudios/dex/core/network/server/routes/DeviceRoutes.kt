package com.dexstudios.dex.core.network.server.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.RegisterDto
import io.ktor.http.HttpStatusCode

fun Route.deviceRoutes(discoveryEngine: DiscoveryEngine?) {
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

    route("/local") {
        get("/devices") {
            if (discoveryEngine != null) {
                val devices = discoveryEngine.devices.value.values.map { it.info }
                call.respond(devices)
            } else {
                call.respond(emptyList<String>())
            }
        }

        post("/devices/flush") {
            // TODO: Clear discovered devices
            call.respond(HttpStatusCode.OK)
        }

        get("/devices/ping") {
            val ip = call.request.queryParameters["ip"]
            if (ip.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            // TODO: Ping the IP
            call.respond(HttpStatusCode.NotFound)
        }

        get("/token") {
            val ip = call.request.queryParameters["ip"]
            if (ip.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            // TODO: Lookup token by IP via AuthState
            call.respond(HttpStatusCode.NotFound)
        }

        post("/unpair") {
            val fp = call.request.queryParameters["fingerprint"]
            if (!fp.isNullOrEmpty()) {
                // TODO: Remove paired device via DeviceManager
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        get("/pair-status") {
            call.respond(HttpStatusCode.NotFound)
        }

        get("/pending-pair") {
            call.respond(HttpStatusCode.NotFound)
        }

        get("/cert") {
            call.respond(HttpStatusCode.NotFound)
        }

        post("/pair-initiate") {
            call.respond(HttpStatusCode.BadRequest)
        }

        post("/pair-cancel") {
            call.respond(HttpStatusCode.OK)
        }
    }
}
