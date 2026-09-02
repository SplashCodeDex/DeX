package com.dexstudios.dex.server.routes

import com.dexstudios.dex.core.sync.SyncEndpoints
import com.dexstudios.dex.core.sync.SyncExchangeRequest
import com.dexstudios.dex.core.sync.SyncExchangeResponse
import com.dexstudios.dex.server.auth.IdTokenVerifier
import com.dexstudios.dex.server.sync.SyncHostStore
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

/**
 * DoS bounds for the exchange surface (plan 032 hardening): a metadata sync is small by
 * definition; hostile clients fail fast BEFORE any parse or store write.
 */
private const val MAX_EXCHANGE_BODY_BYTES = 8L * 1024 * 1024
private const val MAX_EXCHANGE_BODY_CHARS = 8 * 1024 * 1024

/** Delta batch bound: the engine coalesces per key; huge batches are hostile. */
private const val MAX_DELTAS_PER_EXCHANGE = 1_000

/** Advisory field bound: deviceIds are fingerprints (short by construction). */
private const val MAX_DEVICE_ID_LENGTH = 256

/**
 * The sync host surface (plan 032): authenticated, tenant-scoped delta exchange
 * speaking the wire law defined in core/sync (SyncExchangeRequest/Response — the exact
 * DTOs the client's HttpSyncTransport serializes, so drift is impossible).
 *
 * Auth: `Authorization: Bearer <Google ID token>`; the tenant subtree is derived ONLY
 * from the verified token's `sub` claim — request data can never select a tenant.
 */
fun Route.syncRoutes(verifier: IdTokenVerifier, store: SyncHostStore) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    route(SyncEndpoints.EXCHANGE) {
        post {
            val bearer = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank()) {
                call.respondText("missing bearer token", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val tenant = verifier.verifyToken(bearer)
            if (tenant == null) {
                call.respondText("invalid or expired ID token", status = HttpStatusCode.Unauthorized)
                return@post
            }

            // DoS bounds: a hostile client must not be able to OOM the host with a giant
            // body or an unbounded delta batch. Fail fast BEFORE parsing.
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            if (contentLength != null && contentLength > MAX_EXCHANGE_BODY_BYTES) {
                call.respondText("exchange body too large", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }

            val rawBody = call.receiveText()
            if (rawBody.length > MAX_EXCHANGE_BODY_CHARS) {
                call.respondText("exchange body too large", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }

            val request = try {
                json.decodeFromString(SyncExchangeRequest.serializer(), rawBody)
            } catch (_: Exception) {
                call.respondText("malformed exchange body", status = HttpStatusCode.BadRequest)
                return@post
            }

            if (request.deltas.size > MAX_DELTAS_PER_EXCHANGE) {
                call.respondText("too many deltas in one exchange", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }

            if (request.deviceId.length > MAX_DEVICE_ID_LENGTH) {
                call.respondText("deviceId too long", status = HttpStatusCode.PayloadTooLarge)
                return@post
            }

            // deviceId is advisory metadata on the wire; the tenant boundary comes from
            // the token alone. Deltas outside legal collections are dropped by the store.
            request.deltas.forEach { store.merge(tenant, it) }

            // Delta window: only records newer than the caller's cursor. since=0 (first
            // contact / cursor loss) yields the full snapshot — backward compatible with
            // every deployed client, which simply never sends a cursor.
            val (records, hasMore) = store.snapshotSince(tenant, request.sinceHostSeq)
            call.respondText(
                json.encodeToString(
                    SyncExchangeResponse.serializer(),
                    SyncExchangeResponse(
                        records = records,
                        hostSeq = store.currentSeq(tenant),
                        hasMore = hasMore,
                    ),
                ),
                ContentType.Application.Json,
            )
        }
    }
}
