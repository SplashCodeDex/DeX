package com.dexstudios.dex.network

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resume logic for direct phone-to-phone transfers: per-file byte progress must
 * survive connection drops (resume), be cleared on completion, and be pruned after
 * the 10-minute accepted-session window.
 */
class PunchResumeStateTest {

    private fun entry(received: Long, size: Long): ResumeEntry =
        ResumeEntry(mockk<Uri>(), received, size)

    @Test
    fun `mapFor returns the same map for the same session`() {
        val a = PunchResumeState.mapFor("s1")
        val b = PunchResumeState.mapFor("s1")
        assertSame(a, b)
        PunchResumeState.complete("s1")
    }

    @Test
    fun `mapFor returns independent maps per session`() {
        val a = PunchResumeState.mapFor("sA")
        val b = PunchResumeState.mapFor("sB")
        assertNotSame(a, b)
        PunchResumeState.complete("sA")
        PunchResumeState.complete("sB")
    }

    @Test
    fun `resume progress accumulates per file`() {
        val map = PunchResumeState.mapFor("resume1")
        map["file1"] = entry(0L, 1000L)
        map["file1"]!!.received = 400L
        map["file1"]!!.received = 1000L
        assertEquals(1000L, map["file1"]!!.received)
        assertTrue(map["file1"]!!.received >= map["file1"]!!.size)
        PunchResumeState.complete("resume1")
    }

    @Test
    fun `isAccepted reflects markAccepted and complete`() {
        assertFalse(PunchResumeState.isAccepted("acc1"))
        PunchResumeState.markAccepted("acc1")
        assertTrue(PunchResumeState.isAccepted("acc1"))
        PunchResumeState.complete("acc1")
        assertFalse(PunchResumeState.isAccepted("acc1"))
    }

    @Test
    fun `complete clears the per-file resume map`() {
        val map = PunchResumeState.mapFor("c1")
        map["f"] = entry(10L, 100L)
        PunchResumeState.complete("c1")
        // A fresh map for the same session id must be empty (state cleared)
        assertTrue(PunchResumeState.mapFor("c1").isEmpty())
    }
}
