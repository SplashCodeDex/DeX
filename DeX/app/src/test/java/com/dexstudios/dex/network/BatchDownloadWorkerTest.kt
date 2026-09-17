package com.dexstudios.dex.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDownloadWorkerTest {

    @Test
    fun `sizeMatches accepts exact match for positive file sizes`() {
        assertTrue(BatchDownloadWorker.sizeMatches(received = 1024L, expected = 1024L))
    }

    @Test
    fun `sizeMatches rejects partial received bytes for positive file sizes`() {
        assertFalse(BatchDownloadWorker.sizeMatches(received = 512L, expected = 1024L))
    }

    @Test
    fun `sizeMatches rejects oversized received bytes for positive file sizes`() {
        assertFalse(BatchDownloadWorker.sizeMatches(received = 2048L, expected = 1024L))
    }

    @Test
    fun `sizeMatches accepts zero bytes when expected size is zero`() {
        assertTrue(BatchDownloadWorker.sizeMatches(received = 0L, expected = 0L))
    }

    @Test
    fun `sizeMatches rejects non-zero bytes when expected size is zero`() {
        // A declared 0-byte file must reject non-empty streams (e.g. 100 bytes received)
        assertFalse(
            "A declared 0-byte file must not accept received bytes > 0",
            BatchDownloadWorker.sizeMatches(received = 100L, expected = 0L)
        )
    }

    @Test
    fun `sizeMatches accepts any non-negative bytes when expected size is negative (unknown)`() {
        assertTrue(BatchDownloadWorker.sizeMatches(received = 0L, expected = -1L))
        assertTrue(BatchDownloadWorker.sizeMatches(received = 4096L, expected = -1L))
    }
}
