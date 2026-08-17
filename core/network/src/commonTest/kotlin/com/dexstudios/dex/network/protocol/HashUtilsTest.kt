package com.dexstudios.dex.network.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class HashUtilsTest {

    @Test
    fun sha256_computes_correct_hash() {
        val input = "hello world"
        // echo -n "hello world" | sha256sum
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        
        val actual = HashUtils.sha256(input)
        assertEquals(expected, actual)
    }

    @Test
    fun generateUUID_returns_unique_string() {
        val uuid1 = HashUtils.generateUUID()
        val uuid2 = HashUtils.generateUUID()
        
        assertNotNull(uuid1)
        assertNotEquals(uuid1, uuid2)
        assertEquals(36, uuid1.length)
    }
}
