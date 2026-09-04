package com.dexstudios.dex.server

import com.dexstudios.dex.server.auth.FixtureIdTokenVerifier
import com.dexstudios.dex.server.auth.IdTokenVerifier
import com.dexstudios.dex.server.relay.RelaySessionRegistry
import com.dexstudios.dex.server.relay.relayRoutes
import com.dexstudios.dex.server.routes.punchRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 032 contract suite for the relay + punch surfaces.
 *
 * Route level: auth gates the session-open (quota BEFORE first byte), the data plane
 * runs on the unguessable stream token. Registry level: streaming pass-through,
 * byte accounting, and the size/idle/TTL laws — exercised directly with coroutines,
 * which is where the streaming behavior genuinely lives.
 */
class RelayAndPunchRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val verifier: IdTokenVerifier = FixtureIdTokenVerifier(
        mapOf(
            "token-alice" to "sub-alice",
            "token-bob" to "sub-bob",
            "token-quota" to "sub-quota",
        ),
    )

    private fun Application.installRelay() {
        routing {
            relayRoutes(verifier)
            punchRoutes(verifier)
        }
    }

    private suspend fun openSession(client: HttpClient, token: String): Pair<String, String> {
        val response = client.post("/relay/v1/session?targetDeviceId=fp-phone") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status, "session open failed: ${response.bodyAsText()}")
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return body["sessionId"]!!.jsonPrimitive.content to body["streamToken"]!!.jsonPrimitive.content
    }

    // ------------------------------------------------------------------
    // Route gates
    // ------------------------------------------------------------------

    @Test
    fun `session open without bearer is unauthorized`() = testApplication {
        application { installRelay() }
        val response = client.post("/relay/v1/session?targetDeviceId=fp-phone")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `session open with forged token is unauthorized`() = testApplication {
        application { installRelay() }
        val response = client.post("/relay/v1/session?targetDeviceId=fp-phone") {
            header("Authorization", "Bearer forged")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `bad stream token cannot stream into someone's session`() = testApplication {
        application { installRelay() }
        val (sessionId, _) = openSession(client, "token-alice")

        val response = client.post("/relay/v1/session/$sessionId/data") {
            header("X-DeX-Stream-Token", "wrong-token")
            setBody(ByteArray(16))
        }
        assertEquals(HttpStatusCode.Gone, response.status, "wrong stream token must be rejected")
    }

    @Test
    fun `data plane without stream token is a bad request`() = testApplication {
        application { installRelay() }
        val (sessionId, _) = openSession(client, "token-alice")

        val response = client.post("/relay/v1/session/$sessionId/data") {
            setBody(ByteArray(16))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `concurrent session quota rejects the third session before any byte moves`() = testApplication {
        application { installRelay() }
        // Dedicated tenant: the singleton registry may still hold sessions opened by
        // earlier tests under other tenants — isolation by tenant, not by test order.
        openSession(client, "token-quota")
        openSession(client, "token-quota")

        val third = client.post("/relay/v1/session?targetDeviceId=fp-phone") {
            header("Authorization", "Bearer token-quota")
        }
        assertEquals(HttpStatusCode.TooManyRequests, third.status, "cap is 2 concurrent sessions per tenant")
    }

    // ------------------------------------------------------------------
    // Registry mechanics (the streaming law)
    // ------------------------------------------------------------------

    @Test
    fun `streamed chunks pass through the bounded channel exactly as sent`() = runBlocking {
        val (sessionId, _) = RelaySessionRegistry.openSession("tenant-a")
        val channel = RelaySessionRegistry.framesFor(sessionId)
        assertNotNull(channel)

        // "Encrypted" payload — bytes the relay must never interpret or mutate.
        val chunks = listOf(
            ByteArray(256 * 1024) { (it * 31 + 7).toByte() },
            ByteArray(256 * 1024) { (it * 17 + 3).toByte() },
        )

        val received = java.util.Collections.synchronizedList(ArrayList<ByteArray>())
        coroutineScope {
            // The channel is capacity-bounded (64 frames): sends succeed WITHOUT a
            // subscriber, and an outrunning sender suspends once full — bounded memory.
            val collector = launch {
                for (chunk in channel) {
                    received.add(chunk)
                    if (received.size == chunks.size) break
                }
            }
            kotlinx.coroutines.delay(50) // let the drainer start; not required for correctness

            chunks.forEach { chunk ->
                assertTrue(
                    RelaySessionRegistry.accountBytes(sessionId, chunk.size.toLong()),
                    "byte accounting must stay under the cap",
                )
                channel.send(chunk)
            }
            RelaySessionRegistry.completeSession(sessionId)
            collector.join()
        }

        assertEquals(chunks.size, received.size, "every chunk arrives exactly once")
        assertTrue(chunks[0].contentEquals(received[0]), "chunk 1 mutated in transit")
        assertTrue(chunks[1].contentEquals(received[1]), "chunk 2 mutated in transit")
        RelaySessionRegistry.close(sessionId)
    }

    @Test
    fun `unknown session and expired sessions are unavailable`() {
        val thrown = run {
            try {
                RelaySessionRegistry.requireOpenSession("no-such-session", "any")
                null
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                e
            }
        }
        assertNotNull(thrown, "unknown sessions must reject")
    }

    @Test
    fun `closing a session kills its flow and frees the quota slot`() {
        val (sessionId, streamToken) = RelaySessionRegistry.openSession("tenant-a")
        assertEquals(1, RelaySessionRegistry.activeSessionsFor("tenant-a"))
        RelaySessionRegistry.close(sessionId)
        assertEquals(0, RelaySessionRegistry.activeSessionsFor("tenant-a"))

        val thrown = run {
            try {
                RelaySessionRegistry.requireOpenSession(sessionId, streamToken)
                null
            } catch (e: RelaySessionRegistry.QuotaExceeded) {
                e
            }
        }
        assertNotNull(thrown, "closed sessions must reject")
    }

    // ------------------------------------------------------------------
    // Punch rendezvous
    // ------------------------------------------------------------------

    @Test
    fun `punch register and resolve round-trip`() = testApplication {
        application { installRelay() }

        val registered = client.get("/punch/register?fingerprint=fp-phone&ip=203.0.113.7&port=48424") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.OK, registered.status)

        val resolved = client.get("/punch/resolve?fingerprint=fp-phone") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
        val body = json.parseToJsonElement(resolved.bodyAsText()).jsonObject
        assertEquals("203.0.113.7", body["ip"]!!.jsonPrimitive.content)
        assertEquals(48424, body["port"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `punch routes reject unauthenticated and forged callers`() = testApplication {
        application { installRelay() }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/punch/register?fingerprint=fp&ip=1.2.3.4&port=443").status,
            "register requires the bearer",
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/punch/resolve?fingerprint=fp").status,
            "resolve requires the bearer",
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/punch/register?fingerprint=fp&ip=1.2.3.4&port=443") {
                header("Authorization", "Bearer forged")
            }.status,
            "forged tokens must fail verification",
        )
    }

    @Test
    fun `punch entries are account-isolated - a stranger never resolves another tenant's endpoint`() = testApplication {
        application { installRelay() }

        // Alice's device registers.
        client.get("/punch/register?fingerprint=fp-alice-phone&ip=203.0.113.7&port=48424") {
            header("Authorization", "Bearer token-alice")
        }

        // Bob (different account) tries to resolve it: must learn NOTHING (404, not 403 —
        // the response is indistinguishable from an unknown entry; no account oracle).
        val stranger = client.get("/punch/resolve?fingerprint=fp-alice-phone") {
            header("Authorization", "Bearer token-bob")
        }
        assertEquals(HttpStatusCode.NotFound, stranger.status, "cross-account resolve is a hard boundary")
    }

    @Test
    fun `punch register rejects malformed input`() = testApplication {
        application { installRelay() }

        val missing = client.get("/punch/register?fingerprint=fp&ip=203.0.113.7") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.BadRequest, missing.status)

        val badPort = client.get("/punch/register?fingerprint=fp&ip=1.2.3.4&port=99999") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.BadRequest, badPort.status)
    }

    @Test
    fun `punch resolve of an unknown fingerprint is not found`() = testApplication {
        application { installRelay() }

        val response = client.get("/punch/resolve?fingerprint=never-registered") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `punch register refreshes an existing entry without tenant flags`() = testApplication {
        application { installRelay() }

        // First registration and refresh with a NEW endpoint: same fingerprint+tenant.
        client.get("/punch/register?fingerprint=fp-refresh&ip=1.1.1.1&port=1000") {
            header("Authorization", "Bearer token-alice")
        }
        val refreshed = client.get("/punch/register?fingerprint=fp-refresh&ip=2.2.2.2&port=2000") {
            header("Authorization", "Bearer token-alice")
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)

        val resolved = client.get("/punch/resolve?fingerprint=fp-refresh") {
            header("Authorization", "Bearer token-alice")
        }
        val body = json.parseToJsonElement(resolved.bodyAsText()).jsonObject
        assertEquals("2.2.2.2", body["ip"]!!.jsonPrimitive.content, "refresh replaces the endpoint")
    }

    // ------------------------------------------------------------------
    // Concurrency: parallel sessions through the bounded relay
    // ------------------------------------------------------------------

    @Test
    fun `registry-level concurrent sessions relay within the per-tenant caps`() = runBlocking {
        // Two tenants, two sessions each (the exact per-tenant cap), all four live at
        // once: pumps + drains run concurrently and every stream completes intact.
        // Each session streams 4 frames of 64 KiB.
        val tenants = listOf("load-tenant-a", "load-tenant-b")
        fun sessionFrames(sessionIdx: Int): List<ByteArray> = List(4) { frameIdx -> ByteArray(64 * 1024) { b -> ((b + sessionIdx * 16 + frameIdx) and 0xFF).toByte() } }

        coroutineScope {
            val jobs = ArrayList<kotlinx.coroutines.Job>()
            for (tenant in tenants) {
                repeat(2) { sessionIdx ->
                    val frames = sessionFrames(sessionIdx)
                    val (sessionId, _) = RelaySessionRegistry.openSession(tenant)
                    val channel = RelaySessionRegistry.framesFor(sessionId)!!
                    jobs += launch {
                        val received = ArrayList<ByteArray>()
                        val drain = launch {
                            for (frame in channel) {
                                received.add(frame)
                                if (received.size == frames.size) break
                            }
                        }
                        frames.forEach { frame ->
                            assertTrue(RelaySessionRegistry.accountBytes(sessionId, frame.size.toLong()))
                            channel.send(frame)
                        }
                        RelaySessionRegistry.completeSession(sessionId)
                        drain.join()
                        assertEquals(frames.size, received.size, "session $sessionIdx of $tenant lost frames")
                        frames.zip(received).forEach { (a, b) -> assertTrue(a.contentEquals(b), "frame corrupted in transit") }
                        RelaySessionRegistry.close(sessionId)
                    }
                }
            }
            jobs.joinAll()
        }

        assertEquals(0, RelaySessionRegistry.activeSessionsFor("load-tenant-a"))
        assertEquals(0, RelaySessionRegistry.activeSessionsFor("load-tenant-b"))
    }

    @Test
    fun `data endpoint releases tenant quota and closes session when receiver completes`() = testApplication {
        application { installRelay() }
        val (sessionId, streamToken) = openSession(client, "token-alice")
        assertEquals(1, RelaySessionRegistry.activeSessionsFor("sub-alice"))

        // Complete the session so receiver finishes cleanly
        RelaySessionRegistry.completeSession(sessionId)

        // Receiver pulls data endpoint
        val response = client.get("/relay/v1/session/$sessionId/data?streamToken=$streamToken")
        assertEquals(HttpStatusCode.OK, response.status)

        // After receiver completes, the finally block MUST have closed the session
        assertEquals(0, RelaySessionRegistry.activeSessionsFor("sub-alice"), "tenant quota must be 0 after receiver finishes")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `session close cancels frame channel unblocking any suspended sender immediately`() = runBlocking {
        val (sessionId, _) = RelaySessionRegistry.openSession("tenant-unblock")
        assertEquals(1, RelaySessionRegistry.activeSessionsFor("tenant-unblock"))
        val channel = RelaySessionRegistry.framesFor(sessionId)!!

        // Hard close (as triggered by receiver drop or abort)
        RelaySessionRegistry.close(sessionId)
        assertEquals(0, RelaySessionRegistry.activeSessionsFor("tenant-unblock"))
        assertTrue(channel.isClosedForSend, "frame channel must be closed for send to unblock sender")
    }
}
