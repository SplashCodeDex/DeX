package com.dexstudios.dex.core.network

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Plan 032 contract suite for the E2EE relay key schedule: identical derivation on both
 * peers, AEAD round-trips, tamper/truncation/wrong-key fail-closed, and session-key
 * uniqueness per sessionId (one transfer's key never opens another's).
 */
class RelayCryptoTest {

    @Test
    fun `both peers derive the identical session key from token plus session id`() {
        val a = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")
        val b = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")

        assertEquals(RelayCrypto.KEY_LENGTH_BYTES, a.size)
        assertContentEquals(a, b, "the key schedule must be deterministic on both peers")
    }

    @Test
    fun `different session ids derive different keys`() {
        val k1 = RelayCrypto.deriveSessionKey("pair-token-secret", "session-1")
        val k2 = RelayCrypto.deriveSessionKey("pair-token-secret", "session-2")
        assertNotEquals(k1.toList(), k2.toList(), "key reuse across sessions is forbidden")
    }

    @Test
    fun `different tokens derive different keys`() {
        val k1 = RelayCrypto.deriveSessionKey("token-a", "session-1")
        val k2 = RelayCrypto.deriveSessionKey("token-b", "session-1")
        assertNotEquals(k1.toList(), k2.toList())
    }

    @Test
    fun `seal-then-open round-trips arbitrary payload sizes`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        for (size in listOf(0, 1, 15, 16, 256 * 1024, 1024 * 1024 + 7)) {
            val plain = ByteArray(size) { (it % 251).toByte() }
            val sealed = RelayCrypto.seal(key, plain)
            val opened = RelayCrypto.open(key, sealed)
            assertContentEquals(plain, opened, "round trip failed at $size bytes")
        }
    }

    @Test
    fun `sealing the same plaintext twice yields different ciphertexts (random nonces)`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val plain = "same-plaintext".toByteArray()

        val s1 = RelayCrypto.seal(key, plain)
        val s2 = RelayCrypto.seal(key, plain)

        assertNotEquals(s1.toList(), s2.toList(), "nonce reuse would be catastrophic; nonces must be random")
        assertContentEquals(plain, RelayCrypto.open(key, s1))
        assertContentEquals(plain, RelayCrypto.open(key, s2))
    }

    @Test
    fun `a wrong key fails closed`() {
        val senderKey = RelayCrypto.deriveSessionKey("token-sender", "s-1")
        val attackerKey = RelayCrypto.deriveSessionKey("token-attacker", "s-1")
        val sealed = RelayCrypto.seal(senderKey, "secret-content".toByteArray())

        assertFailsWith<RelayCryptoException> { RelayCrypto.open(attackerKey, sealed) }
    }

    @Test
    fun `tampered ciphertext fails closed`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed = RelayCrypto.seal(key, "integrity-checked".toByteArray())

        sealed[sealed.size / 2] = (sealed[sealed.size / 2] + 1).toByte()

        assertFailsWith<RelayCryptoException> { RelayCrypto.open(key, sealed) }
    }

    @Test
    fun `truncated chunks fail closed`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed = RelayCrypto.seal(key, "some payload".toByteArray())

        val truncated = sealed.copyOf(sealed.size - 5)

        assertFailsWith<RelayCryptoException> { RelayCrypto.open(key, truncated) }
    }

    @Test
    fun `empty and oversized-nonce chunks are rejected`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")

        assertFailsWith<RelayCryptoException> { RelayCrypto.open(key, ByteArray(0)) }
        assertFailsWith<RelayCryptoException> { RelayCrypto.open(key, ByteArray(RelayCrypto.NONCE_LENGTH_BYTES)) }
    }

    @Test
    fun `streamed chunks decrypt independently and in order`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val chunks = (0 until 16).map { i -> ByteArray(64 * 1024) { b -> (b + i).toByte() } }

        val sealedChunks = chunks.map { RelayCrypto.seal(key, it) }
        val opened = sealedChunks.map { RelayCrypto.open(key, it) }

        chunks.zip(opened).forEach { (original, roundTripped) ->
            assertContentEquals(original, roundTripped)
        }
        assertTrue(sealedChunks.map { it.toList() }.distinct().size == sealedChunks.size, "every chunk seals uniquely")
    }

    // ------------------------------------------------------------------
    // Frame-sequenced AEAD (replay / reorder / gap defense)
    // ------------------------------------------------------------------

    @Test
    fun `sequenced frames round-trip in order`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val frames = (0 until 8).map { i -> ByteArray(1024) { b -> (b + i).toByte() } }

        val sealed = frames.mapIndexed { i, f -> RelayCrypto.sealFrame(key, i.toLong(), f) }
        val opened = sealed.mapIndexed { i, s -> RelayCrypto.openFrame(key, i.toLong(), s) }

        frames.zip(opened).forEach { (a, b) -> assertContentEquals(a, b) }
    }

    @Test
    fun `a replayed frame at the wrong sequence fails closed`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed0 = RelayCrypto.sealFrame(key, 0L, "frame-zero".toByteArray())
        val sealed1 = RelayCrypto.sealFrame(key, 1L, "frame-one".toByteArray())

        // The attacker relays frame 0 AGAIN in slot 1 (duplicate delivery):
        assertFailsWith<RelayCryptoException> { RelayCrypto.openFrame(key, 1L, sealed0) }
        // And frame 1 replayed in slot 0 (reorder):
        assertFailsWith<RelayCryptoException> { RelayCrypto.openFrame(key, 0L, sealed1) }
    }

    @Test
    fun `a gapped sequence fails closed at the missed frame`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val sealed = RelayCrypto.sealFrame(key, 3L, "frame-three".toByteArray())

        // The receiver expects seq 0 but gets seq 3 (frames 0..2 vanished): fail closed.
        assertFailsWith<RelayCryptoException> { RelayCrypto.openFrame(key, 0L, sealed) }
    }

    @Test
    fun `the same plaintext at different sequences seals differently`() {
        val key = RelayCrypto.deriveSessionKey("tok", "s-1")
        val plain = "same".toByteArray()

        val s0 = RelayCrypto.sealFrame(key, 0L, plain)
        val s1 = RelayCrypto.sealFrame(key, 1L, plain)

        assertNotEquals(s0.toList(), s1.toList(), "seq uniqueness must also diverge ciphertexts")
        assertContentEquals(plain, RelayCrypto.openFrame(key, 0L, s0))
        assertContentEquals(plain, RelayCrypto.openFrame(key, 1L, s1))
    }
}
