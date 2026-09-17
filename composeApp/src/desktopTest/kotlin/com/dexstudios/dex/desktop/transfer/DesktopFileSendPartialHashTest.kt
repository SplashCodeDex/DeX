package com.dexstudios.dex.desktop.transfer

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.min

class DesktopFileSendPartialHashTest {

    private class ChunkedInputStream(private val data: ByteArray, private val maxChunkSize: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
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
        val service = DesktopFileSendService(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val data = ByteArray(100 * 1024) { index -> ((index * 31) % 256).toByte() }

        val normalStream = ByteArrayInputStream(data)
        val normalHash = service.computePartialHash(normalStream, data.size.toLong())
        assertNotNull(normalHash)

        val chunked1k = ChunkedInputStream(data, 1024)
        val chunked1kHash = service.computePartialHash(chunked1k, data.size.toLong())

        val chunked4k = ChunkedInputStream(data, 4096)
        val chunked4kHash = service.computePartialHash(chunked4k, data.size.toLong())

        assertEquals("Hash from 1KB chunked stream must match normal stream hash", normalHash, chunked1kHash)
        assertEquals("Hash from 4KB chunked stream must match normal stream hash", normalHash, chunked4kHash)
    }

    @Test
    fun computePartialHashReturnsNullForEmptyOrNonPositiveSize() {
        val service = DesktopFileSendService(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        val stream = ByteArrayInputStream(ByteArray(0))
        assertNull(service.computePartialHash(stream, 0L))
        assertNull(service.computePartialHash(stream, -5L))
    }
}
