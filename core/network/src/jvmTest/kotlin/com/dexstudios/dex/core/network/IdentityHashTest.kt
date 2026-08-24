package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.network.security.IdentityHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdentityHashTest {

    @Test
    fun testHashConsistency() {
        val hash1 = IdentityHash.calculateHash("user@dex.net", "sub_123")
        val hash2 = IdentityHash.calculateHash("user@dex.net", "sub_123")

        assertEquals("Hashes for identical inputs must match", hash1, hash2)
    }

    @Test
    fun testHashSaltVariation() {
        val hash1 = IdentityHash.calculateHash("user@dex.net", "sub_123", "salt_A")
        val hash2 = IdentityHash.calculateHash("user@dex.net", "sub_123", "salt_B")

        assertNotEquals("Different salts should produce different hashes", hash1, hash2)
    }
}
