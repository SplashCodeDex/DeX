package com.dexstudios.dex.server

import com.dexstudios.dex.server.auth.FixtureIdTokenVerifier
import com.dexstudios.dex.server.auth.GoogleIdTokenVerifierAdapter
import com.dexstudios.dex.server.auth.IdTokenVerifier
import com.dexstudios.dex.server.relay.relayRoutes
import com.dexstudios.dex.server.routes.punchRoutes
import com.dexstudios.dex.server.routes.syncRoutes
import com.dexstudios.dex.server.sync.SyncHostStore
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * The DeX cloud peer (plan 032): streaming E2EE relay + NAT-punch rendezvous + sync host
 * on the self-hosted VPS. Configuration is environment-only (never secrets in git):
 *
 *   DEX_PORT               — listen port            (default 8443)
 *   DEX_GOOGLE_CLIENT_ID   — OAuth audience for ID token verification (REQUIRED in prod)
 *   DEX_FIXTURE_AUTH       — "1" enables the fixture verifier; refused unless a dev flag
 *                           is also set. Tests inject their own verifier directly.
 */
fun main() {
    val port = System.getenv("DEX_PORT")?.toIntOrNull() ?: 8443
    val clientId = System.getenv("DEX_GOOGLE_CLIENT_ID").orEmpty()

    val verifier: IdTokenVerifier = when {
        clientId.isNotBlank() -> GoogleIdTokenVerifierAdapter(clientId)

        System.getenv("DEX_FIXTURE_AUTH") == "1" && System.getenv("DEX_DEV") == "1" -> FixtureIdTokenVerifier(emptyMap())

        else -> {
            // Refuse to boot without real verification — a silent open relay would be the
            // worst possible failure mode (never-fake-success applies to ops too).
            System.err.println("FATAL: DEX_GOOGLE_CLIENT_ID is required (or DEX_FIXTURE_AUTH=1 + DEX_DEV=1 for local runs)")
            kotlin.system.exitProcess(2)
        }
    }

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        routing {
            // Unauthenticated liveness probe for Docker/uptime monitors: answers OK
            // whenever the process is serving. Every OTHER route requires auth.
            get("/healthz") {
                call.respondText("ok", io.ktor.http.ContentType.Text.Plain)
            }
            syncRoutes(verifier, SyncHostStore())
            punchRoutes(verifier)
            relayRoutes(verifier)
        }
    }.start(wait = true)
}
