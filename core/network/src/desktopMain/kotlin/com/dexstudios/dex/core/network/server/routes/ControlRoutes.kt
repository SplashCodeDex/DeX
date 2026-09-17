package com.dexstudios.dex.core.network.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

fun Route.controlRoutes() {
    route("/api/localsend/v2") {
        post("/cancel") {
            val sessionId = call.request.queryParameters["sessionId"]
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val session = activeUploadSessions[sessionId]
            if (session != null) {
                // Only the sender that prepared this session (same bearer identity) may cancel it
                val presented = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
                val owner = session.ownerToken
                val authorized = when {
                    owner == null -> true

                    // legacy/hand-built session without recorded ownership
                    presented.isNullOrEmpty() -> false

                    else -> MessageDigest.isEqual(presented.toByteArray(), owner.toByteArray())
                }
                if (!authorized) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
            }

            // Cancel the LIVE handler FIRST. Removing only the bookkeeping left the in-flight
            // write running to completion, so a cancelled transfer could still commit its file
            // and republish itself as successful. The handler also re-checks cancellation
            // before committing, which closes the race where the cancel lands mid-stream.
            activeUploadJobs.remove(sessionId)?.cancel(CancellationException("Incoming transfer cancelled by sender"))
            activeUploadSessions.remove(sessionId)
            activeUploadSessionsProgress.remove(sessionId)
            com.dexstudios.dex.core.network.TransferStateMonitor.removeSession(sessionId)
            call.respond(HttpStatusCode.OK)
        }
    }
}
