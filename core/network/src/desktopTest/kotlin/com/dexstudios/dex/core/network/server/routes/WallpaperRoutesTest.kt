package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.DeviceConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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

class WallpaperRoutesTest {

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
        AuthState.updateFingerprints(emptySet())
    }

    @Test
    fun `wallpaper endpoint rejects requests without auth`() = runTest {
        testApplication {
            application { routing { wallpaperRoutes() } }
            val response = client.get("/api/dex/wallpaper")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `wallpaper endpoint rejects forged bearer token`() = runTest {
        testApplication {
            application { routing { wallpaperRoutes() } }
            val response = client.get("/api/dex/wallpaper") {
                bearerAuth("forged-token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `localsend v2 wallpaper endpoint rejects unauthenticated request`() = runTest {
        testApplication {
            application { routing { wallpaperRoutes() } }
            val response = client.get("/api/localsend/v2/wallpaper")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
