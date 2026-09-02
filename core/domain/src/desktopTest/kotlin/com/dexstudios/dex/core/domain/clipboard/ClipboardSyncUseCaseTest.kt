package com.dexstudios.dex.core.domain.clipboard

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan 029 contract suite: the echo guard (the infinite-loop killer), the enable
 * policy, blank-payload handling, and the receive-then-write-then-not-rebroadcast
 * sequence every peer must reproduce.
 */
class ClipboardSyncUseCaseTest {

    /** In-memory clipboard: records writes; returns the last written payload on read. */
    private class FakeAccess(var initial: ClipboardPayload? = null) : ClipboardAccess {
        val writes = ArrayList<ClipboardPayload>()
        var content: ClipboardPayload? = initial

        override suspend fun read(): ClipboardPayload? = content

        override suspend fun write(payload: ClipboardPayload) {
            writes.add(payload)
            content = payload
        }
    }

    private class RecordingSender : ClipboardSender {
        val sent = ArrayList<ClipboardPayload>()
        var reachable: Boolean = true

        override suspend fun send(payload: ClipboardPayload): Boolean {
            if (!reachable) return false
            sent.add(payload)
            return true
        }
    }

    private fun useCase(access: FakeAccess, sender: RecordingSender, enabled: Boolean = true) = ClipboardSyncUseCase(
        access = access,
        sender = sender,
        enabled = { enabled },
        hash = { it.hashCode().toString() },
    )

    @Test
    fun `local changes push to the peer`() = runTest {
        val access = FakeAccess(initial = ClipboardPayload.Text("hello"))
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        assertTrue(sync.onLocalClipboardChanged())
        assertEquals(listOf<ClipboardPayload>(ClipboardPayload.Text("hello")), sender.sent)
    }

    @Test
    fun `disabled sync never sends`() = runTest {
        val access = FakeAccess(initial = ClipboardPayload.Text("hello"))
        val sender = RecordingSender()
        val sync = useCase(access, sender, enabled = false)

        assertFalse(sync.onLocalClipboardChanged())
        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `an empty clipboard is a no-op`() = runTest {
        val access = FakeAccess(initial = null)
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        assertFalse(sync.onLocalClipboardChanged())
        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `received content is written locally but never re-broadcast`() = runTest {
        val access = FakeAccess()
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        // Peer pushes text; we apply it...
        sync.applyRemoteClipboard(ClipboardPayload.Text("from-phone"))

        assertEquals(listOf<ClipboardPayload>(ClipboardPayload.Text("from-phone")), access.writes)

        // ...the platform fires its clipboard-changed event; the echo guard must
        // suppress the bounce-back (the classic two-device infinite copy loop).
        assertFalse(sync.onLocalClipboardChanged(), "received content must never be re-broadcast")
        assertTrue(sender.sent.isEmpty())
    }

    @Test
    fun `a genuinely new local copy still sends after receiving remote content`() = runTest {
        val access = FakeAccess()
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        sync.applyRemoteClipboard(ClipboardPayload.Text("from-phone"))
        access.content = ClipboardPayload.Text("fresh-local-copy") // the user copies something new

        assertTrue(sync.onLocalClipboardChanged())
        assertEquals(listOf<ClipboardPayload>(ClipboardPayload.Text("fresh-local-copy")), sender.sent)
    }

    @Test
    fun `the echo guard is per-content not per-event`() = runTest {
        val access = FakeAccess()
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        sync.applyRemoteClipboard(ClipboardPayload.Text("abc"))
        assertFalse(sync.onLocalClipboardChanged())

        access.content = ClipboardPayload.Text("different")
        assertTrue(sync.onLocalClipboardChanged())

        // Same remote content arrives again — still guarded (idempotent).
        sync.applyRemoteClipboard(ClipboardPayload.Text("abc"))
        assertFalse(sync.onLocalClipboardChanged())
    }

    @Test
    fun `image payloads flow through the same guard`() = runTest {
        val access = FakeAccess()
        val sender = RecordingSender()
        val sync = useCase(access, sender)

        val image = ClipboardPayload.Image("image/png", base64Png = "aGVsbG8=")
        sync.applyRemoteClipboard(image)
        assertFalse(sync.onLocalClipboardChanged(), "received images must not bounce back")

        access.content = ClipboardPayload.Image("image/png", base64Png = "bmV3")
        assertTrue(sync.onLocalClipboardChanged())
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun `unreachable peers report send failure so callers can fall back`() = runTest {
        val access = FakeAccess(initial = ClipboardPayload.Text("hello"))
        val sender = RecordingSender().apply { reachable = false }
        val sync = useCase(access, sender)

        assertFalse(sync.onLocalClipboardChanged(), "delivery failure must surface to the caller")
    }
}
