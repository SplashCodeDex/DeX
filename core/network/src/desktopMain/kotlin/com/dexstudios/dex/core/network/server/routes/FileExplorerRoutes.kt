package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.server.DexRequestStore
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.network.server.guardLoopback
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * File-explorer over WS proxy: the desktop UI drives a connected phone's SAF surfaces.
 * LOOPBACK-ONLY by the `/local/` contract (plan 021) — every handler gates on
 * [guardLoopback] because these routes are also mounted on the LAN-facing TLS listener
 * via DeXServer's baseModule, and an unguarded mount would let any LAN peer drive
 * list/browse/grant requests into a phone's session (grant pops a user-facing dialog
 * that hangs up to 190 s).
 */
fun Route.fileExplorerRoutes() {
    route("/local/dex") {
        post("/list-folders") {
            if (!guardLoopback()) return@post
            val fp = call.request.queryParameters["fingerprint"]
            if (fp == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val requestId = UUID.randomUUID().toString()
            val requestJson = ProtocolEnvelope.envelopeOf(MessageTypes.LIST_SHARED_FOLDERS) {
                put(FieldNames.REQUEST_ID, requestId)
            }

            val deferred = DexRequestStore.createRequest(requestId)
            val sent = WebSocketConnectionManager.sendRequest(fp, requestJson)
            if (!sent) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val reply = withTimeoutOrNull(25000) { deferred.await() }
            if (reply == null) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(reply)
            }
        }

        post("/browse") {
            if (!guardLoopback()) return@post
            val fp = call.request.queryParameters["fingerprint"]
            val folderUri = call.request.queryParameters["folderUri"]
            if (fp == null || folderUri == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val requestId = UUID.randomUUID().toString()
            val requestJson = ProtocolEnvelope.envelopeOf(MessageTypes.BROWSE_FOLDER) {
                put(FieldNames.REQUEST_ID, requestId)
                put("folderUri", folderUri)
            }

            val deferred = DexRequestStore.createRequest(requestId)
            val sent = WebSocketConnectionManager.sendRequest(fp, requestJson)
            if (!sent) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val reply = withTimeoutOrNull(25000) { deferred.await() }
            if (reply == null) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(reply)
            }
        }

        post("/grant-folder") {
            if (!guardLoopback()) return@post
            val fp = call.request.queryParameters["fingerprint"]
            if (fp == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val requestId = UUID.randomUUID().toString()
            val requestJson = ProtocolEnvelope.envelopeOf(MessageTypes.GRANT_SHARED_FOLDER) {
                put(FieldNames.REQUEST_ID, requestId)
            }

            val deferred = DexRequestStore.createRequest(requestId)
            val sent = WebSocketConnectionManager.sendRequest(fp, requestJson)
            if (!sent) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            // Granting folder might take long (requires user interaction on phone)
            val reply = withTimeoutOrNull(190000) { deferred.await() }
            if (reply == null) {
                DexRequestStore.cancelRequest(requestId)
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(reply)
            }
        }
    }
}
