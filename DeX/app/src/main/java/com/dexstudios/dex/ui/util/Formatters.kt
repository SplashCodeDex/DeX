package com.dexstudios.dex.ui.util

import java.util.Locale

/**
 * Centralized formatting utilities for byte sizes, durations, and speeds across the DeX UI (Plan 024 Phase 2).
 */
object Formatters {

    /**
     * Formats byte count into a human-readable string with units (B, KB, MB, GB, TB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            bytes >= tb -> String.format(Locale.US, "%.1f TB", bytes / tb)
            bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    /**
     * Formats duration in milliseconds into "m:ss" format.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /**
     * Formats transfer speed in bytes per second.
     */
    fun formatSpeed(speedBps: Long): String {
        if (speedBps <= 0L) return "0 B/s"
        return "${formatBytes(speedBps)}/s"
    }
}
