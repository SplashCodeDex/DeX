package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.RegisterDto
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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * Route-level baseline for [shareRoutes] — the LocalSend v2 hot zone.
 *
 * Covers the prepare-upload auth matrix (auto-trust vs pairing tokens seeded into the
 * REAL [AuthState] store), the pull-download token gates, and the upload rejection
 * paths including Zip-slip. The upload happy path is intentionally NOT exercised here:
 * it writes to the real user Downloads folder and fires a SystemTray notification,
 * which would leak test side effects onto the host machine.
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
