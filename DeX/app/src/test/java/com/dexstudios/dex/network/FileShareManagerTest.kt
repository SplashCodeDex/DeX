package com.dexstudios.dex.network

import android.content.Context
import com.dexstudios.dex.network.PullFileDto2
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileShareManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)
    private val deviceConfig = mockk<DeviceConfig>(relaxed = true)
    private val client = mockk<ClientEngine>(relaxed = true)
    private val wsService = mockk<WebSocketClientService>(relaxed = true)
    private val discoveryEngine = mockk<DiscoveryEngine>(relaxed = true)

    private lateinit var manager: FileShareManager

    @Before
    fun setUp() {
        mockkObject(PullForegroundService)
        manager = FileShareManager(
            deviceConfig = deviceConfig,
            client = client,
            context = context,
            wsServiceOverride = wsService,
            discoveryEngineOverride = discoveryEngine,
            scope = testScope,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(PullForegroundService)
    }

    @Test
    fun `resolveName falls back to unnamed_file when both query and DTO names are blank`() {
        val blankDto = PullFileDto2(name = "   ", uri = "content://media/external/files/999", size = 100)
        val resolved = manager.resolveName(blankDto)
        assertEquals("unnamed_file", resolved)
    }

    @Test
    fun `resolveName preserves valid non-blank DTO name when content query returns null`() {
        val validDto = PullFileDto2(name = "my_notes.txt", uri = "content://media/external/files/1000", size = 200)
        val resolved = manager.resolveName(validDto)
        assertEquals("my_notes.txt", resolved)
    }

    @Test
    fun `resolveName strips path traversal and directory separators`() {
        val traversalDto = PullFileDto2(name = "../../etc/shadow", uri = "content://media/external/files/1001", size = 50)
        val resolved = manager.resolveName(traversalDto)
        assertEquals("shadow", resolved)

        val winTraversalDto = PullFileDto2(name = "..\\..\\Windows\\System32\\cmd.exe", uri = "content://media/external/files/1002", size = 50)
        val winResolved = manager.resolveName(winTraversalDto)
        assertEquals("cmd.exe", winResolved)
    }

    @Test
    fun `sanitizeName handles empty strings, slashes, and illegal characters`() {
        assertEquals("unnamed_file", manager.sanitizeName(""))
        assertEquals("unnamed_file", manager.sanitizeName("   "))
        assertEquals("unnamed_file", manager.sanitizeName("///"))
        assertEquals("unnamed_file", manager.sanitizeName("\\\\\\"))
        assertEquals("report_2026.pdf", manager.sanitizeName("report:2026.pdf"))
        assertEquals("file_test", manager.sanitizeName("file*test..."))
        assertEquals("photo.jpg", manager.sanitizeName("/storage/emulated/0/DCIM/photo.jpg"))
    }

    @Test
    fun `handleRequest with pull-cancel records cancellation`() {
        val cancelData = buildJsonObject {
            put(ProtocolKeys.REQUEST_ID, "req-cancel-123")
        }
        manager.handleRequest(ProtocolKeys.PULL_CANCEL, cancelData)
        assertTrue(manager.isCancelled("req-cancel-123"))
    }
}
