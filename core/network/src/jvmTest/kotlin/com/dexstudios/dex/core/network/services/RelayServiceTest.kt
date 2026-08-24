package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the PC-side relay pipeline: hosted pull-token bookkeeping and the
 * prepare-upload WebSocket frame pushed to the phone.
 */
class RelayServiceTest {

    @Before
    fun setUp() {
        RelayService.hostedFiles.clear()
        RelayService.hostedFileTokens.clear()
        RelayService.hostedFileLastAccess.clear()
        RelayService.relaySessionFiles.clear()
        RelayService.relaySessionAliases.clear()
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
        RelayService.hostedFiles.clear()
        RelayService.hostedFileTokens.clear()
        RelayService.hostedFileLastAccess.clear()
        RelayService.relaySessionFiles.clear()
        RelayService.relaySessionAliases.clear()
    }

    @Test
    fun `trackRelayFile registers path alias and timestamp under the session`() {
        RelayService.trackRelayFile("sess-1", "photo.jpg", "/data/photo.jpg", "Pixel")

        val entries = RelayService.relaySessionFiles["sess-1"]
        assertNotNull(entries)
        assertEquals(listOf<Pair<String, String>>("photo.jpg" to "/data/photo.jpg"), entries.toList())
        assertEquals("Pixel", RelayService.relaySessionAliases["sess-1"])
    }

    @Test
    fun `hostAndPushAsync rejects empty target or empty file list`() = runTest {
        assertFalse(RelayService.hostAndPushAsync("", listOf("a" to null), "alias"))
        assertFalse(RelayService.hostAndPushAsync("fp_target", emptyList(), "alias"))
    }

    @Test
    fun `hostAndPushAsync returns false when none of the files exist`() = runTest {
        assertFalse(
            RelayService.hostAndPushAsync(
                "fp_target",
                listOf("Z:/definitely/not/here.bin" to null),
                "alias",
            ),
        )
        assertTrue(RelayService.hostedFiles.isEmpty())
    }

    @Test
    fun `hostAndPushAsync hosts files with pull tokens and pushes prepare-upload frame`() = runTest {
        val tempDir = Files.createTempDirectory("dex_relay_test").toFile()
        val fileA = Files.write(tempDir.resolve("alpha.bin").toPath(), byteArrayOf(1, 2, 3)).toFile()
        val fileB = Files.write(tempDir.resolve("nested.txt").toPath(), "payload".toByteArray()).toFile()

        val deviceConfig = mockk<DeviceConfig> { every { this@mockk.fingerprint } returns "" }
        startKoin { modules(module { single { deviceConfig } }) }

        mockkObject(WebSocketConnectionManager)
        val jsonSlot = slot<String>()
        coEvery {
            WebSocketConnectionManager.sendRequest("fp_phone", capture(jsonSlot))
        } returns true

        val delivered = RelayService.hostAndPushAsync(
            targetFingerprint = "fp_phone",
            files = listOf(fileA.absolutePath to null, fileB.absolutePath to "sub/dir/nested.txt"),
            senderAlias = "MyPC",
        )

        assertTrue(delivered)
        coVerify(exactly = 1) { WebSocketConnectionManager.sendRequest(any(), any()) }

        // Frame contract
        val frame = Json.parseToJsonElement(jsonSlot.captured).jsonObject
        assertEquals("prepare-upload", frame["type"]?.toString()?.trim('"'))
        val request = Json.decodeFromJsonElement(
            PrepareUploadRequestDto.serializer(),
            requireNotNull(frame["data"]),
        )
        assertEquals("MyPC", request.info.alias)
        assertEquals("desktop-migration", request.info.fingerprint) // empty local fingerprint falls back
        assertEquals(2, request.files.size)

        // Hosted bookkeeping mirrors the advertised FileDtos
        for ((_, dto) in request.files) {
            assertEquals(RelayService.hostedFiles[dto.id], tempDir.resolve(dto.fileName).absolutePath)
            assertEquals(RelayService.hostedFileTokens[dto.id], dto.token)
            assertNotNull(RelayService.hostedFileLastAccess[dto.id])
        }

        val nestedDto = request.files.values.first { it.fileName == "nested.txt" }
        assertEquals("sub/dir/nested.txt", nestedDto.relativePath)
        val flatDto = request.files.values.first { it.fileName == "alpha.bin" }
        assertEquals(null, flatDto.relativePath)
    }
}
