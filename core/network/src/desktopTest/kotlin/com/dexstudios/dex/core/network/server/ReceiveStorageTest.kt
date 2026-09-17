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

    @Test
    fun `uniqueDest sanitizes Windows reserved device names`() {
        val reservedNames = listOf(
            "con.txt", "CON.TXT", "aux.json", "AUX", "nul", "NUL.log",
            "prn.dat", "PRN", "com1.txt", "COM5.bin", "lpt1.pdf", "LPT9",
            "con.tar.gz", "nul.part.1",
        )
        for (name in reservedNames) {
            val dest = ReceiveStorage.uniqueDest(tempDir, name)
            assertFalse(
                dest.name.startsWith("con.", ignoreCase = true) ||
                    dest.name.equals("con", ignoreCase = true) ||
                    dest.name.startsWith("aux.", ignoreCase = true) ||
                    dest.name.equals("aux", ignoreCase = true) ||
                    dest.name.startsWith("nul.", ignoreCase = true) ||
                    dest.name.equals("nul", ignoreCase = true) ||
                    dest.name.startsWith("prn.", ignoreCase = true) ||
                    dest.name.equals("prn", ignoreCase = true) ||
                    dest.name.matches(Regex("^(?i)(com[1-9]|lpt[1-9])(\\..*)?$")),
                "Destination name '${dest.name}' must not be a Windows reserved device name for input '$name'",
            )
            // It must be safe to create on Windows filesystem without exception
            dest.writeText("safe content")
            assertTrue(dest.exists())
            dest.delete()
        }
    }

    @Test
    fun `uniqueDest preserves legitimate names that start with reserved letters`() {
        val safeNames = listOf(
            "contact.txt",
            "auxiliary.bin",
            "null.txt",
            "comedy.mp4",
            "prno.dat",
            "lpt10.log",
            "com10.zip",
        )
        for (name in safeNames) {
            val dest = ReceiveStorage.uniqueDest(tempDir, name)
            assertEquals(name, dest.name, "Legitimate name '$name' must not be altered")
        }
    }

    @Test
    fun `uniqueDest trims trailing spaces and dots to prevent Win32 path failure`() {
        val dest1 = ReceiveStorage.uniqueDest(tempDir, "document.pdf. ")
        assertEquals("document.pdf", dest1.name)

        val dest2 = ReceiveStorage.uniqueDest(tempDir, "archive.tar.gz...")
        assertEquals("archive.tar.gz", dest2.name)

        val dest3 = ReceiveStorage.uniqueDest(tempDir, "   ")
        assertEquals("unnamed_file", dest3.name)

        val dest4 = ReceiveStorage.uniqueDest(tempDir, "....")
        assertEquals("unnamed_file", dest4.name)
    }

    @Test
    fun `uniqueDest sanitizes relativePath segments and avoids reserved directory names`() {
        val dest = ReceiveStorage.uniqueDest(tempDir, "data.json", "nested/aux/con.txt")
        assertFalse(dest.absolutePath.contains("${File.separator}aux${File.separator}", ignoreCase = true))
        assertFalse(dest.name.equals("con.txt", ignoreCase = true))
        assertTrue(dest.name.equals("_con.txt", ignoreCase = true))

        dest.parentFile?.mkdirs()
        dest.writeText("nested safe")
        assertTrue(dest.exists())
        dest.delete()
    }

    @Test
    fun `safeCommit succeeds on Windows with reserved device name`() {
        val dest = ReceiveStorage.uniqueDest(tempDir, "con.txt")
        val part = File(tempDir, "con.txt.part.test.1").apply { writeText("device content") }

        val committed = ReceiveStorage.safeCommit(part, dest)
        assertNotNull(committed)
        assertTrue(committed.exists())
        assertEquals("_con.txt", committed.name)
        assertEquals("device content", committed.readText())
        assertFalse(part.exists())
        committed.delete()
    }

    @Test
    fun `safeCommit creates parent directory when dest is in nested subfolder`() {
        val subDir = File(tempDir, "nested/folder/structure")
        assertFalse(subDir.exists())
        val dest = File(subDir, "report.pdf")
        val part = File(tempDir, "report.pdf.part").apply { writeText("report data") }

        val committed = ReceiveStorage.safeCommit(part, dest)
        assertNotNull(committed, "safeCommit must not return null when parent directory does not exist")
        assertTrue(committed.exists())
        assertEquals("report data", committed.readText())
        assertFalse(part.exists())
    }
}
