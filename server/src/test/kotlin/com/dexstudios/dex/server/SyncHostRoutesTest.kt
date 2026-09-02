package com.dexstudios.dex.server

import com.dexstudios.dex.core.sync.HlcTimestamp
import com.dexstudios.dex.core.sync.SyncCollections
import com.dexstudios.dex.core.sync.SyncEndpoints
import com.dexstudios.dex.core.sync.SyncExchangeRequest
import com.dexstudios.dex.core.sync.SyncExchangeResponse
import com.dexstudios.dex.core.sync.SyncRecord
import com.dexstudios.dex.server.auth.FixtureIdTokenVerifier
import com.dexstudios.dex.server.auth.IdTokenVerifier
import com.dexstudios.dex.server.routes.syncRoutes
import com.dexstudios.dex.server.sync.SyncHostStore
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 032 contract suite for the sync host surface: auth gates, tenant isolation,
 * the deterministic HLC-LWW merge law (same [SyncRecord.supersedes] every peer uses),
 * and legal-collection enforcement.
 */
class SyncHostRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val verifier: IdTokenVerifier = FixtureIdTokenVerifier(
        mapOf(
            "token-alice" to "sub-alice",
            "token-bob" to "sub-bob",
        ),
    )

    private fun Application.installSync(store: SyncHostStore) {
        install(ContentNegotiation) { json(json) }
        routing { syncRoutes(verifier, store) }
    }

    private fun payload(alias: String) = buildJsonObject { put("alias", alias) }

    private fun record(collection: String = SyncCollections.SETTINGS, key: String, physical: Long, deviceId: String, alias: String? = "x") =
        SyncRecord(collection, key, HlcTimestamp(physical, 0L), deviceId, alias?.let { buildJsonObject { put("alias", it) } })

    private suspend fun io.ktor.client.HttpClient.push(token: String, vararg deltas: SyncRecord): String {
        val response = post(SyncEndpoints.EXCHANGE) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(json.encodeToString(SyncExchangeRequest.serializer(), SyncExchangeRequest("device-a", deltas.toList())))
        }
        return response.bodyAsText()
    }

    private suspend fun io.ktor.client.HttpClient.pushSince(token: String, sinceHostSeq: Long): String {
        val response = post(SyncEndpoints.EXCHANGE) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                json.encodeToString(
                    SyncExchangeRequest.serializer(),
                    SyncExchangeRequest("device-a", emptyList(), sinceHostSeq = sinceHostSeq),
                ),
            )
        }
        return response.bodyAsText()
    }

    @Test
    fun `exchange without bearer is rejected`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val response = client.post(SyncEndpoints.EXCHANGE) {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d","deltas":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `exchange with invalid token is rejected`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val response = client.post(SyncEndpoints.EXCHANGE) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer forged-token")
            setBody("""{"deviceId":"d","deltas":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticated exchange stores deltas and echoes the snapshot`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val delta = record(key = "theme", physical = 1_000L, deviceId = "device-a", alias = "dark")
        val body = client.push("token-alice", delta)

        val echoed = json.decodeFromString(SyncExchangeResponse.serializer(), body)
        assertEquals(listOf(delta), echoed.records)
        assertEquals(delta, store.record("sub-alice", SyncCollections.SETTINGS, "theme"))
    }

    @Test
    fun `tenants are isolated - bobs token never sees alices subtree`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val aliceRecord = record(key = "theme", physical = 1_000L, deviceId = "device-a", alias = "dark")
        client.push("token-alice", aliceRecord)

        // Bob exchanges: his snapshot must NOT contain Alice's record.
        val bobBody = client.push("token-bob")
        val bobSnapshot = json.decodeFromString(SyncExchangeResponse.serializer(), bobBody)
        assertTrue(bobSnapshot.records.none { it.key == "theme" }, "cross-tenant leak")
        assertNull(store.record("sub-bob", SyncCollections.SETTINGS, "theme"))
    }

    @Test
    fun `the host applies the same supersedes law as every peer`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val older = record(key = "theme", physical = 1_000L, deviceId = "device-a", alias = "old")
        val newer = record(key = "theme", physical = 2_000L, deviceId = "device-a", alias = "new")
        val staleResend = older.copy(deviceId = "device-b")

        client.push("token-alice", older)
        client.push("token-alice", newer)
        client.push("token-alice", staleResend) // delayed re-delivery of the superseded record

        val stored = store.record("sub-alice", SyncCollections.SETTINGS, "theme")
        assertEquals("new", (stored?.payload as? JsonObject)?.get("alias")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `illegal collections are dropped at the door`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        val evil = record(collection = "clipboard-content", key = "c1", physical = 1_000L, deviceId = "device-a")
        client.push("token-alice", evil)

        assertNull(store.record("sub-alice", "clipboard-content", "c1"))
    }

    @Test
    fun `an up-to-date cursor yields an empty delta window instead of the full snapshot`() = testApplication {
        val store = SyncHostStore()
        application { installSync(store) }

        // Alice writes two records; the full snapshot path sees both.
        val r1 = record(key = "theme", physical = 1_000L, deviceId = "device-a", alias = "dark")
        val r2 = record(key = "alias", physical = 1_100L, deviceId = "device-a", alias = "Pixel")
        client.push("token-alice", r1, r2)
        val first = json.decodeFromString(SyncExchangeResponse.serializer(), client.push("token-alice"))
        assertEquals(2, first.records.size, "no cursor = full snapshot")

        // No new writes: the next exchange with the returned cursor gets NOTHING.
        val idle = json.decodeFromString(
            SyncExchangeResponse.serializer(),
            client.pushSince("token-alice", sinceHostSeq = first.hostSeq),
        )
        assertEquals(0, idle.records.size, "an up-to-date client's exchange is a no-op pull")
        assertEquals(first.hostSeq, idle.hostSeq)

        // One new write: exactly that record arrives.
        val r3 = record(key = "locale", physical = 2_000L, deviceId = "device-a", alias = "en-US")
        client.push("token-alice", r3)
        val delta = json.decodeFromString(
            SyncExchangeResponse.serializer(),
            client.pushSince("token-alice", sinceHostSeq = first.hostSeq),
        )
        assertEquals(listOf("locale"), delta.records.map { it.key }, "delta windows return only newer records")
    }
}
