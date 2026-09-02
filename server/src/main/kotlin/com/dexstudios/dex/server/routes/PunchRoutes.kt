package com.dexstudios.dex.server.routes

import com.dexstudios.dex.server.auth.IdTokenVerifier
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.ConcurrentHashMap

/**
 * NAT-punch rendezvous for WAN peers (plan 032) — the server-side port of the desktop's
 * /punch/endpoint semantics (DeviceRoutes.kt), moved OFF the desktop so it stays a peer
 * and never a rendezvous bottleneck.
 *
 * LAW (plan 032): trusted-caller-only rules migrate verbatim. BOTH routes require the
 * verified bearer token; the caller's device fingerprint must ALSO match the verified
 * account's registered device set is a client-side concern — at the server we enforce
 * the account boundary (any registered device of the SAME account may resolve another
 * device of that account; a stranger from a different account must never poison the
 * table or learn endpoints).
 *
 * Entries expire 5 minutes after registration (desktop parity) via read-time TTL checks
 * plus opportunistic sweeps on every request.
 */
private data class PunchEntry(val tenant: String, val ip: String, val port: Int, val registeredAt: Long)

private val punchTable = ConcurrentHashMap<String, PunchEntry>()
private const val PUNCH_TTL_MS = 5 * 60 * 1000L

/** Hard bound: one account may never register more than this many punch endpoints. */
private const val MAX_PUNCH_ENTRIES = 64

/**
 * GLOBAL table bound (plan 032 hardening): the per-tenant cap alone does not bound
 * total memory — a botnet of throwaway accounts could each register their 64 entries
 * and OOM the host. The table itself is bounded; overflow rejects NEW tenants first
 * (existing tenants keep refreshing — an established account is never wedged by junk).
 */
private const val MAX_PUNCH_TABLE_TOTAL = 10_000

private fun stale(entry: PunchEntry, now: Long = System.currentTimeMillis()) = now - entry.registeredAt > PUNCH_TTL_MS

private fun sweep() {
    val now = System.currentTimeMillis()
    punchTable.entries.removeIf { stale(it.value, now) }
}

fun Route.punchRoutes(verifier: IdTokenVerifier) {
    route("/punch") {
        get("/register") {
            val bearer = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank()) {
                call.respondText("missing bearer token", status = HttpStatusCode.Unauthorized)
                return@get
            }
            val tenant = verifier.verifyToken(bearer)
            if (tenant == null) {
                call.respondText("invalid or expired ID token", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val fingerprint = call.request.queryParameters["fingerprint"]
            val ip = call.request.queryParameters["ip"]
            val port = call.request.queryParameters["port"]?.toIntOrNull()

            if (fingerprint.isNullOrBlank() || fingerprint.length > 256 ||
                ip.isNullOrBlank() || ip.length > 64 ||
                port == null || port !in 1..65535
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            sweep()

            // Account boundary + DoS bounds: this TENANT's count is capped, and the
            // TABLE total is capped (botnet-of-accounts OOM guard — new tenants are
            // rejected first; established tenants keep refreshing).
            val tenantEntries = punchTable.values.count { it.tenant == tenant }
            val isRefresh = punchTable[fingerprint]?.tenant == tenant
            if (tenantEntries >= MAX_PUNCH_ENTRIES && !isRefresh) {
                call.respondText("punch table cap reached", status = HttpStatusCode.TooManyRequests)
                return@get
            }
            if (!isRefresh && punchTable.size >= MAX_PUNCH_TABLE_TOTAL) {
                call.respondText("punch table full", status = HttpStatusCode.TooManyRequests)
                return@get
            }

            punchTable[fingerprint] = PunchEntry(tenant, ip, port, System.currentTimeMillis())

            // Self-contained JSON: no ContentNegotiation dependency, so the route
            // behaves identically with or without the plugin installed.
            call.respondText(
                """{"fingerprint":"$fingerprint","ip":"$ip","port":$port}""",
                ContentType.Application.Json,
            )
        }

        get("/resolve") {
            val bearer = call.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank()) {
                call.respondText("missing bearer token", status = HttpStatusCode.Unauthorized)
                return@get
            }
            val tenant = verifier.verifyToken(bearer)
            if (tenant == null) {
                call.respondText("invalid or expired ID token", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val fingerprint = call.request.queryParameters["fingerprint"]
            if (fingerprint.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            sweep()

            val entry = punchTable[fingerprint]
            when {
                // Unknown or expired: indistinguishable to the caller (no oracle).
                entry == null -> call.respond(HttpStatusCode.NotFound)

                // Cross-account resolve is a hard boundary.
                entry.tenant != tenant -> call.respond(HttpStatusCode.NotFound)

                else -> call.respondText(
                    """{"fingerprint":"$fingerprint","ip":"${entry.ip}","port":${entry.port}}""",
                    ContentType.Application.Json,
                )
            }
        }
    }
}
