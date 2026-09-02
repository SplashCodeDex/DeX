package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.sync.HlcTimestamp
import com.dexstudios.dex.core.sync.SyncEndpoints
import com.dexstudios.dex.core.sync.SyncExchangeRequest
import com.dexstudios.dex.core.sync.SyncExchangeResponse
import com.dexstudios.dex.core.sync.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Plan 031 WP3 contract: the HTTP transport must round-trip the shared exchange DTOs
 * byte-faithfully against a host speaking the SAME wire law (the 032 server will be
 * built on these exact definitions), carry the bearer auth, propagate failures to the
 * engine (which re-queues), and refuse to send when unauthenticated.
 */
class HttpSyncTransportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun sampleDelta() = SyncRecord(
        collection = "history",
        key = "rec-1",
        hlc = HlcTimestamp(1_000L, 3L),
        deviceId = "device-a",
        payload = buildJsonObject {
            put("name", "photo.jpg")
            put("size", 1024L)
            put("direction", "received")
        },
    )

    private fun hostRecord() = SyncRecord(
        collection = "settings",
        key = "theme",
        hlc = HlcTimestamp(2_000L, 0L),
        deviceId = "device-b",
        payload = buildJsonObject { put("theme", "dark") },
    )

    @Test
    fun `exchange round-trips deltas and host records through the shared DTOs`() = testApplication {
        var receivedAuth: String? = null
        var receivedRequest: SyncExchangeRequest? = null
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
            routing {
                post(SyncEndpoints.EXCHANGE) {
                    receivedAuth = call.request.header(HttpHeaders.Authorization)
                    receivedRequest = json.decodeFromString(
                        SyncExchangeRequest.serializer(),
                        call.receiveText(),
                    )
                    call.respondText(
                        json.encodeToString(
                            SyncExchangeResponse.serializer(),
                            SyncExchangeResponse(records = listOf(hostRecord()), hostSeq = 42L, hasMore = false),
                        ),
                        io.ktor.http.ContentType.Application.Json,
                    )
                }
            }
        }

        val transport = HttpSyncTransport(
            client = createClient {
                install(ContentNegotiation) { json(json) }
            },
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "google-id-token" },
            deviceIdProvider = { "device-a" },
        )

        val batch = transport.exchange(listOf(sampleDelta()), sinceHostSeq = 7L)

        assertEquals("Bearer google-id-token", receivedAuth, "auth header must carry the live ID token")
        assertEquals("device-a", receivedRequest?.deviceId)
        assertEquals(1, receivedRequest?.deltas?.size)
        assertEquals(7L, receivedRequest?.sinceHostSeq, "the cursor must ride the request")
        assertEquals(sampleDelta(), receivedRequest?.deltas?.single())
        assertEquals(listOf(hostRecord()), batch.records)
        assertEquals(42L, batch.hostSeq, "the cursor must return for persistence")
    }

    @Test
    fun `transport errors propagate so the engine can re-queue`() = testApplication {
        application {
            routing {
                post(SyncEndpoints.EXCHANGE) {
                    call.respondText("denied", status = io.ktor.http.HttpStatusCode.Forbidden)
                }
            }
        }

        val transport = HttpSyncTransport(
            client = createClient {
                install(ContentNegotiation) { json(json) }
            },
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "expired-token" },
            deviceIdProvider = { "device-a" },
        )

        val error = assertFailsWith<IllegalStateException> { transport.exchange(listOf(sampleDelta())) }
        assertTrue(error.message!!.contains("403"), "the HTTP status must surface in the error: ${error.message}")
    }

    @Test
    fun `a blank token refuses to exchange instead of sending unauthenticated`() = kotlinx.coroutines.runBlocking {
        val transport = HttpSyncTransport(
            client = HttpClient(),
            baseUrlProvider = { "https://sync.invalid" },
            tokenProvider = { "" },
            deviceIdProvider = { "device-a" },
        )

        val error = assertFailsWith<IllegalStateException> { transport.exchange(emptyList()) }
        assertTrue(error.message!!.contains("sign in"), "refusal must be explicit: ${error.message}")
    }

    @Test
    fun `empty delta lists are legal on the wire`() = testApplication {
        var receivedEmpty = false
        application {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
            routing {
                post(SyncEndpoints.EXCHANGE) {
                    val request = json.decodeFromString(SyncExchangeRequest.serializer(), call.receiveText())
                    receivedEmpty = request.deltas.isEmpty()
                    call.respondText(
                        json.encodeToString(SyncExchangeResponse.serializer(), SyncExchangeResponse(emptyList(), hostSeq = 5L)),
                        io.ktor.http.ContentType.Application.Json,
                    )
                }
            }
        }

        val transport = HttpSyncTransport(
            client = createClient {
                install(ContentNegotiation) { json(json) }
            },
            baseUrlProvider = { "http://localhost" },
            tokenProvider = { "token" },
            deviceIdProvider = { "device-a" },
        )

        val batch = transport.exchange(emptyList())
        assertEquals(emptyList(), batch.records)
        assertEquals(5L, batch.hostSeq)
        assertTrue(receivedEmpty)
    }
}
