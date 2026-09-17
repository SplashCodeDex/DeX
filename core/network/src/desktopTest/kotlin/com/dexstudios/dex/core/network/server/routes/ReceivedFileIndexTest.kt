package com.dexstudios.dex.core.network.server.routes

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReceivedFileIndexTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("received-file-index-test").toFile()
        ReceivedFileIndex.clear()
    }

    @After
    fun tearDown() {
        ReceivedFileIndex.clear()
        tempDir.deleteRecursively()
    }

    @Test
    fun `findLive returns path when file exists and size matches`() {
        val file = File(tempDir, "photo.jpg").apply { writeBytes(ByteArray(2048) { 42 }) }
        ReceivedFileIndex.record(file, 2048, "hash-2048")

        val found = ReceivedFileIndex.findLive(2048, "hash-2048")
        assertNotNull(found)
        assertEquals(file.absolutePath, found)
    }

    @Test
    fun `findLive returns null and evicts entry when file is deleted`() {
        val file = File(tempDir, "temp.doc").apply { writeBytes(ByteArray(500)) }
        ReceivedFileIndex.record(file, 500, "hash-500")

        file.delete()

        val found = ReceivedFileIndex.findLive(500, "hash-500")
        assertNull(found)
        assertNull(ReceivedFileIndex.find(500, "hash-500"))
    }

    @Test
    fun `findLive returns null and evicts entry when file length on disk has changed`() {
        val file = File(tempDir, "sample.bin").apply { writeBytes(ByteArray(1024) { 1 }) }
        ReceivedFileIndex.record(file, 1024, "hash-1024")

        assertEquals(file.absolutePath, ReceivedFileIndex.findLive(1024, "hash-1024"))

        // File is modified/truncated on disk to different length
        file.writeBytes(ByteArray(512) { 2 })

        val found = ReceivedFileIndex.findLive(1024, "hash-1024")
        assertNull(found, "findLive must return null when file size on disk differs from indexed size")
        assertNull(ReceivedFileIndex.find(1024, "hash-1024"), "Stale entry must be evicted from index")
    }

    @Test
    fun `findLive returns null and evicts entry when path points to a directory`() {
        val dir = File(tempDir, "fake_file").apply { mkdirs() }
        ReceivedFileIndex.record(dir, 100, "hash-dir")

        val found = ReceivedFileIndex.findLive(100, "hash-dir")
        assertNull(found, "Directories must not be treated as live file matches")
        assertNull(ReceivedFileIndex.find(100, "hash-dir"))
    }

    @Test
    fun `record ignores null or empty partialHash and non-positive sizes`() {
        val file = File(tempDir, "empty.txt").apply { writeBytes(ByteArray(0)) }
        ReceivedFileIndex.record(file, 0, "hash-zero")
        assertNull(ReceivedFileIndex.find(0, "hash-zero"))

        ReceivedFileIndex.record(file, 100, null)
        assertNull(ReceivedFileIndex.find(100, null))

        ReceivedFileIndex.record(file, 100, "")
        assertNull(ReceivedFileIndex.find(100, ""))
    }
}
