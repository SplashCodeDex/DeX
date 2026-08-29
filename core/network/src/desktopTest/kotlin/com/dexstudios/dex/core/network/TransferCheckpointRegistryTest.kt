package com.dexstudios.dex.core.network

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferCheckpointRegistryTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "dex_checkpoint_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testPartFileLifecycleAndCommit() {
        val sessionId = "session-123"
        val fileId = "file-456"
        val fileName = "video.mp4"
        val expectedSize = 1000L

        val partFile = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, sessionId, fileId, fileName, expectedSize)
        assertTrue(partFile.name.startsWith("video.mp4.part.session-123"))

        // Write partial bytes
        partFile.writeBytes(ByteArray(400))
        assertEquals(400L, TransferCheckpointRegistry.getExistingOffset(sessionId, fileId))

        // Append remaining bytes
        java.io.FileOutputStream(partFile, true).use {
            it.write(ByteArray(600))
        }
        assertEquals(1000L, TransferCheckpointRegistry.getExistingOffset(sessionId, fileId))

        // Commit to final destination
        val destFile = File(tempDir, "video.mp4")
        val committed = TransferCheckpointRegistry.commitPartFile(sessionId, fileId, destFile)
        assertTrue(committed)
        assertTrue(destFile.exists())
        assertEquals(1000L, destFile.length())
        assertFalse(partFile.exists())
    }

    @Test
    fun testDiscardPartFile() {
        val sessionId = "session-discard"
        val fileId = "file-discard"
        val partFile = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, sessionId, fileId, "doc.pdf", 500L)
        partFile.writeBytes(ByteArray(200))
        assertTrue(partFile.exists())

        TransferCheckpointRegistry.discardPartFile(sessionId, fileId)
        assertFalse(partFile.exists())
        assertEquals(0L, TransferCheckpointRegistry.getExistingOffset(sessionId, fileId))
    }
}
