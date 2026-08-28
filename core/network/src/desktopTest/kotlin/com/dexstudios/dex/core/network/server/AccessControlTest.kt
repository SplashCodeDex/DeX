package com.dexstudios.dex.core.network.server

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unit coverage for the shared bearer-trust and loopback-gate helpers. */
class AccessControlTest {

    private lateinit var deviceConfig: DeviceConfig

    @Before
    fun setUp() {
        deviceConfig = mockk {
            every { this@mockk.identityHash } returns "identity-hash-secret"
            every { this@mockk.googleSub } returns "google-sub-secret"
            every { this@mockk.fingerprint } returns "pc-fingerprint"
        }
        startKoin { modules(module { single { deviceConfig } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
        AuthState.updateTokens(emptyMap())
        AuthState.updateFingerprints(emptySet())
    }

    // ---------------------------------------------------------------- isLoopbackBind

    @Test
    fun `loopback bind hosts are recognized`() {
        assertTrue(isLoopbackBind("127.0.0.1"))
        assertTrue(isLoopbackBind("::1"))
        assertTrue(isLoopbackBind("0:0:0:0:0:0:0:1"))
        assertTrue(isLoopbackBind("localhost"))
    }

    @Test
    fun `non-loopback bind hosts are rejected`() {
        assertFalse(isLoopbackBind("0.0.0.0"))
        assertFalse(isLoopbackBind("192.168.1.10"))
        assertFalse(isLoopbackBind("10.0.0.2"))
        assertFalse(isLoopbackBind(""))
    }

    // ----------------------------------------------------------------- BearerTrust.matches

    @Test
    fun `matches is exact equality for equal-length secrets`() {
        assertTrue(BearerTrust.matches("abc123", "abc123"))
        assertFalse(BearerTrust.matches("abc124", "abc123"))
        assertFalse(BearerTrust.matches("abc12", "abc123"))
        assertFalse(BearerTrust.matches("abc1234", "abc123"))
        assertFalse(BearerTrust.matches("anything", ""))
    }

    // ------------------------------------------------- BearerTrust.resolveHandshakeTrust

    @Test
    fun `handshake trust accepts googleSub tier and reports the identity credential`() {
        val (trusted, identity) = BearerTrust.resolveHandshakeTrust("phone-fp", "google-sub-secret")
        assertTrue(trusted)
        assertEquals("google-sub-secret", identity)
    }

    @Test
    fun `handshake trust accepts identityHash tier`() {
        val (trusted, identity) = BearerTrust.resolveHandshakeTrust("phone-fp", "identity-hash-secret")
        assertTrue(trusted)
        assertEquals("identity-hash-secret", identity)
    }

    @Test
    fun `handshake trust accepts only the presented fingerprint's paired token`() {
        AuthState.updateTokens(mapOf("phone-fp" to "paired-token-a", "other-fp" to "paired-token-b"))

        val (trusted, identity) = BearerTrust.resolveHandshakeTrust("phone-fp", "paired-token-a")
        assertTrue(trusted)
        assertEquals(null, identity)

        val (crossTrusted, _) = BearerTrust.resolveHandshakeTrust("other-fp", "paired-token-a")
        assertFalse(crossTrusted, "a paired token must not authenticate a different fingerprint")
    }

    @Test
    fun `handshake trust rejects empty, null and unknown bearers`() {
        assertFalse(BearerTrust.resolveHandshakeTrust("phone-fp", null).first)
        assertFalse(BearerTrust.resolveHandshakeTrust("phone-fp", "").first)
        assertFalse(BearerTrust.resolveHandshakeTrust("phone-fp", "junk").first)
    }

    // ---------------------------------------------------- BearerTrust.isTrustedBearer

    @Test
    fun `fingerprintless bearer accepts identity tiers and any stored pairing token`() {
        AuthState.updateTokens(mapOf("phone-fp" to "paired-token-a"))

        assertTrue(BearerTrust.isTrustedBearer("google-sub-secret"))
        assertTrue(BearerTrust.isTrustedBearer("identity-hash-secret"))
        assertTrue(BearerTrust.isTrustedBearer("paired-token-a"))
    }

    @Test
    fun `fingerprintless bearer rejects missing and unknown tokens`() {
        assertFalse(BearerTrust.isTrustedBearer(null))
        assertFalse(BearerTrust.isTrustedBearer(""))
        assertFalse(BearerTrust.isTrustedBearer("forged"))
    }
}
