package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.ClipboardHook
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun Route.clipboardRoutes() {
    route("/api/dex") {
        post("/clipboard") {
            try {
                val text = call.receiveText()
                if (text.isNotBlank()) {
                    val selection = StringSelection(text)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                    ClipboardHook.onRemoteTextReceived?.invoke(text)
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
