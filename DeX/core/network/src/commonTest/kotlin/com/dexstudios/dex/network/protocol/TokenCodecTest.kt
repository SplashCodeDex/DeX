package com.dexstudios.dex.network.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenCodecTest {

    @Test
    fun encode_and_decode_map() {
        val original = mapOf(
            "fp1" to "token_abc",
            "fp2" to "token_def"
        )
        
        val encoded = TokenCodec.encode(original)
        val decoded = TokenCodec.decode(encoded)
        
        assertEquals(original, decoded)
    }

    @Test
    fun decode_handles_empty_string() {
        val decoded = TokenCodec.decode("")
        assertEquals(emptyMap<String, String>(), decoded)
    }
    
    @Test
    fun encode_handles_empty_map() {
        val encoded = TokenCodec.encode(emptyMap())
        assertEquals("", encoded)
    }
}
