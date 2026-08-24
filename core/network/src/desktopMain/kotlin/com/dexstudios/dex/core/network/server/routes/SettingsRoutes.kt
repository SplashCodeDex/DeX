package com.dexstudios.dex.core.network.server.routes

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.auth.GoogleOAuth
import io.ktor.server.application.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(val email: String, val name: String, val picture: String)

/**
 * Owns the fire-and-forget Google sign-in flow. The HTTP trigger responds immediately;
 * this scope keeps awaiting the browser round-trip and persists the verified profile
 * afterwards (email drives the auto-trust identity hash; name/picture/sub feed the
 * Settings header and identity-proof verification).
 */
private val googleSignInScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
fun Route.settingsRoutes(deviceConfig: DeviceConfig) {
    post("/local/settings/email") {
        val email = call.receiveText()
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
        // Fire-and-forget: respond immediately so the UI trigger cannot time out waiting
        // for the human browser round-trip. The /local/oauth/callback redirect completes
        // the deferred; this coroutine persists the verified profile when it lands.
        googleSignInScope.launch {
            runCatching {
                val profile = GoogleOAuth.signInAsync() ?: return@launch
                deviceConfig.email = profile.email
                deviceConfig.setGoogleProfile(profile.name, profile.picture)
                deviceConfig.setGoogleSub(profile.sub)
                Logger.i("Google sign-in completed for ${profile.email}")
            }.onFailure { Logger.e("Google sign-in failed: ${it.message}") }
        }
        call.respondText(
            "<html><body style=\"font-family:sans-serif;text-align:center;margin-top:3em\"><h3>Continue in your browser&hellip;</h3><p>Complete the Google sign-in in the window that just opened.</p></body></html>",
            io.ktor.http.ContentType.Text.Html,
        )
    }

    post("/local/settings/signout") {
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
