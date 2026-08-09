package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageHandlerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockContext = mockk<Context>()
    private val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    private val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val notificationHelper = mockk<NotificationHelper>(relaxed = true)

    private val pairPromptJson = """
        {"type":"pair-prompt","data":{"alias":"PC-1","fingerprint":"pc_fp","pin":"123456","token":"tok123"}}
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(TokenCodec)
        every { TokenCodec.encode(any()) } returns """{"fp":"token"}"""
        every { TokenCodec.decode(any()) } returns emptyMap()
        every { mockContext.getSharedPreferences("dex_device_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putStringSet(any(), any()) } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor

        DeviceManager.init(mockContext)
        AuthState.incomingPairRequest.value = null
        
        TransferState.pendingPrompts.clear()
    }

    @After
    fun tearDown() {
        unmockkObject(TokenCodec)
        Dispatchers.resetMain()
        AuthState.incomingPairRequest.value = null
        
        TransferState.pendingPrompts.clear()
    }

    @Test
    fun `pair-prompt with matching pin sends accepted response and saves fingerprint and token`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", 53317)

        verify { notificationHelper.showPairingRequestNotification("PC-1") }

        val info = AuthState.incomingPairRequest.value
        assertNotNull(info)
        info!!.deferred.complete("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("true", accepted)
        assertTrue(AuthState.pairedFingerprints.contains("pc_fp"))
        assertEquals("tok123", AuthState.pairedTokens["pc_fp"])
        assertNull("Request must be cleared after completion", AuthState.incomingPairRequest.value)
    }

    @Test
    fun `pair-prompt with empty pin sends rejected response and does not save`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", 53317)

        AuthState.incomingPairRequest.value!!.deferred.complete("")
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("false", accepted)
        assertFalse(AuthState.pairedFingerprints.contains("pc_fp"))
        assertFalse(AuthState.pairedTokens.containsKey("pc_fp"))
    }

    @Test
    fun `pair-prompt times out after 60 seconds and sends rejected response`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", 53317)

        testDispatcher.scheduler.advanceTimeBy(61_000)
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("false", accepted)
        assertFalse(AuthState.pairedFingerprints.contains("pc_fp"))
    }

    @Test
    fun `duplicate pair-prompt while one is pending is ignored`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper)

        handler.handleMessage(pairPromptJson, "192.168.1.10", 53317)
        handler.handleMessage(pairPromptJson, "192.168.1.10", 53317)

        verify(exactly = 1) { notificationHelper.showPairingRequestNotification("PC-1") }
        assertNotNull(AuthState.incomingPairRequest.value)
    }

    @Test
    fun `public-address message auto-fills blank WAN config`() = runTest(testDispatcher) {
        val mockConfig = mockk<DeviceConfig>(relaxed = true)
        val handler = MessageHandler(mockConfig, mockContext, notificationHelper)

        handler.handleMessage("""{"type":"public-address","data":{"address":"203.0.113.5"}}""", "192.168.1.10", 53317)

        verify { mockConfig.setPublicAddress("203.0.113.5") }
    }

    @Test
    fun `public-address message never overwrites manual WAN config`() = runTest(testDispatcher) {
        val mockConfig = mockk<DeviceConfig>(relaxed = true)
        every { mockConfig.publicAddress } returns "mypc.dyndns.org"
        val handler = MessageHandler(mockConfig, mockContext, notificationHelper)

        handler.handleMessage("""{"type":"public-address","data":{"address":"203.0.113.5"}}""", "192.168.1.10", 53317)

        verify(exactly = 0) { mockConfig.setPublicAddress(any()) }
    }

    @Test
    fun `prepare-upload accepted enqueues one batch download for all files from the PC pull server`() = runTest(testDispatcher) {
        mockkObject(TcpDownloadService)
        mockkObject(SafStorage)
        every { TcpDownloadService.downloadBatch(any(), any(), any(), any(), any(), any()) } returns Unit
        every { SafStorage.getDownloadsDexUri(any()) } returns mockk<android.net.Uri>()

        try {
            val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper)
            val uploadJson = """
                {"type":"prepare-upload","data":{"info":{"alias":"PC-1","version":"2.0","deviceModel":"Windows PC","deviceType":"desktop","fingerprint":"pc_fp","port":53317,"protocol":"https","download":false},"files":{"f1":{"id":"f1","fileName":"photo.jpg","size":1024,"fileType":"image/jpeg"},"f2":{"id":"f2","fileName":"doc.pdf","size":2048,"fileType":"application/pdf"}}}}
            """.trimIndent()

            handler.handleMessage(uploadJson, "192.168.1.10", 53317)

            val sessionSlot = slot<String>()
            verify { notificationHelper.showIncomingFileNotification(capture(sessionSlot), any(), 2) }

            val capturedSession = sessionSlot.captured
            TransferState.pendingPrompts[capturedSession]!!.complete(true)

            // The download coroutine runs on the real IO dispatcher, so poll instead of sleeping
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && TransferState.pendingPrompts.isNotEmpty()) {
                Thread.sleep(100)
            }
            assertTrue("coroutine should have consumed the pending prompt", TransferState.pendingPrompts.isEmpty())

            verify(timeout = 10_000, exactly = 1) {
                TcpDownloadService.downloadBatch(any(), "192.168.1.10", 53317, 53319, match { files -> files.size == 2 && files.any { it.fileId == "f1" } && files.any { it.fileId == "f2" } },
                    any()
                )
            }
            assertNull(TransferState.pendingPrompts[sessionSlot.captured])
        } finally {
            unmockkObject(TcpDownloadService)
            unmockkObject(SafStorage)
        }
    }
}
