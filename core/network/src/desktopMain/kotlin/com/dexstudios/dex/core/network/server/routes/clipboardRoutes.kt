package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.ClipboardSyncState
import com.dexstudios.dex.core.network.server.BearerTrust
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

const val MAX_CLIPBOARD_TEXT_BYTES = 1024 * 1024 // 1MB upper bound

fun Route.clipboardRoutes() {
    route("/api/dex") {
        post("/clipboard") {
            // The sender authenticates with the same bearer tiers as the /ws handshake
            // (googleSub / identityHash / any paired token — see BearerTrust). This gate
            // used to be missing entirely, letting any LAN peer inject clipboard content.
            val bearer = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
            if (!BearerTrust.isTrustedBearer(bearer)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val contentLength = call.request.contentLength()
            if (contentLength != null && contentLength > MAX_CLIPBOARD_TEXT_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge)
                return@post
            }
            try {
                val text = call.receiveText()
                if (text.length > MAX_CLIPBOARD_TEXT_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                if (text.isNotBlank()) {
                    // Route through the shared domain use case (plan 029): the
                    // write AND the echo-guard marking must both happen there,
                    // or the AWT change listener bounces this text right back.
                    if (ClipboardSyncState.useCase != null) {
                        ClipboardSyncState.applyRemoteText(text)
                    } else {
                        // Fallback when useCase is not initialized (e.g. headless or tests)
                        try {
                            val selection = StringSelection(text)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        } catch (_: Exception) {}
                    }
                    ClipboardSyncState.emitReceived(text)
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
