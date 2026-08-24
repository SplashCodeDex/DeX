package com.dexstudios.dex.core.network.server

import io.ktor.websocket.Frame
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runtime trust-gate semantics of the live session registry: handshake trust, PIN-proof
 * upgrade, peer-initiated downgrade, and that sends respect the gate.
 */
class WebSocketConnectionManagerTest {

    private fun freshManager() = WebSocketConnectionManager

    @Test
    fun `register refuses to replace an active fingerprint slot`() {
        val manager = freshManager()
        val first = mockk<io.ktor.websocket.WebSocketSession>(relaxed = true)
        val second = mockk<io.ktor.websocket.WebSocketSession>(relaxed = true)

        assertTrue(manager.register("fp-a", first, trusted = false))
        assertFalse(manager.register("fp-a", second, trusted = true), "hijack must be refused")
        assertEquals(first, manager.holderOf("fp-a")?.session)
        manager.unregister("fp-a")
    }

    @Test
    fun `markTrusted upgrades a registered session and records identity`() {
        val manager = freshManager()
        val session = mockk<io.ktor.websocket.WebSocketSession>(relaxed = true)
        manager.register("fp-b", session, trusted = false, identityToken = null)
        assertFalse(manager.isTrusted("fp-b"))

        manager.markTrusted("fp-b", "sub-123")
        assertTrue(manager.isTrusted("fp-b"))
        assertEquals("sub-123", manager.holderOf("fp-b")?.identityToken)
        manager.unregister("fp-b")
    }

    @Test
    fun `markUntrusted revokes prompts and clears recorded identity`() = runTest {
        val manager = freshManager()
        val session = mockk<io.ktor.websocket.WebSocketSession>(relaxed = true)
        manager.register("fp-c", session, trusted = true, identityToken = "sub-xyz")

        manager.markUntrusted("fp-c")
        assertFalse(manager.isTrusted("fp-c"), "downgraded session must lose trusted flag")
        assertNull(manager.holderOf("fp-c")?.identityToken, "identity proof must die with the downgrade")
        assertFalse(manager.sendToTrusted("fp-c", "{}"), "sendToTrusted must refuse downgraded sessions")
        assertTrue(manager.sendRequest("fp-c", "{}"), "pairing channel stays open after downgrade")
        manager.unregister("fp-c")
    }

    @Test
    fun `unregistered fingerprints are unknown`() = runTest {
        val manager = freshManager()
        assertFalse(manager.isConnected("never-registered"))
        assertFalse(manager.sendRequest("never-registered", "{}"))
    }

    @Test
    fun `DexRequestStore completes only the matching deferred`() = runTest {
        val deferred = DexRequestStore.createRequest("req-1")
        DexRequestStore.completeRequest("req-1", JsonObject(emptyMap()))
        assertTrue(deferred.isCompleted)

        val cancelled = DexRequestStore.createRequest("req-2")
        DexRequestStore.cancelRequest("req-2")
        assertTrue(cancelled.isCancelled)
    }
}
