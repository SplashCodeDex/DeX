package com.dexstudios.dex.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

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
