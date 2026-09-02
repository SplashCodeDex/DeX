package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
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
    private val fileShareManager = mockk<FileShareManager>(relaxed = true)

    private val pairPromptJson = """
        {"type":"pair-prompt","data":{"alias":"PC-1","fingerprint":"pc_fp","pin":"12345","token":"tok123"}}
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
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", DeXPorts.HTTPS)

        verify { notificationHelper.showPairingRequestNotification("PC-1") }

        val info = AuthState.incomingPairRequest.value
        assertNotNull(info)
        info!!.deferred.complete("12345")
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("true", accepted)
        assertTrue(AuthState.pairedFingerprints.contains("pc_fp"))
        assertEquals("tok123", AuthState.pairedTokens["pc_fp"])
        assertNull("Request must be cleared after completion", AuthState.incomingPairRequest.value)
    }

    @Test
    fun `pair-prompt with empty pin sends rejected response and does not save`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", DeXPorts.HTTPS)

        AuthState.incomingPairRequest.value!!.deferred.complete("")
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("false", accepted)
        assertFalse(AuthState.pairedFingerprints.contains("pc_fp"))
        assertFalse(AuthState.pairedTokens.containsKey("pc_fp"))
    }

    @Test
    fun `pair-prompt times out after 60 seconds and sends rejected response`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
        var sent: String? = null
        handler.onSendMessage = { sent = it }

        handler.handleMessage(pairPromptJson, "192.168.1.10", DeXPorts.HTTPS)

        testDispatcher.scheduler.advanceTimeBy(61_000)
        testDispatcher.scheduler.advanceUntilIdle()

        val accepted = Json.parseToJsonElement(sent!!).jsonObject["data"]!!.jsonObject["accepted"]!!.jsonPrimitive.content
        assertEquals("false", accepted)
        assertFalse(AuthState.pairedFingerprints.contains("pc_fp"))
    }

    @Test
    fun `duplicate pair-prompt while one is pending is ignored`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)

        handler.handleMessage(pairPromptJson, "192.168.1.10", DeXPorts.HTTPS)
        handler.handleMessage(pairPromptJson, "192.168.1.10", DeXPorts.HTTPS)

        verify(exactly = 1) { notificationHelper.showPairingRequestNotification("PC-1") }
        assertNotNull(AuthState.incomingPairRequest.value)
    }

    @Test
    fun `public-address message auto-fills blank WAN config`() = runTest(testDispatcher) {
        val mockConfig = mockk<DeviceConfig>(relaxed = true)
        val handler = MessageHandler(mockConfig, mockContext, notificationHelper, fileShareManager)

        handler.handleMessage("""{"type":"public-address","data":{"address":"203.0.113.5"}}""", "192.168.1.10", DeXPorts.HTTPS)

        verify { mockConfig.setPublicAddress("203.0.113.5") }
    }

    @Test
    fun `public-address message never overwrites manual WAN config`() = runTest(testDispatcher) {
        val mockConfig = mockk<DeviceConfig>(relaxed = true)
        every { mockConfig.publicAddress } returns "mypc.dyndns.org"
        val handler = MessageHandler(mockConfig, mockContext, notificationHelper, fileShareManager)

        handler.handleMessage("""{"type":"public-address","data":{"address":"203.0.113.5"}}""", "192.168.1.10", DeXPorts.HTTPS)

        verify(exactly = 0) { mockConfig.setPublicAddress(any()) }
    }

    @Test
    fun `set-clipboard writes text to the phone clipboard`() = runTest(testDispatcher) {
        // android.jar stubs return null from ClipData.newPlainText; mock it statically
        mockkStatic(android.content.ClipData::class)
        val mockClip = mockk<android.content.ClipData>()
        every { android.content.ClipData.newPlainText(any(), any()) } returns mockClip
        val mockItem = mockk<android.content.ClipData.Item>()
        every { mockClip.getItemAt(0) } returns mockItem
        every { mockItem.text } returns "Hello from PC"
        val mockClipboard = mockk<android.content.ClipboardManager>()
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboard

        try {
            val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
            handler.handleMessage("""{"type":"set-clipboard","data":{"text":"Hello from PC"}}""", "192.168.1.10", DeXPorts.HTTPS)

            val clipSlot = slot<android.content.ClipData>()
            verify(exactly = 1) { mockClipboard.setPrimaryClip(capture(clipSlot)) }
            assertEquals("Hello from PC", clipSlot.captured.getItemAt(0).text.toString())
        } finally {
            unmockkStatic(android.content.ClipData::class)
        }
    }
    @Test
    fun `set-clipboard with blank text is ignored`() = runTest(testDispatcher) {
        val mockClipboard = mockk<android.content.ClipboardManager>()
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboard

        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
        handler.handleMessage("""{"type":"set-clipboard","data":{"text":"   "}}""", "192.168.1.10", DeXPorts.HTTPS)

        verify(exactly = 0) { mockClipboard.setPrimaryClip(any()) }
    }

    @Test
    fun `prepare-upload accepted enqueues one batch download for all files from the PC pull server`() = runTest(testDispatcher) {
        mockkObject(TcpDownloadService)
        mockkObject(SafStorage)
        every { TcpDownloadService.downloadBatch(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        every { SafStorage.getDownloadsDexUri(any()) } returns mockk<android.net.Uri>()

        try {
            val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
            val uploadJson = """
                {"type":"prepare-upload","data":{"info":{"alias":"PC-1","version":"2.0","deviceModel":"Windows PC","deviceType":"desktop","fingerprint":"pc_fp","port":48424,"protocol":"https","download":false},"files":{"f1":{"id":"f1","fileName":"photo.jpg","size":1024,"fileType":"image/jpeg"},"f2":{"id":"f2","fileName":"doc.pdf","size":2048,"fileType":"application/pdf"}}}}
            """.trimIndent()

            handler.handleMessage(uploadJson, "192.168.1.10", DeXPorts.HTTPS)

            verify(exactly = 1) {
                TcpDownloadService.downloadBatch(
                    any(),
                    "192.168.1.10",
                    48424,
                    any(),
                    match { files -> files.size == 2 && files.any { it.fileId == "f1" } && files.any { it.fileId == "f2" } },
                    any(),
                    "pc_fp",
                    "PC-1"
                )
            }
        } finally {
            unmockkObject(TcpDownloadService)
            unmockkObject(SafStorage)
        }
    }

    @Test
    fun `sendPinDigitEntered emits pin-digit-entered payload with digitCount`() = runTest(testDispatcher) {
        val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
        var capturedMessage: String? = null
        handler.onSendMessage = { capturedMessage = it }

        handler.sendPinDigitEntered(3)

        assertNotNull(capturedMessage)
        assertTrue(capturedMessage!!.contains("\"type\":\"pin-digit-entered\""))
        assertTrue(capturedMessage!!.contains("\"digitCount\":3"))
    }

    @Test
    fun `relay-offer triggers downloadWanRelay when peer is paired`() = runTest(testDispatcher) {
        mockkObject(TcpDownloadService)
        mockkObject(SafStorage)
        every { SafStorage.getDownloadsDexUri(any()) } returns null
        every { TcpDownloadService.downloadWanRelay(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit

        AuthState.pairedTokens["pc_fp"] = "secret_paired_token"

        try {
            val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
            val offerJson = """
                {"type":"relay-offer","data":{"sessionId":"sess_123","streamToken":"tok_abc","relayUrl":"https://relay.dexstudios.com","fileName":"archive.zip","size":1048576,"fingerprint":"pc_fp","alias":"Desktop PC"}}
            """.trimIndent()

            handler.handleMessage(offerJson, "10.0.0.1", 443)

            verify(exactly = 1) {
                TcpDownloadService.downloadWanRelay(
                    context = any(),
                    sessionId = "sess_123",
                    streamToken = "tok_abc",
                    relayUrl = "https://relay.dexstudios.com",
                    pairedToken = "secret_paired_token",
                    fileName = "archive.zip",
                    totalBytes = 1048576L,
                    destDirUri = any(),
                    fingerprint = "pc_fp",
                    sourceAlias = "Desktop PC"
                )
            }
        } finally {
            unmockkObject(TcpDownloadService)
            unmockkObject(SafStorage)
            AuthState.pairedTokens.remove("pc_fp")
        }
    }

    @Test
    fun `relay-offer is ignored when peer is unpaired`() = runTest(testDispatcher) {
        mockkObject(TcpDownloadService)
        AuthState.pairedTokens.remove("unpaired_fp")

        try {
            val handler = MessageHandler(mockk<DeviceConfig>(relaxed = true), mockContext, notificationHelper, fileShareManager)
            val offerJson = """
                {"type":"relay-offer","data":{"sessionId":"sess_123","streamToken":"tok_abc","relayUrl":"https://relay.dexstudios.com","fileName":"archive.zip","size":1048576,"fingerprint":"unpaired_fp","alias":"Attacker"}}
            """.trimIndent()

            handler.handleMessage(offerJson, "10.0.0.1", 443)

            verify(exactly = 0) {
                TcpDownloadService.downloadWanRelay(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        } finally {
            unmockkObject(TcpDownloadService)
        }
    }
}
