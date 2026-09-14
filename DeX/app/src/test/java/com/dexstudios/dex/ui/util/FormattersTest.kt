package com.dexstudios.dex.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors

class FormattersTest {

    @Test
    fun resolveMetadataUsesIoDispatcherForEveryProviderCall() {
        val context = mockk<Context>()
        val resolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        val cursor = mockk<Cursor>(relaxed = true)
        val providerThreads = mutableListOf<Thread>()
        every { context.contentResolver } returns resolver
        every { uri.scheme } returns "content"
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 1
        every { cursor.getString(0) } returns "photo.jpg"
        every { cursor.getLong(1) } returns 2048L
        every { resolver.query(uri, any<Array<String>>(), null, null, null) } answers {
            providerThreads.add(Thread.currentThread())
            cursor
        }
        every { resolver.getType(uri) } answers {
            providerThreads.add(Thread.currentThread())
            "image/jpeg"
        }
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            val ioThread = executor.submit<Thread> { Thread.currentThread() }.get()
            val metadata = runBlocking { Formatters.resolveMetadata(context, uri, dispatcher) }
            assertEquals(Formatters.UriMetadata("photo.jpg", "image/jpeg", 2048L), metadata)
            assertEquals(listOf(ioThread, ioThread, ioThread), providerThreads)
        }
        verify(exactly = 2) { cursor.close() }
    }


    @Test
    fun formatBytesHandlesAllRanges() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("0 B", Formatters.formatBytes(-50L))
        assertEquals("500 B", Formatters.formatBytes(500L))
        assertEquals("1.0 KB", Formatters.formatBytes(1024L))
        assertEquals("1.5 KB", Formatters.formatBytes(1536L))
        assertEquals("1.0 MB", Formatters.formatBytes(1024L * 1024L))
        assertEquals("2.5 MB", Formatters.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", Formatters.formatBytes(1024L * 1024L * 1024L))
        assertEquals("1.0 TB", Formatters.formatBytes(1024L * 1024L * 1024L * 1024L))
    }

    @Test
    fun formatDurationHandlesMinutesAndSeconds() {
        assertEquals("0:00", Formatters.formatDuration(0L))
        assertEquals("0:00", Formatters.formatDuration(-10L))
        assertEquals("0:05", Formatters.formatDuration(5000L))
        assertEquals("0:59", Formatters.formatDuration(59000L))
        assertEquals("1:00", Formatters.formatDuration(60000L))
        assertEquals("2:35", Formatters.formatDuration(155000L))
    }

    @Test
    fun formatSpeedAddsPerSecondUnit() {
        assertEquals("0 B/s", Formatters.formatSpeed(0L))
        assertEquals("1.0 MB/s", Formatters.formatSpeed(1024L * 1024L))
    }
}
