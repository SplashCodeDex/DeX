package com.dexstudios.dex.network.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Android test suite verifying RelayCrypto behavior against the identical
 * HKDF-SHA256 and sequence-bound AES-256-GCM AEAD contract established in core/data.
 */
class RelayCryptoTest {

    @Test
    fun bothPeersDeriveIdenticalSessionKey() {
        val a = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")
        val b = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")

        assertEquals(RelayCrypto.KEY_LENGTH_BYTES, a.size)
        assertArrayEquals("Session key derivation must be deterministic", a, b)
    }

    @Test
    fun differentSessionIdsDeriveDifferentKeys() {
        val k1 = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")
        val k2 = RelayCrypto.deriveSessionKey("pair-token-secret", "session-2")
        assertNotEquals(k1.toList(), k2.toList())
    }

    @Test
    fun differentTokensDeriveDifferentKeys() {
        val k1 = RelayCrypto.deriveSessionKey("token-a", "session-1")
        val k2 = RelayCrypto.deriveSessionKey("token-b", "session-1")
        assertNotEquals(k1.toList(), k2.toList())
    }

    @Test
    fun sealThenOpenRoundTripsPayloads() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        for (size in listOf(0, 1, 15, 16, 64 * 1024, 256 * 1024)) {
            val plain = ByteArray(size) { (it % 251).toByte() }
            val sealed = RelayCrypto.seal(key, plain)
            val opened = RelayCrypto.open(key, sealed)
            assertArrayEquals("Round trip failed for size $size", plain, opened)
        }
    }

    @Test
    fun randomNoncesPreventCiphertextReuse() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val plain = "same-content".toByteArray(Charsets.UTF_8)
        val s1 = RelayCrypto.seal(key, plain)
        val s2 = RelayCrypto.seal(key, plain)

        assertNotEquals(s1.toList(), s2.toList())
        assertArrayEquals(plain, RelayCrypto.open(key, s1))
        assertArrayEquals(plain, RelayCrypto.open(key, s2))
    }

    @Test
    fun sequencedFrameVerificationEnforcesOrder() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val frame0 = RelayCrypto.sealFrame(key, 0L, "frame-zero".toByteArray())
        val frame1 = RelayCrypto.sealFrame(key, 1L, "frame-one".toByteArray())

        // Matching sequences open cleanly
        assertArrayEquals("frame-zero".toByteArray(), RelayCrypto.openFrame(key, 0L, frame0))
        assertArrayEquals("frame-one".toByteArray(), RelayCrypto.openFrame(key, 1L, frame1))

        // Wrong sequence fails closed (replay/reorder defense)
        assertThrows(RelayCryptoException::class.java) {
            RelayCrypto.openFrame(key, 1L, frame0) // Replay of frame 0 when expecting frame 1
        }
        assertThrows(RelayCryptoException::class.java) {
            RelayCrypto.openFrame(key, 0L, frame1) // Reordered frame 1 when expecting frame 0
        }
    }

    @Test
    fun wrongKeyFailsClosed() {
        val key = RelayCrypto.deriveSessionKey("tok-1", "s-1")
        val attackerKey = RelayCrypto.deriveSessionKey("tok-2", "s-1")
        val sealed = RelayCrypto.seal(key, "payload".toByteArray())

        assertThrows(RelayCryptoException::class.java) {
            RelayCrypto.open(attackerKey, sealed)
        }
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed = RelayCrypto.seal(key, "payload".toByteArray())
        sealed[sealed.size / 2] = (sealed[sealed.size / 2] + 1).toByte()

        assertThrows(RelayCryptoException::class.java) {
            RelayCrypto.open(key, sealed)
        }
    }

    @Test
    fun truncatedFrameFailsClosed() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed = RelayCrypto.sealFrame(key, 0L, "payload".toByteArray())
        val truncated = sealed.copyOf(sealed.size - 4)

        assertThrows(RelayCryptoException::class.java) {
            RelayCrypto.openFrame(key, 0L, truncated)
        }
    }
}
