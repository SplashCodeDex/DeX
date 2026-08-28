package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
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
import kotlinx.serialization.json.decodeFromJsonElement
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
 * Guards the PC-side relay pipeline: hosted pull-token bookkeeping, the prepare-upload
 * WebSocket frame pushed to the phone (trusted sessions only), pull-completion callbacks,
 * and the A->PC->B relay staging registry.
 */
class RelayServiceTest {

    @Before
    fun setUp() {
        RelayService.hostedFiles.clear()
        RelayService.hostedFileTokens.clear()
        RelayService.hostedFileLastAccess.clear()
        RelayService.relaySessionFiles.clear()
        RelayService.relaySessionAliases.clear()
        RelayService.relaySessionExpected.clear()
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
        RelayService.relaySessionExpected.clear()
    }

    private fun startKoinWithDeviceConfig(fingerprint: String = "") {
        val deviceConfig = mockk<DeviceConfig> { every { this@mockk.fingerprint } returns fingerprint }
        startKoin { modules(module { single { deviceConfig } }) }
    }

    private fun stubTrustedSession(fingerprint: String) {
        mockkObject(WebSocketConnectionManager)
        every { WebSocketConnectionManager.isTrusted(fingerprint) } returns true
        coEvery { WebSocketConnectionManager.sendToTrusted(any(), any()) } returns true
    }

    @Test
    fun `trackRelayFile registers path alias and timestamp under the session`() {
        RelayService.trackRelayFile("sess-1", "photo.jpg", "/data/photo.jpg", "Pixel", relativePath = "DCIM/photo.jpg")

        val entries = RelayService.relaySessionFiles["sess-1"]
        assertNotNull(entries)
        assertEquals(listOf(RelayReceivedFile("photo.jpg", "/data/photo.jpg", "DCIM/photo.jpg")), entries.toList())
        assertEquals("Pixel", RelayService.relaySessionAliases["sess-1"])
    }

    @Test
    fun `hostAndPushAsync rejects empty target or empty file list`() = runTest {
        assertFalse(RelayService.hostAndPushAsync("", listOf("a" to null), "alias"))
        assertFalse(RelayService.hostAndPushAsync("fp_target", emptyList(), "alias"))
    }

    @Test
    fun `hostAndPushAsync refuses untrusted sessions so prompts never leak to strangers`() = runTest {
        startKoinWithDeviceConfig()
        mockkObject(WebSocketConnectionManager)
        every { WebSocketConnectionManager.isTrusted("stranger-fp") } returns false

        val delivered = RelayService.hostAndPushAsync("stranger-fp", listOf("/tmp/x" to null), "alias")
        assertFalse(delivered)
        assertTrue(RelayService.hostedFiles.isEmpty())
    }

    @Test
    fun `hostAndPushAsync returns false when none of the files exist`() = runTest {
        startKoinWithDeviceConfig()
        stubTrustedSession("fp_phone")

        assertFalse(
            RelayService.hostAndPushAsync(
                "fp_phone",
                listOf("Z:/definitely/not/here.bin" to null),
                "alias",
            ),
        )
        assertTrue(RelayService.hostedFiles.isEmpty())
    }

