package com.dexstudios.dex.core.network.server

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReceiveStorageTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dex-receive-storage-test").toFile()
        ReceiveStorage.overridePath = tempDir.absolutePath
    }

    @After
    fun tearDown() {
        ReceiveStorage.overridePath = null
        tempDir.deleteRecursively()
    }

    @Test
    fun `uniqueDest returns original name when no file exists`() {
        val dest = ReceiveStorage.uniqueDest(tempDir, "photo.jpg")
        assertEquals(File(tempDir, "photo.jpg").absolutePath, dest.absolutePath)
    }

    @Test
    fun `uniqueDest disambiguates with sequential index on collision without nesting`() {
        val file0 = File(tempDir, "document.pdf").apply { writeText("original") }
        val file1 = File(tempDir, "document (1).pdf").apply { writeText("copy 1") }

        val dest = ReceiveStorage.uniqueDest(tempDir, "document.pdf")
        assertEquals(File(tempDir, "document (2).pdf").absolutePath, dest.absolutePath)

        // Multiple collisions must never produce nested parentheses like "document (1) (2).pdf"
        assertFalse(dest.name.contains("(1) (2)"))
    }

    @Test
    fun `firstFreeDestination returns dest when unoccupied`() {
        val target = File(tempDir, "notes.txt")
        val resolved = ReceiveStorage.firstFreeDestination(target)
        assertEquals(target.absolutePath, resolved.absolutePath)
    }

    @Test
    fun `firstFreeDestination advances index sequentially on multiple existing files`() {
        File(tempDir, "song.mp3").writeText("1")
        File(tempDir, "song (1).mp3").writeText("2")
        File(tempDir, "song (2).mp3").writeText("3")

        val target = File(tempDir, "song.mp3")
        val resolved = ReceiveStorage.firstFreeDestination(target)
        assertEquals(File(tempDir, "song (3).mp3").absolutePath, resolved.absolutePath)
    }

    @Test
    fun `safeCommit promotes part file when destination is free`() {
        val dest = File(tempDir, "archive.zip")
        val part = File(tempDir, "archive.zip.part").apply { writeText("zip contents") }

        val committed = ReceiveStorage.safeCommit(part, dest)
        assertNotNull(committed)
        assertEquals(dest.absolutePath, committed.absolutePath)
        assertTrue(dest.exists())
        assertEquals("zip contents", dest.readText())
        assertFalse(part.exists(), "part file must be cleaned up on commit")
    }

    @Test
    fun `safeCommit never deletes existing destination and claims sequential name`() {
        val existing = File(tempDir, "data.bin").apply { writeText("pre-existing content") }
        val part = File(tempDir, "data.bin.part.sess1.file1").apply { writeText("new content") }

        val committed = ReceiveStorage.safeCommit(part, existing)
        assertNotNull(committed)
        assertEquals(File(tempDir, "data (1).bin").absolutePath, committed.absolutePath)

        // Existing file must be untouched
        assertTrue(existing.exists())
        assertEquals("pre-existing content", existing.readText())

        // Committed file has new content
        assertTrue(committed.exists())
        assertEquals("new content", committed.readText())
        assertFalse(part.exists())
    }
}
