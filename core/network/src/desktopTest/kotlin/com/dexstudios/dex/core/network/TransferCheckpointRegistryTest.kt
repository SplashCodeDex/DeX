package com.dexstudios.dex.core.network

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        // The registry is a process-wide singleton: entries must not leak into the next test.
        TransferCheckpointRegistry.clear()
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
        assertNotNull(committed)
        assertEquals(destFile, committed)
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

    @Test
    fun `staging is unique per file so same-named files never share a part file`() {
        // One batch may legally carry two files that sanitize to the same basename in the same
        // destination directory. They arrive on separate connections; sharing staging would
        // interleave their bytes and commit a corrupt file for one of them.
        val first = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, "session-dup", "file-a", "report.pdf", 100L)
        val second = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, "session-dup", "file-b", "report.pdf", 100L)

        assertTrue(first != second, "Two files of one session must never share a staging file")
        assertTrue(first.name.contains("file-a"), "Staging must identify the file, got ${first.name}")
        assertTrue(second.name.contains("file-b"), "Staging must identify the file, got ${second.name}")
    }

    @Test
    fun `commit disambiguates instead of deleting a destination that already exists`() {
        val existing = File(tempDir, "photo.jpg")
        existing.writeBytes("original".toByteArray())

        val part = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, "session-clash", "file-clash", "photo.jpg", 4L)
        part.writeBytes("new!".toByteArray())

        val committed = TransferCheckpointRegistry.commitPartFile("session-clash", "file-clash", existing)

        assertNotNull(committed, "A clashing commit must still place its bytes")
        // Same shape as the route's own pre-scan ("name (1).ext"), so users see one convention.
        assertEquals("photo (1).jpg", committed.name)
        assertEquals("original", existing.readText(), "An existing destination file must never be deleted")
        assertEquals("new!", committed.readText())
    }

    @Test
    fun `failed commit keeps the staging file and checkpoint so the transfer stays resumable`() {
        val part = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, "session-blocked", "file-blocked", "blocked.bin", 8L)
        part.writeBytes(ByteArray(8))

        // A destination whose parent is a FILE cannot be created — the same shape as an
        // unwritable destination (permissions, dead volume): the rename AND the placeholder
        // creation both fail, so the commit must admit it could not place the file.
        val notADirectory = File(tempDir, "not_a_directory")
        notADirectory.writeBytes(ByteArray(0))

        val committed = TransferCheckpointRegistry.commitPartFile(
            "session-blocked",
            "file-blocked",
            File(notADirectory, "blocked.bin"),
        )

        assertNull(committed, "An unplaceable commit must report failure")
        assertTrue(part.exists(), "Staging must survive a failed commit so the upload can resume")
        assertEquals(8L, TransferCheckpointRegistry.getExistingOffset("session-blocked", "file-blocked"))
    }

    @Test
    fun `commitPartFile creates nested parent directories when destFile parent does not exist`() {
        val nestedDest = File(tempDir, "subfolder/nested/reports/quarterly.pdf")
        assertFalse(nestedDest.parentFile!!.exists(), "Parent directories must not exist initially")

        val part = TransferCheckpointRegistry.getOrCreatePartFile(tempDir, "session-nested", "file-nested", "quarterly.pdf", 12L)
        part.writeBytes("confidential".toByteArray())

        val committed = TransferCheckpointRegistry.commitPartFile("session-nested", "file-nested", nestedDest)

        assertNotNull(committed, "Commit to nested non-existent directory must succeed")
        assertTrue(nestedDest.parentFile!!.exists(), "Parent directories must be created by commit")
        assertTrue(committed.exists(), "Committed file must exist")
        assertEquals("confidential", committed.readText())
        assertFalse(part.exists(), "Staging part file must be cleaned up on successful commit")
        assertEquals(0L, TransferCheckpointRegistry.getExistingOffset("session-nested", "file-nested"))
    }
}
