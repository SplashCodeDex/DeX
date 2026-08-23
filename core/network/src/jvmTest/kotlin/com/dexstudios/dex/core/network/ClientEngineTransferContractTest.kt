package com.dexstudios.dex.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.channels.Channels
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Transfer-contract baseline for [ClientEngine]: prepare-upload status mapping,
 * upload query/transport contract, QUIC delegation, and the upload state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientEngineTransferContractTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Mirrors the production client wiring (ContentNegotiation is required for body parsing). */
    private fun clientWithJson(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }

    private fun sampleRequest() = PrepareUploadRequestDto(
        info = RegisterDto(
            alias = "MyPC",
            version = "2.0",
            deviceModel = "PC",
            deviceType = "desktop",
            fingerprint = "fp_pc",
            port = 48424,
            protocol = "localsend",
            download = false
        ),
        files = mapOf("f1" to FileDto(id = "f1", fileName = "a.bin", size = 10, fileType = "application/octet-stream"))
    )

    // =========================================================================
    // prepareUpload — status mapping contract (httpStatus -1 == transport failure)
    // =========================================================================

    @Test
    fun `prepareUpload parses session response on 200 and forwards bearer token`() = runTest {
        val payload = Json.encodeToString(PrepareUploadResponseDto("sess-1", mapOf("f1" to "rid-1")))
        var authHeader: String? = null
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond(payload, HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val result = ClientEngine(clientWithJson(engine)).prepareUpload("10.0.0.2", 48424, sampleRequest(), token = "tok_123")

        assertEquals(200, result.httpStatus)
        assertEquals("sess-1", result.response?.sessionId)
        assertEquals(mapOf("f1" to "rid-1"), result.response?.files)
        assertEquals("Bearer tok_123", authHeader)
    }

    @Test
    fun `prepareUpload preserves rejection status without body`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Forbidden) }

        val result = ClientEngine(clientWithJson(engine)).prepareUpload("10.0.0.2", 48424, sampleRequest(), token = "bad")

        assertNull(result.response)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun `prepareUpload reports transport failure as -1`() = runTest {
        val engine = MockEngine { throw java.net.ConnectException("host unreachable") }

        val result = ClientEngine(clientWithJson(engine)).prepareUpload("10.255.255.1", 1, sampleRequest(), token = null)

        assertNull(result.response)
        assertEquals(-1, result.httpStatus)
    }

    // =========================================================================
    // uploadFile — endpoint/query/outcome contract
    // =========================================================================

    @Test
    fun `uploadFile posts stream with session file token query parameters`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("", HttpStatusCode.OK)
        }
        val clientEngine = ClientEngine(HttpClient(engine))
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))

        val outcome = clientEngine.uploadFile(
            ip = "10.0.0.2", port = 48424, sessionId = "sess-9", fileId = "rid-1",
            fileName = "a.bin", token = "pulltok", stream = stream, fileSize = 4
        )

        assertTrue(outcome.ok)
        assertEquals(200, outcome.httpStatus)
        assertTrue(capturedUrl!!.contains("/api/localsend/v2/upload"))
        assertEquals("sess-9", capturedUrl!!.toQueryValue("sessionId"))
        assertEquals("rid-1", capturedUrl!!.toQueryValue("fileId"))
        assertEquals("pulltok", capturedUrl!!.toQueryValue("token"))
    }

    @Test
    fun `uploadFile maps http failure status`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InsufficientStorage) }

        val outcome = ClientEngine(HttpClient(engine)).uploadFile(
            "10.0.0.2", 48424, "s", "f", "n", "t", ByteArrayInputStream(ByteArray(1)), 1
        )

        assertFalse(outcome.ok)
        assertEquals(507, outcome.httpStatus)
    }

    @Test
    fun `uploadFile maps transport failure to -1`() = runTest {
        val engine = MockEngine { throw java.net.ConnectException("boom") }

        val outcome = ClientEngine(HttpClient(engine)).uploadFile(
            "10.0.0.2", 48424, "s", "f", "n", "t", ByteArrayInputStream(ByteArray(1)), 1
        )

        assertFalse(outcome.ok)
        assertEquals(-1, outcome.httpStatus)
    }

    // =========================================================================
    // QUIC delegation
    // =========================================================================

    private fun mockQuic(configure: (IQuicClient) -> Unit): IQuicClient {
        val quic = mockk<IQuicClient>()
        every { quic.available() } returns true
        every { quic.lastUploadProtocol } returns "h3"
        configure(quic)
        return quic
    }

    @Test
    fun `uploadFileQuic delegates and maps transport result`() = runTest {
        val quic = mockQuic {
            every {
                it.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                arg<(Boolean, Int) -> Unit>(9).invoke(true, 200)
                Unit
            }
        }
        val clientEngine = ClientEngine(HttpClient(MockEngine { respondOk() }), quicClient = quic)

        val outcome = clientEngine.uploadFileQuic(
            "10.0.0.2", 48424, "s", "f", "n", "t", ByteArrayInputStream(ByteArray(1)), 1
        )

        assertEquals(UploadOutcome(true, 200), outcome)
        assertTrue(clientEngine.quicAvailable())
        assertEquals("h3", clientEngine.lastUploadProtocol())
    }

    @Test
    fun `downloadFileQuic delegates with negotiated protocol`() = runTest {
        val quic = mockQuic {
            every {
                it.downloadFile(any(), any(), any(), any(), any(), any(), any())
            } answers {
                arg<(Boolean, Int, String) -> Unit>(6).invoke(true, 200, "h3")
                Unit
            }
        }
        val clientEngine = ClientEngine(HttpClient(MockEngine { respondOk() }), quicClient = quic)

        val outcome = clientEngine.downloadFileQuic(
            "10.0.0.2", 48424, "file-1", "pulltok",
            output = Channels.newChannel(ByteArrayOutputStream())
        )

        assertTrue(outcome.ok)
        assertEquals("h3", outcome.protocol)
    }

    @Test
    fun `quic uploads degrade to transport failure when engine unavailable`() = runTest {
        val noQuic = ClientEngine(HttpClient(MockEngine { respondOk() }))
        val nullRequest = ClientEngine(
            HttpClient(MockEngine { respondOk() }),
            quicClient = mockQuic {
                every {
                    it.uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                } returns null
            }
        )

        assertFalse(noQuic.quicAvailable())
        assertEquals("", noQuic.lastUploadProtocol())
        assertEquals(
            UploadOutcome(false, -1),
            noQuic.uploadFileQuic("ip", 1, "s", "f", "n", "t", ByteArrayInputStream(ByteArray(1)), 1)
        )
        assertEquals(
            UploadOutcome(false, -1),
            nullRequest.uploadFileQuic("ip", 1, "s", "f", "n", "t", ByteArrayInputStream(ByteArray(1)), 1)
        )
        assertEquals(
            DownloadOutcome(false, -1),
            noQuic.downloadFileQuic("ip", 1, "fid", "tok", Channels.newChannel(ByteArrayOutputStream()))
        )
    }

    // =========================================================================
    // Upload state machine (virtual time on Dispatchers.Main)
    // =========================================================================

    @Test
    fun `success upload state auto-resets after six seconds`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clientEngine = ClientEngine(HttpClient(MockEngine { respondOk() }))

        clientEngine.updateUploadState(UploadState(fileName = "batch.zip", isSuccess = true))
        assertTrue(clientEngine.uploadState.value.isSuccess)

        advanceTimeBy(5_999)
        runCurrent()
        assertTrue(clientEngine.uploadState.value.isSuccess)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(UploadState(), clientEngine.uploadState.value)
    }

    @Test
    fun `finishUpload summarizes success and resets after five seconds`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clientEngine = ClientEngine(HttpClient(MockEngine { respondOk() }))

        clientEngine.finishUpload(successCount = 2, totalFiles = 3)

        var state = clientEngine.uploadState.value
        assertTrue(state.isSuccess)
        assertEquals("2 of 3 files", state.fileName)

        advanceTimeBy(5_000)
        runCurrent()
        state = clientEngine.uploadState.value
        assertFalse(state.isSuccess)
        assertEquals(UploadState(), state)
    }

    @Test
    fun `finishUpload with zero successes sets error without auto-reset`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val clientEngine = ClientEngine(HttpClient(MockEngine { respondOk() }))

        clientEngine.finishUpload(successCount = 0, totalFiles = 3)

        val state = clientEngine.uploadState.value
        assertFalse(state.isSuccess)
        assertEquals("Upload failed for all files", state.error)

        advanceUntilIdle() // no reset job scheduled — the error must persist
        assertEquals("Upload failed for all files", clientEngine.uploadState.value.error)
    }

    @Test
    fun `cancelUpload notifies the active worker exactly once`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val cancelledIds = mutableListOf<String>()
        val clientEngine = ClientEngine(
            HttpClient(MockEngine { respondOk() }),
            onCancelUpload = { cancelledIds.add(it) }
        )
        clientEngine.activeWorkId = "work-7"

        clientEngine.cancelUpload()
        clientEngine.cancelUpload() // activeWorkId already cleared — must not double-cancel

        assertEquals(listOf("work-7"), cancelledIds)
        assertEquals("Upload cancelled", clientEngine.uploadState.value.error)
        assertFalse(clientEngine.uploadState.value.isUploading)
    }

    private fun String.toQueryValue(key: String): String? =
        substringAfter('?', "").split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.first() == key }
            ?.getOrNull(1)
}