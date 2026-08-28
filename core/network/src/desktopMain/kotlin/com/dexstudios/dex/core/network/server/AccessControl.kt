package com.dexstudios.dex.core.network.server

import com.dexstudios.dex.auth.AuthState
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import java.security.MessageDigest

/**
 * Single home for bearer-credential trust resolution (constant-time throughout).
 *
 * A presented bearer is TRUSTED when it equals our googleSub / identityHash (same-account
 * auto-trust) or the fingerprint's stored pairing token. These values are NEVER advertised
 * (see DiscoveryEngine.localInfo) so only legitimate holders can present them. Every
 * consumer — the /ws handshake, HTTP routes, clipboard push — must resolve trust HERE,
 * never with its own ad-hoc comparison.
 */
object BearerTrust {
    /** Length pre-checked constant-time equality for bearer/secret pairs. */
    fun matches(presented: String, secret: String): Boolean = secret.isNotEmpty() && presented.length == secret.length &&
        MessageDigest.isEqual(presented.toByteArray(), secret.toByteArray())

    /**
     * Fingerprint-scoped trust for the /ws handshake: the paired-token tier is checked
     * against [fingerprint]'s stored token only, and the winning identity credential is
     * returned so same-email roster membership can be derived from the session.
     */
    fun resolveHandshakeTrust(fingerprint: String, token: String?): Pair<Boolean, String?> {
        if (token.isNullOrEmpty()) return false to null
        val deviceConfig = org.koin.core.context.GlobalContext.get()
            .get<com.dexstudios.dex.core.network.DeviceConfig>()

        if (matches(token, deviceConfig.googleSub)) return true to deviceConfig.googleSub
        if (matches(token, deviceConfig.identityHash)) return true to deviceConfig.identityHash

        val pairedToken = AuthState.pairedTokens.value[fingerprint]
        if (!pairedToken.isNullOrEmpty() && matches(token, pairedToken)) {
            return true to null
        }
        return false to null
    }

    /**
     * Fingerprintless trust for HTTP routes whose sender is not otherwise identified
     * (e.g. clipboard push): identity tiers are checked as usual, and a presented paired
     * token is accepted when it matches ANY stored pairing. Tokens are unguessable UUIDs,
     * so the multi-entry compare leaks nothing actionable.
     */
    fun isTrustedBearer(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val deviceConfig = org.koin.core.context.GlobalContext.get()
            .get<com.dexstudios.dex.core.network.DeviceConfig>()

        if (matches(token, deviceConfig.googleSub)) return true
        if (matches(token, deviceConfig.identityHash)) return true
        return AuthState.pairedTokens.value.values.any { matches(token, it) }
    }
}

/** Host strings that mark a listener (or connection) as loopback-bound. */
private val LOOPBACK_HOSTS = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost")

/**
 * True when [host] names a loopback binding. "localhost" is included so the Ktor
 * test harness (which reports serverHost=localhost) exercises the same gate the
 * production loopback listeners (127.0.0.1) do.
 */
fun isLoopbackBind(host: String): Boolean = host in LOOPBACK_HOSTS

/**
 * Gate for the `/local/` automation surfaces: respond 403 and return false unless this
 * request arrived on a loopback-bound listener. The `/local/` prefix is a loopback-only
 * contract (plan 021) — these routes must never be reachable from the LAN listeners.
 */
suspend fun RoutingContext.guardLoopback(): Boolean {
    if (isLoopbackBind(call.request.local.serverHost)) return true
    call.respond(HttpStatusCode.Forbidden)
    return false
}
