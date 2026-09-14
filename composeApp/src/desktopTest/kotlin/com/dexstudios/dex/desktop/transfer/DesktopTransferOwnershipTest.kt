package com.dexstudios.dex.desktop.transfer

import com.dexstudios.dex.core.network.*
import com.dexstudios.dex.core.network.services.RelayService
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTransferOwnershipTest {
    @AfterTest fun tearDown() = unmockkAll()

    @Test
    fun waitingTransferRejectsAnotherSendAndCancelKeepsOwnershipUntilCleanup() = runTest {
        val state = MutableStateFlow(UploadState())
        val client = mockk<ClientEngine>(relaxed = true) { every { uploadState } returns state }
        every { client.updateUploadState(any()) } answers { state.value = firstArg() }
        val discovery = mockk<DiscoveryEngine> {
            every { devices } returns MutableStateFlow(emptyMap<String, DiscoveredDevice>())
        }
        val config = mockk<DeviceConfig>(relaxed = true)
        mockkObject(RelayService)
        val gate = CompletableDeferred<Boolean>()
        coEvery { RelayService.hostAndPushAsync(any(), any(), any(), any(), any(), true) } coAnswers { gate.await() }
        val service = DesktopFileSendService(client, discovery, config, backgroundScope)
        val file = Files.createTempFile("dex-owner", ".bin").toFile()
        try {
            service.sendFiles(listOf(file), "phone")
            runCurrent()
            assertTrue(service.isSessionActive())
            assertTrue(state.value.isUploading)
            service.sendFiles(listOf(file), "other-phone")
            runCurrent()
            coVerify(exactly = 1) { RelayService.hostAndPushAsync(any(), any(), any(), any(), any(), true) }
            assertTrue(state.value.isUploading)
            service.cancelActiveSession()
            assertTrue(service.isSessionActive())
            runCurrent()
            assertFalse(service.isSessionActive())
            assertFalse(state.value.isUploading)
            gate.complete(true)
            runCurrent()
            verify(exactly = 0) { client.finishUpload(any(), any()) }
        } finally {
            service.cancelActiveSession()
            file.delete()
        }
    }
}
