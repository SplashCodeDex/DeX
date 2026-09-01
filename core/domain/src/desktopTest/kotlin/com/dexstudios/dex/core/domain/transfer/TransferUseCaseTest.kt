package com.dexstudios.dex.core.domain.transfer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 027 contract suite: the registry semantics that the legacy
 * TransferStateMonitor enforced must survive the move to the domain layer verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferUseCaseTest {

    @Test
    fun `register creates a zero-progress entry keyed by session id`() = runTest {
        val useCase = TransferUseCase(backgroundScope)

        useCase.registerSession("s-1", "Pixel", totalFiles = 3, totalBytes = 300L)

        val session = useCase.session("s-1")
        assertEquals("s-1", session?.sessionId)
        assertEquals("Pixel", session?.senderAlias)
        assertEquals(3, session?.totalFiles)
        assertEquals(0, session?.filesReceived)
        assertEquals(0L, session?.bytesReceived)
        assertFalse(session?.isComplete == true, "a fresh session must not be complete")
    }

    @Test
    fun `progress reports replace fields verbatim`() = runTest {
        val useCase = TransferUseCase(backgroundScope)
        useCase.registerSession("s-1", "Pixel", 3, 300L)

        useCase.reportProgress(
            "s-1",
            TransferProgress(
                filesDone = 1,
                totalFiles = 3,
                bytesTransferred = 120L,
                totalBytes = 300L,
                speedBps = 2048L,
                etaSeconds = 90L,
                currentFileName = "photo.jpg",
            ),
        )

        val session = useCase.session("s-1")!!
        assertEquals(1, session.filesReceived)
        assertEquals(120L, session.bytesReceived)
        assertEquals(2048L, session.speedBps)
        assertEquals(90L, session.etaSeconds)
        assertEquals("photo.jpg", session.currentFileName)
        assertFalse(session.isComplete, "in-flight progress must not read as complete")
    }

    @Test
    fun `progress for an unknown session is ignored`() = runTest {
        val useCase = TransferUseCase(backgroundScope)

        useCase.reportProgress("never-registered", TransferProgress(1, 1, 10L, 10L))

        assertNull(useCase.session("never-registered"))
        assertTrue(useCase.sessions.value.isEmpty())
    }

    @Test
    fun `complete marks isComplete and lingers for six seconds then removes`() = runTest {
        val useCase = TransferUseCase(backgroundScope)
        useCase.registerSession("s-1", "Pixel", 2, 200L)

        useCase.completeSession("s-1", filesReceived = 2, totalFiles = 2)
        assertTrue(useCase.session("s-1")?.isComplete == true, "must read complete immediately")

        advanceTimeBy(5_999L)
        assertTrue(useCase.session("s-1") != null, "still within the 6s linger window")

        advanceTimeBy(2L)
        advanceUntilIdle()
        assertNull(useCase.session("s-1"), "must disappear exactly after the linger")
    }

    @Test
    fun `remove deletes immediately with no linger`() = runTest {
        val useCase = TransferUseCase(backgroundScope)
        useCase.registerSession("s-1", "Pixel", 1, 10L)

        useCase.removeSession("s-1")

        assertNull(useCase.session("s-1"))
    }

    @Test
    fun `sessions flow exposes the live map for UI collection`() = runTest {
        val useCase = TransferUseCase(backgroundScope)

        assertEquals(0, useCase.sessions.value.size)
        useCase.registerSession("s-1", "Pixel", 1, 10L)
        useCase.registerSession("s-2", "Watch", 2, 20L)
        assertEquals(setOf("s-1", "s-2"), useCase.sessions.value.keys)

        useCase.removeSession("s-1")
        assertEquals(setOf("s-2"), useCase.sessions.value.keys)
    }

    @Test
    fun `blank session ids are never registered`() = runTest {
        val useCase = TransferUseCase(backgroundScope)

        useCase.registerSession("", "Pixel", 1, 10L)

        assertTrue(useCase.sessions.value.isEmpty())
    }

    @Test
    fun `markComplete never auto-removes - caller owns the entry lifetime`() = runTest {
        val useCase = TransferUseCase(backgroundScope)
        useCase.registerSession("s-1", "Pixel", 2, 200L)

        useCase.markComplete("s-1", 2, 2)

        assertTrue(useCase.session("s-1")?.isComplete == true)
        advanceTimeBy(60_000L)
        advanceUntilIdle()
        assertTrue(useCase.session("s-1") != null, "markComplete must never schedule removal — legacy monitor contract")
    }

    @Test
    fun `direction and status constants freeze the history contract`() = runTest {
        // TransferHistory entries are synced data (plan 031) — these values are contract.
        assertEquals("received", TransferUseCase.DIRECTION_RECEIVED)
        assertEquals("sent", TransferUseCase.DIRECTION_SENT)
        assertEquals("success", TransferUseCase.STATUS_SUCCESS)
        assertEquals("failed", TransferUseCase.STATUS_FAILED)
        assertEquals(6_000L, TransferUseCase.SESSION_LINGER_MS)
    }
}
