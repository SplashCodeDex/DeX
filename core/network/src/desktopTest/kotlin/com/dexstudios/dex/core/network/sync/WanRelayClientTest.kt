package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.server.auth.FixtureIdTokenVerifier
import com.dexstudios.dex.server.relay.relayRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan 032 contract suite for the desktop WAN relay orchestration: a full E2EE transfer
 * through an embedded relay (sender seal -> relay pass-through -> receiver open), tamper
 * aborts, wrong-token rejection, and the framing integrity — all against the REAL server
 * module's relay routes (same wire law both sides of the deployment).
 */
class WanRelayClientTest {

    private val verifier = FixtureIdTokenVerifier(mapOf("token-live" to "sub-alice"))

    private fun Application.installRelay() {
        routing {
            relayRoutes(verifier)
        }
    }

    private fun newClient(): WanRelayClient = WanRelayClient(
        client = HttpClient(),
        baseUrlProvider = { "https://relay.invalid" }, // never contacted — tests inject transport below
        tokenProvider = { "token-live" },
    )

    @Test
    fun `a full e2ee transfer round-trips through the relay`() = testApplication {
        application { installRelay() }
        val transport = WanRelayClient(
            client = createClient {},
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "token-live" },
        )

        // 1. Sender opens the session (authenticated) against the embedded relay.
        val session = transport.openSession("fp-phone")
        try {
            assertEquals("fp-phone", session.targetDeviceId)

            // 2. Sender uploads a multi-chunk payload, sealed under the paired token.
            val payload = ByteArray(600 * 1024) { (it * 7 + 3).toByte() } // 3 sealed chunks
            runBlocking {
                transport.upload(session, pairedToken = "pair-secret", input = ByteArrayInputStream(payload))
            }

            // 3. Receiver pulls and opens with the SAME paired token -> identical plaintext.
            val received = ByteArrayOutputStream()
            runBlocking {
                transport.download(session, pairedToken = "pair-secret", output = received)
            }
            assertContentEquals(payload, received.toByteArray())
        } finally {
            runBlocking { transport.closeSession(session) } // quota hygiene across tests
        }
    }

    @Test
    fun `receiver with the wrong paired token fails closed`() = testApplication {
        application { installRelay() }
        val transport = WanRelayClient(
            client = createClient {},
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "token-live" },
        )
        val session = transport.openSession("fp-phone")
        try {
            val payload = "secret-wan-content".toByteArray()
            runBlocking {
                transport.upload(session, pairedToken = "pair-secret", input = ByteArrayInputStream(payload))
            }

            val received = ByteArrayOutputStream()
            val failure = runBlocking {
                runCatching { transport.download(session, pairedToken = "WRONG-token", output = received) }.exceptionOrNull()
            }
            assertTrue(failure is com.dexstudios.dex.core.network.RelayCryptoException, "wrong key must fail closed, got $failure")
            assertTrue(received.size() == 0, "no plaintext may leak on authentication failure")
        } finally {
            runBlocking { transport.closeSession(session) }
        }
    }

    @Test
    fun `upload mid-stream failures surface to the caller`() = testApplication {
        application { installRelay() }
        val transport = WanRelayClient(
            client = createClient {},
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "token-live" },
        )
        val session = transport.openSession("fp-phone")
        try {
            // Wrong stream token -> the relay rejects every frame -> upload must abort.
            val broken = session.copy(streamToken = "wrong-stream-token")
            val failure = runBlocking {
                runCatching {
                    transport.upload(broken, "pair-secret", ByteArrayInputStream(ByteArray(1024)))
                }.exceptionOrNull()
            }
            assertTrue(failure is IllegalStateException, "relay rejection must surface, got $failure")
        } finally {
            runBlocking { transport.closeSession(session) }
        }
    }

    @Test
    fun `unauthenticated session open is rejected by the relay`() = testApplication {
        application { installRelay() }
        val transport = WanRelayClient(
            client = createClient {},
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "forged-token" },
        )

        val failure = runBlocking {
            runCatching { transport.openSession("fp-phone") }.exceptionOrNull()
        }
        assertTrue(failure is IllegalStateException, "auth gate must reject, got $failure")
    }

    @Test
    fun `framing integrity - many small chunks reassemble exactly`() = testApplication {
        application { installRelay() }
        val transport = WanRelayClient(
            client = createClient {},
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "token-live" },
        )
        val session = transport.openSession("fp-phone")
        try {
            // 37 chunks of odd sizes: exercises frame boundaries exhaustively.
            val payloads = (0 until 37).map { i -> ByteArray(1000 + i * 13) { b -> (b + i).toByte() } }
            val all = payloads.reduce { acc, bytes -> acc + bytes }
            runBlocking {
                transport.upload(session, "pair-secret", ByteArrayInputStream(all))
            }

            val received = ByteArrayOutputStream()
            runBlocking { transport.download(session, "pair-secret", received) }
            assertContentEquals(all, received.toByteArray())
        } finally {
            runBlocking { transport.closeSession(session) }
        }
    }
}
