package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
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
}
