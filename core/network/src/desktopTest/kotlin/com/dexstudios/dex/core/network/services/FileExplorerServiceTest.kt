package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileExplorerServiceTest {

    private lateinit var service: FileExplorerService
    private val capturedFrames = mutableListOf<String>()

    @Before
    fun setUp() {
        service = FileExplorerService()
        capturedFrames.clear()
    }

    @After
    fun tearDown() {
        WebSocketConnectionManager.connectedFingerprints().forEach { fp ->
            WebSocketConnectionManager.holderOf(fp)?.session?.let {
                WebSocketConnectionManager.unregister(fp, it)
            }
        }
        capturedFrames.clear()
    }

    private fun registerMockSession(fingerprint: String): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true)
        val frameSlot = slot<Frame>()
        coEvery { session.send(capture(frameSlot)) } coAnswers {
            val f = frameSlot.captured
            if (f is Frame.Text) {
                capturedFrames.add(f.readText())
            }
        }
        WebSocketConnectionManager.register(fingerprint, session, trusted = true)
        return session
    }

    @Test
    fun `pullFiles records target fingerprint and request metadata in pullProgress`() = runTest {
        val fp = "target-phone-fp"
        registerMockSession(fp)

        val files = listOf(
            PullFileItem(uri = "content://media/1", name = "photo.jpg", size = 2048L),
            PullFileItem(uri = "content://media/2", name = "video.mp4", size = 1048576L),
        )

        val reqId = service.pullFiles(fp, files)
        assertNotNull(reqId)

        val progress = service.pullProgress.value
        assertEquals(reqId, progress.requestId)
        assertEquals(fp, progress.fingerprint)
        assertTrue(progress.isPulling)
        assertFalse(progress.isDone)
        assertFalse(progress.isCancelled)
        assertEquals(2, progress.totalFiles)
        assertEquals(2048L + 1048576L, progress.totalBytes)

        assertEquals(1, capturedFrames.size)
        val frameObj = Json.parseToJsonElement(capturedFrames.single()).jsonObject
        assertEquals(MessageTypes.PULL_FILES, frameObj["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `cancelPull resolves target fingerprint from active pullProgress when omitted`() = runTest {
        val fp = "target-phone-fp"
        registerMockSession(fp)

        val files = listOf(
            PullFileItem(uri = "content://media/1", name = "document.pdf", size = 5000L),
        )
        val reqId = service.pullFiles(fp, files)
        assertNotNull(reqId)
        capturedFrames.clear()

        // Caller provides empty fingerprint (e.g. from banner callback)
        service.cancelPull(fingerprint = "", requestId = reqId)

        val progress = service.pullProgress.value
        assertFalse(progress.isPulling)
        assertFalse(progress.isDone, "isDone must be false on cancellation to prevent false receive notifications")
        assertTrue(progress.isCancelled)

        assertEquals(1, capturedFrames.size)
        val cancelFrame = Json.parseToJsonElement(capturedFrames.single()).jsonObject
        assertEquals(MessageTypes.PULL_CANCEL, cancelFrame["type"]?.jsonPrimitive?.content)
        val data = cancelFrame["data"]?.jsonObject
        assertEquals(reqId, data?.get(FieldNames.REQUEST_ID)?.jsonPrimitive?.content)
    }

    @Test
    fun `cancelPull with explicit fingerprint dispatches cancel payload and sets isCancelled`() = runTest {
        val fp = "phone-explicit"
        registerMockSession(fp)

        service.cancelPull(fingerprint = fp, requestId = "req-custom-99")

        val progress = service.pullProgress.value
        assertFalse(progress.isPulling)
        assertFalse(progress.isDone)
        assertTrue(progress.isCancelled)

        assertEquals(1, capturedFrames.size)
        val cancelFrame = Json.parseToJsonElement(capturedFrames.single()).jsonObject
        assertEquals(MessageTypes.PULL_CANCEL, cancelFrame["type"]?.jsonPrimitive?.content)
        val data = cancelFrame["data"]?.jsonObject
        assertEquals("req-custom-99", data?.get(FieldNames.REQUEST_ID)?.jsonPrimitive?.content)
    }
}
