package com.dexstudios.dex.core.network.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.controlRoutes() {
    route("/api/localsend/v2") {

        post("/cancel") {
            val sessionId = call.request.queryParameters["sessionId"]
            if (sessionId != null) {
                activeUploadSessions.remove(sessionId)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

    }
}
