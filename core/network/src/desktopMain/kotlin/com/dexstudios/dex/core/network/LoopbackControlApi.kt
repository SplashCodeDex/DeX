package com.dexstudios.dex.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Single client for the desktop UI to reach its OWN process's loopback control plane
 * (DeXServer's 127.0.0.1:[DeXPorts.LOOPBACK_CONTROL] listener, served by SettingsRoutes).
 *
 * Centralizes what used to be an ad-hoc `HttpClient(CIO)` constructed inside a Compose
 * click handler per sign-in click: one lazily-created engine for the whole app lifetime,
 * URLs derived from [DeXPorts], short timeouts because the peer is in-process.
 * The sign-in trigger responds immediately (the browser round-trip completes the flow
 * server-side), so no long timeout is needed for it.
 */
object LoopbackControlApi {
    private const val BASE_URL = "http://127.0.0.1:${DeXPorts.LOOPBACK_CONTROL}"

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                // In-process server; anything beyond this means the listener never came up.
                requestTimeoutMillis = 5_000
            }
        }
    }

    /** Opens the Google sign-in flow server-side and blocks until it resolves/fails. */
    suspend fun triggerGoogleSignIn() {
        client.get("$BASE_URL/local/settings/google-signin")
    }

    suspend fun setEmail(email: String) {
        client.post("$BASE_URL/local/settings/email") { setBody(email) }
    }

    suspend fun signOut() {
        client.post("$BASE_URL/local/settings/signout")
    }
}