    @Test
    fun `hostAndPushAsync hosts files with pull tokens and pushes prepare-upload frame`() = runTest {
        startKoinWithDeviceConfig()
        stubTrustedSession("fp_phone")
        val tempDir = Files.createTempDirectory("dex_relay_test").toFile()
        val fileA = Files.write(tempDir.resolve("alpha.bin").toPath(), byteArrayOf(1, 2, 3)).toFile()
        val fileB = Files.write(tempDir.resolve("nested.txt").toPath(), "payload".toByteArray()).toFile()

        val jsonSlot = slot<String>()
        coEvery { WebSocketConnectionManager.sendToTrusted("fp_phone", capture(jsonSlot)) } returns true

        val delivered = RelayService.hostAndPushAsync(
            targetFingerprint = "fp_phone",
            files = listOf(fileA.absolutePath to null, fileB.absolutePath to "sub/dir/nested.txt"),
            senderAlias = "MyPC",
        )

        assertTrue(delivered)
        coVerify(exactly = 1) { WebSocketConnectionManager.sendToTrusted(any(), any()) }

        // Frame contract
        val frame = Json.parseToJsonElement(jsonSlot.captured).jsonObject
        assertEquals("prepare-upload", frame["type"]?.toString()?.trim('"'))
        val request = Json.decodeFromJsonElement(
            PrepareUploadRequestDto.serializer(),
            requireNotNull(frame["data"]),
        )
        assertEquals("MyPC", request.info.alias)
        assertEquals("desktop-migration", request.info.fingerprint) // empty local fingerprint falls back
        assertTrue(request.info.download) // the desktop hosts the receiver; senders may push back
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

    @Test
    fun `push completes only after every hosted file was pulled`() = runTest {
        startKoinWithDeviceConfig()
        stubTrustedSession("fp_phone")
        val tempDir = Files.createTempDirectory("dex_push_done").toFile()
        val f1 = Files.write(tempDir.resolve("one.bin").toPath(), byteArrayOf(1)).toFile()
        val f2 = Files.write(tempDir.resolve("two.bin").toPath(), byteArrayOf(2)).toFile()

        var completed = false
        var expired = false
        val delivered = RelayService.hostAndPushAsync(
            targetFingerprint = "fp_phone",
            files = listOf(f1.absolutePath to null, f2.absolutePath to null),
            senderAlias = "MyPC",
            onCompleted = { completed = true },
            onExpired = { expired = true },
        )
        assertTrue(delivered)

        val ids = RelayService.hostedFiles.keys.toList()
        assertEquals(2, ids.size)

        // First pull alone must NOT complete the push
        RelayService.markPulled(ids[0])
        Thread.sleep(150)
        assertFalse(completed)

        // Second pull settles it — completion runs on the service scope
        RelayService.markPulled(ids[1])
        Thread.sleep(300)
        assertTrue(completed)
        assertFalse(expired)
    }

    @Test
    fun `failed prompt delivery expires the push immediately`() = runTest {
        startKoinWithDeviceConfig()
        mockkObject(WebSocketConnectionManager)
        every { WebSocketConnectionManager.isTrusted("fp_offline") } returns true
        coEvery { WebSocketConnectionManager.sendToTrusted("fp_offline", any()) } returns false

        val tempFile = Files.createTempFile("dex_push_fail", ".bin").toFile()
        var expired = false
        val delivered = RelayService.hostAndPushAsync(
            targetFingerprint = "fp_offline",
            files = listOf(tempFile.absolutePath to null),
            senderAlias = "MyPC",
            onExpired = { expired = true },
        )
        assertFalse(delivered)
        Thread.sleep(300)
        assertTrue(expired, "expired=$expired delivered=$delivered")
        assertTrue(RelayService.hostedFiles.isEmpty())
    }

    @Test
    fun `markPulled refreshes sliding TTL bookkeeping`() = runTest {
        RelayService.hostedFiles["file-x"] = "/tmp/whatever"
        RelayService.hostedFileTokens["file-x"] = "tok"
        RelayService.hostedFileLastAccess.clear()

        RelayService.markPulled("file-x")

        assertTrue(RelayService.hostedFileLastAccess.containsKey("file-x"))
    }

    @Test
    fun `relayUploadedSession delivers the prompt even after the upload session record was removed`() = runTest {
        // Regression: ShareRoutes.finishIncomingSession removes activeUploadSessions[sessionId]
        // the moment the LAST file lands, while the sender's relay-transfer request arrives
        // only AFTER its uploads complete. The expected count must come from the
        // prepare-time record, not the (already dead) session entry.
        startKoinWithDeviceConfig()
        stubTrustedSession("fp_target")
        val tempDir = Files.createTempDirectory("dex_relay").toFile()
        val staged = Files.write(tempDir.resolve("a.bin").toPath(), byteArrayOf(1)).toFile()

        // prepare-upload recorded the expectation; the file arrived; the session record died
        RelayService.trackRelayExpected("sess-relay", 1)
        RelayService.trackRelayFile("sess-relay", "a.bin", staged.absolutePath, "Pixel")

        val delivered = RelayService.relayUploadedSession("sess-relay", "fp_target")
        assertTrue(delivered, "relay-transfer must be honored after the upload session record was removed")
    }

    @Test
    fun `relayUploadedSession fails closed when no expected count was ever recorded`() = runTest {
        assertFalse(RelayService.relayUploadedSession("ghost-session", "fp_target"))
    }
}
