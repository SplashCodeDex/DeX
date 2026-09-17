package com.dexstudios.dex.desktop.transfer

import com.dexstudios.dex.core.network.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFileSendRetryTest {
    @AfterTest
    fun tearDown() = unmockkAll()

    private fun client(status: Int): ClientEngine {
        mockkObject(TransferHistoryRecorder)
        every { TransferHistoryRecorder.recordFailed(name = any(), size = any(), direction = any(), peerDevice = any()) } just Runs
        val client = mockk<ClientEngine>(relaxed = true)
        every { client.uploadState } returns MutableStateFlow(UploadState()).asStateFlow()
        coEvery { client.prepareUpload(any(), any(), any(), any()) } coAnswers {
            val request = thirdArg<PrepareUploadRequestDto>()
            ClientEngine.PrepareResult(PrepareUploadResponseDto("session", request.files.mapValues { "token" }), 200)
        }
        coEvery { client.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns UploadOutcome(false, status)
        return client
    }

    private fun target(): DiscoveredDevice = mockk {
        every { ip } returns "127.0.0.1"
        every { info } returns RegisterDto(
            alias = "PC",
            version = "2.0",
            deviceModel = "PC",
            deviceType = "desktop",
            fingerprint = "peer",
            port = 48424,
            protocol = "https",
            download = true,
        )
    }

    @Test
    fun allUploadTransportFailuresRetryThenRequestFallback() = runTest {
        val client = client(-1)
        val discovery = mockk<DiscoveryEngine>(relaxed = true)
        val service = DesktopFileSendService(client, discovery, mockk(relaxed = true))
        val file = Files.createTempFile("dex-retry", ".bin").toFile()
        try {
            assertFalse(service.runSession(listOf(file to null), target()))
            coVerify(exactly = 4) { client.prepareUpload(any(), any(), any(), any()) }
            coVerify(exactly = 4) { client.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { client.finishUpload(any(), any()) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun httpRejectionDoesNotRetryOrRequestRelayFallback() = runTest {
        val client = client(403)
        val service = DesktopFileSendService(client, mockk(relaxed = true), mockk(relaxed = true))
        val file = Files.createTempFile("dex-rejected", ".bin").toFile()
        try {
            assertTrue(service.runSession(listOf(file to null), target()))
            coVerify(exactly = 1) { client.prepareUpload(any(), any(), any(), any()) }
            verify(exactly = 1) { client.finishUpload(0, 1) }
        } finally {
            file.delete()
        }
    }
}
