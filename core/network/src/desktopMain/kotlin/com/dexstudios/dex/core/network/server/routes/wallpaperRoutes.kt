package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.server.BearerTrust
import com.dexstudios.dex.core.network.services.DesktopWallpaperService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.wallpaperRoutes() {
    val wallpaperHandler: suspend (ApplicationCall) -> Unit = { call ->
        val queryToken = call.request.queryParameters["token"]
        val queryFp = call.request.queryParameters["fingerprint"]
        val bearer = call.request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()

        val token = bearer?.ifBlank { null } ?: queryToken?.ifBlank { null }
        val isTrusted = BearerTrust.isTrustedBearer(token) ||
            (!queryFp.isNullOrBlank() && AuthState.pairedFingerprints.value.contains(queryFp)) ||
            (!queryFp.isNullOrBlank() && !token.isNullOrBlank() && AuthState.pairedTokens.value[queryFp] == token)

        if (!isTrusted) {
            call.respond(HttpStatusCode.Unauthorized)
        } else {
            val wallpaper = DesktopWallpaperService.getWallpaper480p()
            if (wallpaper == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.response.headers.append(HttpHeaders.ETag, wallpaper.etag)
                call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=300")

                val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)
                if (!ifNoneMatch.isNullOrBlank() && ifNoneMatch == wallpaper.etag) {
                    call.respond(HttpStatusCode.NotModified)
                } else {
                    call.respondBytes(
                        bytes = wallpaper.bytes,
                        contentType = ContentType.parse(wallpaper.contentType),
                        status = HttpStatusCode.OK,
                    )
                }
            }
        }
    }

    route("/api/dex") {
        get("/wallpaper") { wallpaperHandler(call) }
    }
    route("/api/localsend/v2") {
        get("/wallpaper") { wallpaperHandler(call) }
    }
}
