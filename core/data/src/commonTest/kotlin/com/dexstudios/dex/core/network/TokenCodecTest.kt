package com.dexstudios.dex.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the paired-token persistence format: the DataStore value is written by
 * [TokenCodec.encode] and hydrated by [TokenCodec.decode], so a round trip must be lossless
 * and every corruption path must degrade to an empty map instead of killing hydration.
 */
class TokenCodecTest {

    @Test
    fun `encode decode round trips paired tokens`() {
        val tokens = mapOf("fp-a" to "tok-1", "fp-b" to "tok-2", "fp-c" to "")
        assertEquals(tokens, TokenCodec.decode(TokenCodec.encode(tokens)))
    }

    @Test
    fun `blank storage decodes to an empty map`() {
        assertTrue(TokenCodec.decode("").isEmpty())
        assertTrue(TokenCodec.decode("   ").isEmpty())
    }

    @Test
    fun `corrupt storage decodes to an empty map instead of throwing`() {
        assertTrue(TokenCodec.decode("not json {").isEmpty())
        assertTrue(TokenCodec.decode("[1,2,3]").isEmpty())
        assertTrue(TokenCodec.decode("\"just a string\"").isEmpty())
    }

    @Test
    fun `non-string values are rejected rather than coerced`() {
        // A hostile or hand-edited store must never surface a coerced token.
        assertTrue(TokenCodec.decode("""{"fp-a": 12345}""").isEmpty())
    }

    @Test
    fun `empty map encodes to a compact json object`() {
        assertEquals("{}", TokenCodec.encode(emptyMap()))
    }
}
