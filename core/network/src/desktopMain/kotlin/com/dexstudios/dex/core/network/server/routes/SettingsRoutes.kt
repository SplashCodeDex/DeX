package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.auth.GoogleOAuth
import io.ktor.server.application.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(val email: String, val name: String, val picture: String)

/**
 * Google sign-in browser redirect target. Served ONLY by the dedicated loopback 48425
 * listener (DeXServer) — the URI is registered in the Google Cloud Console client, so it
 * must keep serving exactly this path and port. Deliberately NOT registered on the
 * LAN-facing listeners: a forged callback there would let a peer feed arbitrary
 * code/state into [GoogleOAuth.handleCallback].
 */
fun Route.oauthCallbackRoutes() {
    get("/local/oauth/callback") {
        val code = call.request.queryParameters["code"]
        val state = call.request.queryParameters["state"]
        val error = call.request.queryParameters["error"]

        if (!error.isNullOrEmpty()) {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed &mdash; Google reported an error.</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
            return@get
        }
        if (state.isNullOrEmpty() || code.isNullOrEmpty()) {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed &mdash; missing parameters.</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
            return@get
        }

        val result = GoogleOAuth.handleCallback(state, code)
        if (result.first) {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>DeX signed in &mdash; you can close this tab.</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
        } else {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed &mdash; ${result.second}</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
        }
    }
}

/**
 * Account/control settings routes. LOOPBACK-ONLY by contract: DeXServer registers these
 * exclusively on the 127.0.0.1:[DeXPorts.LOOPBACK_CONTROL] listener. They mutate
 * security-relevant state (identity email drives the auto-trust identity hash) and must
 * never be reachable from the LAN-facing HTTPS listener.
 */
fun Route.settingsRoutes() {
    post("/local/settings/email") {
        val email = call.receiveText()
        val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
        deviceConfig.email = email
        call.respond(io.ktor.http.HttpStatusCode.OK)
    }

    get("/local/settings/google-signin") {
        if (!GoogleOAuth.isConfigured()) {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Google Sign-In is not configured (oauth.local.json missing).</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
            return@get
        }
        val profile = GoogleOAuth.signInAsync()
        if (profile != null) {
            val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
            deviceConfig.email = profile.email
            val name = if (profile.name.isEmpty()) profile.email else profile.name
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Signed in as $name &mdash; DeX devices with this email are now auto-trusted.</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
        } else {
            call.respondText(
                "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Sign-in failed or was cancelled.</h3></body></html>",
                io.ktor.http.ContentType.Text.Html,
            )
        }
    }

    post("/local/settings/signout") {
        val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
        deviceConfig.signOut()
        GoogleOAuth.signOut()
        call.respond(io.ktor.http.HttpStatusCode.OK)
    }

    get("/local/settings/google-profile") {
        val profile = GoogleOAuth.loadProfile()
        if (profile == null) {
            call.respond(ProfileDto("", "", ""))
        } else {
            call.respond(ProfileDto(profile.email, profile.name, profile.picture))
        }
    }
}
