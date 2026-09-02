package com.dexstudios.dex.server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory

/**
 * Verifies Google ID tokens and yields the tenant identity (the `sub` claim — the SAME
 * `googleSub` namespace the DeX trust model is keyed on, per docs/ARCHITECTURE.md).
 *
 * Verification covers signature (RS256 against Google's cached public keys), issuer
 * (`accounts.google.com`), audience (the DeX OAuth client id), and expiry with clock
 * skew allowance — per the current documented Google guidance
 * (developers.google.com/identity/sign-in/web/backend-auth).
 */
interface IdTokenVerifier {
    /** Returns the verified `sub` (googleSub), or null when the token fails any check. */
    fun verifyToken(idToken: String): String?
}

/** Production verifier backed by GoogleIdTokenVerifier (cached public keys). */
class GoogleIdTokenVerifierAdapter(audienceClientId: String) : IdTokenVerifier {
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(listOf(audienceClientId))
        .build()

    override fun verifyToken(idToken: String): String? = runCatching {
        verifier.verify(idToken)?.payload?.subject
    }.getOrNull()
}

/**
 * Test/dev verifier: an in-memory token -> sub table. NEVER wired in production — the
 * server refuses to start with it outside tests (MainKt guard).
 */
class FixtureIdTokenVerifier(private val tokens: Map<String, String>) : IdTokenVerifier {
    override fun verifyToken(idToken: String): String? = tokens[idToken]
}
