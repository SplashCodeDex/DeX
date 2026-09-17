package com.dexstudios.dex.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.min

class HashUtilsTest {

    private class ChunkedInputStream(private val data: ByteArray, private val maxChunkSize: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            return if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val toRead = min(len, min(maxChunkSize, data.size - pos))
            System.arraycopy(data, pos, b, off, toRead)
            pos += toRead
            return toRead
        }
    }

    @Test
    fun computePartialHashProducesConsistentHashRegardlessOfStreamChunking() {
        // Create 100KB of predictable test data
        val data = ByteArray(100 * 1024) { index -> ((index * 31) % 256).toByte() }

        val normalStream = ByteArrayInputStream(data)
        val normalHash = HashUtils.computePartialHash(normalStream, data.size.toLong())
        assertNotNull(normalHash)

        // Same data read via small 1KB chunks (simulating Binder / ContentProvider pipe)
        val chunked1k = ChunkedInputStream(data, 1024)
        val chunked1kHash = HashUtils.computePartialHash(chunked1k, data.size.toLong())

        // Same data read via 4KB chunks
        val chunked4k = ChunkedInputStream(data, 4096)
        val chunked4kHash = HashUtils.computePartialHash(chunked4k, data.size.toLong())

        assertEquals("Hash from 1KB chunked stream must match normal stream hash", normalHash, chunked1kHash)
        assertEquals("Hash from 4KB chunked stream must match normal stream hash", normalHash, chunked4kHash)
    }

    @Test
    fun computePartialHashReturnsNullForEmptyOrNonPositiveSize() {
        val stream = ByteArrayInputStream(ByteArray(0))
        assertNull(HashUtils.computePartialHash(stream, 0L))
        assertNull(HashUtils.computePartialHash(stream, -10L))
    }
}
