package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.TransferCheckpointRegistry
import com.dexstudios.dex.core.network.server.ReceiveStorage
import com.dexstudios.dex.core.network.services.RelayService
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Route-level baseline for [shareRoutes] — the LocalSend v2 hot zone.
 *
 * Covers the prepare-upload auth matrix (auto-trust vs pairing tokens seeded into the
 * REAL [AuthState] store), the pull-download token gates, and the upload rejection paths
 * including Zip-slip and the receiver-integrity gates (an unhonorable resume offset, a body
 * shorter than the declared size, and cancel stopping the live writer).
 *
 * The upload HAPPY path is intentionally NOT exercised here: it writes to the real user
 * Downloads folder and fires a SystemTray notification, which would leak test side effects
 * onto the host machine. The integrity tests stop short of a commit; the one that has to
 * stage bytes creates a single small `.part` file in that folder and removes it again.
 */
class ShareRoutesTest {

    private lateinit var deviceConfig: DeviceConfig

    @Before
    fun setUp() {
        deviceConfig = mockk {
            every { this@mockk.identityHash } returns IDENTITY_HASH
            every { this@mockk.googleSub } returns GOOGLE_SUB
            every { this@mockk.fingerprint } returns "pc-fingerprint"
            every { this@mockk.dndEnabled } returns false
        }
        startKoin { modules(module { single { deviceConfig } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
        AuthState.updateTokens(emptyMap())
        activeUploadSessions.clear()
        activeUploadSessionsProgress.clear()
        RelayService.hostedFiles.clear()
        RelayService.hostedFileTokens.clear()
        RelayService.hostedFileLastAccess.clear()
        RelayService.relaySessionFiles.clear()
        RelayService.relaySessionAliases.clear()
    }

    private fun Application.installShareRoutes() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        routing { shareRoutes() }
    }

    /** Share + control routes: the cancel test needs `/cancel` mounted beside `/upload`. */
    private fun Application.installShareAndControlRoutes() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        routing {
            shareRoutes()
            controlRoutes()
        }
    }

    private fun prepareRequest(fingerprint: String, vararg files: FileDto): String = Json.encodeToString(
        PrepareUploadRequestDto(
            info = RegisterDto(
                alias = "Pixel",
                version = "2.0",
                deviceModel = "Pixel 9",
                deviceType = "mobile",
                fingerprint = fingerprint,
                port = 48424,
                protocol = "localsend",
                download = false,
            ),
            files = files.associateBy { it.id },
        ),
    )

    private fun sampleFile(id: String): FileDto = FileDto(id = id, fileName = "photo.jpg", size = 1024, fileType = "image/jpeg")

    // =========================================================================
    // prepare-upload auth matrix
    // =========================================================================

    @Test
    fun `prepare-upload rejects request without authorization header`() = testApplication {
        application { installShareRoutes() }

        val response = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            setBody(prepareRequest("phone-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `prepare-upload rejects unknown bearer token from unpaired fingerprint`() = testApplication {
        application { installShareRoutes() }

        val response = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            bearerAuth("some-random-token")
            setBody(prepareRequest("stranger-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `prepare-upload accepts auto-trusted identity hash token`() = testApplication {
        application { installShareRoutes() }

        val response = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            bearerAuth(IDENTITY_HASH)
            setBody(prepareRequest("phone-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertValidPrepareResponse(Json.decodeFromString<PrepareUploadResponseDto>(response.bodyAsText()))
    }

    @Test
    fun `prepare-upload accepts same-account google sub token`() = testApplication {
        application { installShareRoutes() }

        val response = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            bearerAuth(GOOGLE_SUB)
            setBody(prepareRequest("roster-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertValidPrepareResponse(Json.decodeFromString<PrepareUploadResponseDto>(response.bodyAsText()))
    }

    @Test
    fun `prepare-upload accepts pairing token from AuthState bound to sender fingerprint`() = testApplication {
        application { installShareRoutes() }
        AuthState.updateTokens(mapOf("paired-fp" to "pairtok-123"))

        val paired = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer pairtok-123")
            setBody(prepareRequest("paired-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.OK, paired.status)
        assertValidPrepareResponse(Json.decodeFromString<PrepareUploadResponseDto>(paired.bodyAsText()))

        // The same pairing token presented under a DIFFERENT fingerprint stays rejected
        val mismatched = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer pairtok-123")
            setBody(prepareRequest("other-fp", sampleFile("f1")))
        }
        assertEquals(HttpStatusCode.Forbidden, mismatched.status)
    }

    @Test
    fun `prepare-upload responds insufficient storage when payload exceeds free disk space`() = testApplication {
        application { installShareRoutes() }
        val huge = sampleFile("f1").copy(size = Long.MAX_VALUE / 2)

        val response = client.post("/api/localsend/v2/prepare-upload") {
            contentType(ContentType.Application.Json)
            bearerAuth(IDENTITY_HASH)
            setBody(prepareRequest("phone-fp", huge))
        }
        assertEquals(HttpStatusCode.InsufficientStorage, response.status)
    }

    // =========================================================================
    // pull-download token gates
    // =========================================================================

    @Test
    fun `pull download serves hosted file only with matching pull token`() = testApplication {
        application { installShareRoutes() }
        val payload = "dex-hosted-payload"
        val tempFile = Files.createTempFile("dex_hosted", ".bin")
        Files.write(tempFile, payload.toByteArray())
        try {
            RelayService.hostedFiles["file-1"] = tempFile.toString()
            RelayService.hostedFileTokens["file-1"] = "pulltok"
            RelayService.hostedFileLastAccess.clear()

            val missingParams = client.get("/api/localsend/v2/download") { parameter("sessionId", "s") }
            assertEquals(HttpStatusCode.BadRequest, missingParams.status)

            val wrongToken = client.get("/api/localsend/v2/download") {
                parameter("sessionId", "s")
                parameter("fileId", "file-1")
                parameter("token", "nope")
            }
            assertEquals(HttpStatusCode.Forbidden, wrongToken.status)

            val served = client.get("/api/localsend/v2/download") {
                parameter("sessionId", "s")
                parameter("fileId", "file-1")
                parameter("token", "pulltok")
            }
            assertEquals(HttpStatusCode.OK, served.status)
            assertEquals(payload, served.bodyAsText())

            // Sliding-TTL bookkeeping: serving refreshes last access
            assertEquals(true, RelayService.hostedFileLastAccess.containsKey("file-1"))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `pull download reports not found when hosted path has vanished`() = testApplication {
        application { installShareRoutes() }
        val gone = Files.createTempFile("dex_gone", ".bin")
        Files.delete(gone)

        RelayService.hostedFiles["file-9"] = gone.toString()
        RelayService.hostedFileTokens["file-9"] = "pulltok"

        val response = client.get("/api/localsend/v2/download") {
            parameter("sessionId", "s")
            parameter("fileId", "file-9")
            parameter("token", "pulltok")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(false, RelayService.hostedFileLastAccess.containsKey("file-9"))
    }

    @Test
    fun `legacy download keeps its original not-found token gate semantics`() = testApplication {
        application { installShareRoutes() }
        val tempFile = Files.createTempFile("dex_legacy", ".txt")
        Files.write(tempFile, "legacy".toByteArray())
        try {
            RelayService.hostedFiles["legacy-1"] = tempFile.toString()
            RelayService.hostedFileTokens["legacy-1"] = "legacytok"

            val missingToken = client.get("/download/legacy-1")
            assertEquals(HttpStatusCode.BadRequest, missingToken.status)

            val wrongToken = client.get("/download/legacy-1") { parameter("token", "bad") }
            assertEquals(HttpStatusCode.NotFound, wrongToken.status)

            val served = client.get("/download/legacy-1") { parameter("token", "legacytok") }
            assertEquals(HttpStatusCode.OK, served.status)
            assertEquals("legacy", served.bodyAsText())
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // =========================================================================
    // upload rejection paths (happy path intentionally unexercised — writes to
    // the real Downloads folder and fires a system tray notification)
    // =========================================================================

    @Test
    fun `upload rejects unknown session or file identifiers`() = testApplication {
        application { installShareRoutes() }
        activeUploadSessions["sess-real"] = SessionEntry(prepareRequestParsed("phone-fp", sampleFile("f1")))

        val noParams = client.post("/api/localsend/v2/upload") { setBody("") }
        assertEquals(HttpStatusCode.BadRequest, noParams.status)

        val unknownSession = client.post("/api/localsend/v2/upload") {
            parameter("sessionId", "sess-fake")
            parameter("fileId", "f1")
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownSession.status)

        val unknownFile = client.post("/api/localsend/v2/upload") {
            parameter("sessionId", "sess-real")
            parameter("fileId", "f-other")
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, unknownFile.status)
    }

    @Test
    fun `upload rejects zip-slip relative paths before writing anything`() = testApplication {
        application { installShareRoutes() }
        val slip = sampleFile("f1").copy(fileName = "evil.bin", relativePath = "..\\..\\evil.bin")
        activeUploadSessions["sess-slip"] = SessionEntry(prepareRequestParsed("phone-fp", slip))

        val response = client.post("/api/localsend/v2/upload") {
            parameter("sessionId", "sess-slip")
            parameter("fileId", "f1")
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // =========================================================================
    // receiver-integrity gates (resume offsets, truncated bodies, cancellation)
    // =========================================================================

    @Test
    fun `upload refuses an unhonorable resume offset instead of restarting from zero`() = testApplication {
        application { installShareRoutes() }
        val file = sampleFile("f1").copy(fileName = "resume_probe.bin", size = 2048)
        activeUploadSessions["sess-resume"] = SessionEntry(prepareRequestParsed("phone-fp", file))

        // Nothing is staged for this session, so the claimed offset cannot be honoured. The
        // old code silently wrote the body as if it were a fresh upload and answered 200.
        val response = client.post("/api/localsend/v2/upload") {
            parameter("sessionId", "sess-resume")
            parameter("fileId", "f1")
            parameter("offset", "1024")
            setBody(ByteArray(1024))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `upload refuses a resume offset beyond the declared size`() = testApplication {
        application { installShareRoutes() }
        val file = sampleFile("f1").copy(fileName = "resume_overflow.bin", size = 1024)
        activeUploadSessions["sess-overflow"] = SessionEntry(prepareRequestParsed("phone-fp", file))

        val response = client.post("/api/localsend/v2/upload") {
            parameter("sessionId", "sess-overflow")
            parameter("fileId", "f1")
            parameter("offset", "4096")
            setBody(ByteArray(0))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `upload refuses a body shorter than the declared size and leaves staging resumable`() = testApplication {
        application { installShareRoutes() }
        val fileName = "truncation_probe_${System.currentTimeMillis()}.bin"
        val file = sampleFile("f1").copy(fileName = fileName, size = 4096)
        activeUploadSessions["sess-truncated"] = SessionEntry(prepareRequestParsed("phone-fp", file))
        val staging = File(ReceiveStorage.downloadsDir(), "$fileName.part.sess-truncated.f1")
        val destination = File(ReceiveStorage.downloadsDir(), fileName)

        try {
            val response = client.post("/api/localsend/v2/upload") {
                parameter("sessionId", "sess-truncated")
                parameter("fileId", "f1")
                setBody(ByteArray(100)) // ends cleanly, 3996 bytes short of the declared size
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(staging.exists(), "A short body must leave staging on disk for a resume")
            assertFalse(destination.exists(), "A truncated body must never be committed as the file")
        } finally {
            TransferCheckpointRegistry.discardPartFile("sess-truncated", "f1")
            staging.delete()
            destination.delete()
        }
    }

    @Test
    fun `cancel aborts the live upload so it can neither commit nor report success`() = testApplication {
        application { installShareAndControlRoutes() }
        val fileName = "cancel_probe_${System.currentTimeMillis()}.bin"
        val declaredSize = 100_000L
        val file = sampleFile("f1").copy(fileName = fileName, size = declaredSize)
        activeUploadSessions["sess-cancel"] = SessionEntry(prepareRequestParsed("phone-fp", file))
        // A second client: the uploading client is busy streaming its body.
        val controller = createClient { }
        val uploadScope = CoroutineScope(Dispatchers.IO)
        val committed = File(ReceiveStorage.downloadsDir(), fileName)
        val staging = File(ReceiveStorage.downloadsDir(), "$fileName.part.sess-cancel.f1")

        val upload = uploadScope.async {
            runCatching {
                client.post("/api/localsend/v2/upload") {
                    parameter("sessionId", "sess-cancel")
                    parameter("fileId", "f1")
                    setBody(object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.Application.OctetStream

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            // Exactly the declared size, written slowly: an uncancelled run
                            // would commit successfully, so a failure is attributable to the
                            // cancel alone. Bounded so a regression cannot hang the suite.
                            repeat(50) {
                                val chunk = ByteArray(2000)
                                channel.writeFully(chunk, 0, chunk.size)
                                delay(20)
                            }
                        }
                    })
                }
            }
        }

        try {
            withTimeout(10_000) {
                while (!activeUploadJobs.containsKey("sess-cancel")) delay(10)
            }

            val cancel = controller.post("/api/localsend/v2/cancel") { parameter("sessionId", "sess-cancel") }
            assertEquals(HttpStatusCode.OK, cancel.status)

            val uploadResult = withTimeout(15_000) { upload.await() }
            assertTrue(
                uploadResult.isFailure || !uploadResult.getOrThrow().status.isSuccess(),
                "A cancelled upload must never report success",
            )
            assertFalse(activeUploadSessions.containsKey("sess-cancel"), "Cancel must drop the session record")
            assertFalse(committed.exists(), "A cancelled upload must not commit a file")
        } finally {
            uploadScope.cancel()
            TransferCheckpointRegistry.discardPartFile("sess-cancel", "f1")
            staging.delete()
            committed.delete()
        }
    }

    private fun prepareRequestParsed(fingerprint: String, vararg files: FileDto): PrepareUploadRequestDto = Json.decodeFromString<PrepareUploadRequestDto>(prepareRequest(fingerprint, *files))

    private fun assertValidPrepareResponse(dto: PrepareUploadResponseDto) {
        assertEquals(true, dto.sessionId.isNotEmpty())
        assertEquals(setOf("f1"), dto.files.keys)
        assertEquals(true, dto.files.values.all { it.isNotEmpty() })
    }

    private companion object {
        const val IDENTITY_HASH = "identity-hash-abc123"
        const val GOOGLE_SUB = "google-sub-xyz789"
    }
}
