package com.dexstudios.dex.core.network.sync

import com.dexstudios.dex.core.network.HashUtils
import com.dexstudios.dex.core.sync.SyncEndpoints
import com.dexstudios.dex.core.sync.SyncExchangeRequest
import com.dexstudios.dex.core.sync.SyncExchangeResponse
import com.dexstudios.dex.core.sync.SyncRecord
import com.dexstudios.dex.core.sync.SyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Ktor implementation of the sync transport port (plan 031 WP3): one authenticated
 * POST exchange against the self-hosted sync host. The bearer token is supplied by a
 * [tokenProvider] — the live Google ID token (short-lived, refreshed by the auth
 * layer) rather than a persisted credential.
 *
 * Failure semantics: transport errors propagate to the SyncEngine, which re-queues
 * deltas — offline-first means a failed exchange NEVER loses data.
 */
class HttpSyncTransport(
    private val client: HttpClient,
    /** Resolved per exchange so settings changes take effect without a restart. */
    private val baseUrlProvider: () -> String,
    private val tokenProvider: suspend () -> String,
    private val deviceIdProvider: () -> String,
) : SyncTransport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        /**
         * Exchange timeout: a wedged sync host must fail the request (the engine
         * re-queues + backs off), never stall the flush loop indefinitely. Generous —
         * a first-connect snapshot with a large history can legitimately be slow.
         */
        const val EXCHANGE_TIMEOUT_MS = 90_000L

        /**
         * Client-side response bound: the host caps batches at 5k records, so a legal
         * response is far below this; exceeding it implies a hostile host — fail closed
         * instead of parsing a body built to OOM the client.
         */
        const val MAX_RESPONSE_CHARS = 64 * 1024 * 1024
    }

    override suspend fun exchange(deltas: List<SyncRecord>, sinceHostSeq: Long?): com.dexstudios.dex.core.sync.SyncExchangeBatch {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IllegalStateException("No sync host configured — set it in Settings (sync stays disabled until then)")
        }
        val token = tokenProvider()
        if (token.isBlank()) {
            throw IllegalStateException("No Google ID token available for sync exchange — sign in first")
        }

        val response = client.post("$baseUrl${SyncEndpoints.EXCHANGE}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            timeout { requestTimeoutMillis = EXCHANGE_TIMEOUT_MS }
            setBody(
                json.encodeToString(
                    SyncExchangeRequest.serializer(),
                    SyncExchangeRequest(
                        deviceId = deviceIdProvider(),
                        deltas = deltas,
                        sinceHostSeq = sinceHostSeq ?: 0L,
                    ),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Sync host rejected the exchange: HTTP ${response.status.value}")
        }

        val raw = response.body<String>()
        // Client-side response bound (Patch E): a hostile/buggy host must not be able to
        // OOM the client with a giant response body — fail before parsing it.
        if (raw.length > MAX_RESPONSE_CHARS) {
            throw IllegalStateException("sync host response exceeded the client-side bound (${raw.length} chars)")
        }
        val body = json.decodeFromString(SyncExchangeResponse.serializer(), raw)
        return com.dexstudios.dex.core.sync.SyncExchangeBatch(
            records = body.records,
            hostSeq = body.hostSeq,
            hasMore = body.hasMore,
        )
    }
}
