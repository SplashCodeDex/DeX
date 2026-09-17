package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.domain.clipboard.ClipboardAccess
import com.dexstudios.dex.core.domain.clipboard.ClipboardPayload
import com.dexstudios.dex.core.domain.clipboard.ClipboardSender
import com.dexstudios.dex.core.domain.clipboard.ClipboardSyncUseCase
import com.dexstudios.dex.core.network.ClipboardSyncState
import com.dexstudios.dex.core.network.DeviceConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the clipboard push route's bearer gate. Only rejection paths are exercised:
 * a passing bearer would write to the HOST's real system clipboard, which is a test
 * side effect this suite must not produce (same policy as the upload happy path in
 * [ShareRoutesTest]). Gate ACCEPTANCE for every bearer tier is covered by
 * [com.dexstudios.dex.core.network.server.AccessControlTest].
 */
class ClipboardRoutesTest {

    private lateinit var deviceConfig: DeviceConfig

    @Before
    fun setUp() {
        deviceConfig = mockk {
            every { this@mockk.identityHash } returns "identity-hash-secret"
            every { this@mockk.googleSub } returns "google-sub-secret"
            every { this@mockk.fingerprint } returns "pc-fingerprint"
        }
        startKoin { modules(module { single { deviceConfig } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
        AuthState.updateTokens(emptyMap())
    }

    private suspend fun expectUnauthorized(bearer: String?) {
        testApplication {
            application { routing { clipboardRoutes() } }
            val response = client.post("/api/dex/clipboard") {
                if (bearer != null) bearerAuth(bearer)
                setBody("synced text")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `missing bearer is rejected`() = runTest {
        expectUnauthorized(bearer = null)
    }

    @Test
    fun `wrong bearer is rejected`() = runTest {
        expectUnauthorized(bearer = "not-a-credential")
    }

    @Test
    fun `bearer matching no stored pairing token is rejected`() = runTest {
        AuthState.updateTokens(mapOf("phone-fp" to "real-paired-token"))
        expectUnauthorized(bearer = "forged-paired-token")
    }

    @Test
    fun `trusted clipboard push routes through useCase to prime the echo guard`() = runTest {
        var writtenPayload: ClipboardPayload? = null
        val fakeAccess = object : ClipboardAccess {
            override suspend fun read(): ClipboardPayload? = writtenPayload
            override suspend fun write(payload: ClipboardPayload) {
                writtenPayload = payload
            }
        }
        val fakeSender = object : ClipboardSender {
            val sent = mutableListOf<ClipboardPayload>()
            override suspend fun send(payload: ClipboardPayload): Boolean {
                sent.add(payload)
                return true
            }
        }
        val useCase = ClipboardSyncUseCase(
            access = fakeAccess,
            sender = fakeSender,
            enabled = { true },
            hash = { it },
        )
        ClipboardSyncState.useCase = useCase

        try {
            testApplication {
                application { routing { clipboardRoutes() } }
                val response = client.post("/api/dex/clipboard") {
                    bearerAuth("identity-hash-secret")
                    setBody("hello-world-from-phone")
                }
                assertEquals(HttpStatusCode.OK, response.status)
            }

            // The useCase must have received the payload and written it
            assertEquals(ClipboardPayload.Text("hello-world-from-phone"), writtenPayload)

            // AND the echo guard must be primed so a local change event does NOT bounce it back
            assertFalse(useCase.onLocalClipboardChanged(), "Echo guard must suppress bounce back of remote text")
            assertTrue(fakeSender.sent.isEmpty(), "Remote text must not be re-broadcast")
        } finally {
            ClipboardSyncState.useCase = null
        }
    }

    @Test
    fun `oversized clipboard payload is rejected with PayloadTooLarge`() = runTest {
        testApplication {
            application { routing { clipboardRoutes() } }
            val largeBody = "x".repeat(1024 * 1024 + 10)
            val response = client.post("/api/dex/clipboard") {
                bearerAuth("identity-hash-secret")
                setBody(largeBody)
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }
}
