package com.dexstudios.dex.network

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID

class UploadWorkerTest {

    private lateinit var clientEngine: ClientEngine
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockParams = mockk<WorkerParameters>(relaxed = true)

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { mockUri.toString() } returns "content://media/external/file/1"
        every { android.net.Uri.parse(any()) } returns mockUri
        io.mockk.mockkStatic("com.dexstudios.dex.network.DeviceUtilsKt")
        every { getDeviceName(any()) } returns "Pixel Phone"
        io.mockk.mockkObject(HashUtils)
        every { HashUtils.computePartialHash(any(), any(), any()) } returns "hash123"
        val mockContentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver

        val mockEngine = MockEngine { respond("{}", HttpStatusCode.OK) }
        clientEngine = ClientEngine(mockEngine)
        startKoin {
            modules(
                module {
                    single { clientEngine }
                    single { mockk<DeviceConfig>(relaxed = true) }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        io.mockk.unmockkStatic(android.net.Uri::class)
        io.mockk.unmockkStatic("com.dexstudios.dex.network.DeviceUtilsKt")
        io.mockk.unmockkObject(HashUtils)
    }



    @Test
    fun `prepareUpload rejection updates UploadState with error and clears activeWorkId`() = runBlocking {
        val mockEngine = MockEngine {
            respond("Forbidden", HttpStatusCode.Forbidden)
        }
        clientEngine = ClientEngine(mockEngine)
        stopKoin()
        startKoin {
            modules(
                module {
                    single { clientEngine }
                    single { mockk<DeviceConfig>(relaxed = true) }
                }
            )
        }

        val workId = UUID.randomUUID()
        clientEngine.activeWorkId = workId
        val inputData = Data.Builder()
            .putString(TransferWorkKeys.IP, "127.0.0.1")
            .putInt(TransferWorkKeys.PORT, 53317)
            .putString(TransferWorkKeys.URIS, "[\"content://media/external/file/1\"]")
            .putString(TransferWorkKeys.TARGET_ALIAS, "TargetPC")
            .build()

        every { mockParams.inputData } returns inputData
        every { mockParams.id } returns workId
        every { mockParams.runAttemptCount } returns 0

        val worker = io.mockk.spyk(UploadWorker(mockContext, mockParams))
        io.mockk.coEvery { worker.setForeground(any()) } returns Unit

        val result = worker.doWork()
        org.junit.Assert.assertTrue("doWork must return failure when prepareUpload is rejected", result is androidx.work.ListenableWorker.Result.Failure)
        assertFalse("isUploading must be false", clientEngine.uploadState.value.isUploading)
        org.junit.Assert.assertNotNull("error must be reported to user when transfer is rejected", clientEngine.uploadState.value.error)
        assertEquals("Transfer rejected: device not authorized", clientEngine.uploadState.value.error)
        assertNull("activeWorkId must be cleared on failure", clientEngine.activeWorkId)
    }

    @Test
    fun `cancellation during upload updates UploadState to cancelled and clears activeWorkId`() = runBlocking {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("prepare-upload")) {
                val requestBytes = (request.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)?.bytes()
                val requestText = requestBytes?.decodeToString() ?: ""
                val fileId = Regex(""""files":\{"([^"]+)"""").find(requestText)?.groupValues?.get(1) ?: "file_1"
                respond(
                    content = """{"sessionId":"s1","files":{"$fileId":"tok_1"}}""",
                    status = HttpStatusCode.OK,
                    headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json")
                )
            } else {
                delay(2000)
                respond("OK", HttpStatusCode.OK)
            }
        }
        clientEngine = ClientEngine(mockEngine)
        stopKoin()
        startKoin {
            modules(
                module {
                    single { clientEngine }
                    single { mockk<DeviceConfig>(relaxed = true) }
                }
            )
        }

        val workId = UUID.randomUUID()
        clientEngine.activeWorkId = workId
        val inputData = Data.Builder()
            .putString(TransferWorkKeys.IP, "127.0.0.1")
            .putInt(TransferWorkKeys.PORT, 53317)
            .putString(TransferWorkKeys.URIS, "[\"content://media/external/file/1\"]")
            .putString(TransferWorkKeys.TARGET_ALIAS, "TargetPC")
            .build()

        every { mockParams.inputData } returns inputData
        every { mockParams.id } returns workId
        every { mockParams.runAttemptCount } returns 0

        val delayingStream = object : java.io.InputStream() {
            override fun read(): Int {
                Thread.sleep(50)
                return 42
            }
        }
        every { mockContext.contentResolver.openInputStream(any()) } returns delayingStream

        val worker = io.mockk.spyk(UploadWorker(mockContext, mockParams))
        io.mockk.coEvery { worker.setForeground(any()) } returns Unit

        val uploadJob = launch(kotlinx.coroutines.Dispatchers.Default) {
            worker.doWork()
        }
        // Yield to allow worker to enter coroutineScope
        kotlinx.coroutines.delay(50)
        uploadJob.cancel(kotlinx.coroutines.CancellationException("Worker cancelled by user"))
        uploadJob.join()

        assertFalse("isUploading must be false on cancellation", clientEngine.uploadState.value.isUploading)
        assertEquals("Upload cancelled", clientEngine.uploadState.value.error)
        assertNull("activeWorkId must be cleared on cancellation", clientEngine.activeWorkId)
    }
}



