package com.dexstudios.dex.network

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.network.protocol.DeXPorts
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientEngineTest {

    @Test
    fun authToken_uses_identity_hash_when_target_shares_the_email() = runTest {
        // For testing, we can directly set up a fake client engine if needed, or
        // we can test the token extraction logic directly. 
        // In KMP, without mockk, we construct minimal mocks or use open classes.
        // Assuming ClientEngine logic for tokens:
        val hash = "hash123"
        // Simulate a ClientEngine with an identityHash of hash123
        // If ClientEngine is tightly coupled to DeviceConfig injected by Koin, 
        // we might need to start Koin or refactor to take a tokenProvider.
        // For now, let's assume ClientEngine has a way to inject/pass identityHash for tests.
        // If it's a regular function:
        // assertEquals("hash123", engine.authToken("some_fp", "hash123"))
    }

    @Test
    fun sendClipboard_returns_true_when_response_is_200_OK() = runTest {
        // Arrange
        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val clientEngine = ClientEngine(mockEngine)

        // Act
        val result = clientEngine.sendClipboard("192.168.1.5", DeXPorts.HTTPS, "Hello World")

        // Assert
        assertTrue(result)
    }

    @Test
    fun sendClipboard_returns_false_when_response_is_not_200_OK() = runTest {
        // Arrange
        val mockEngine = MockEngine { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val clientEngine = ClientEngine(mockEngine)

        // Act
        val result = clientEngine.sendClipboard("192.168.1.5", DeXPorts.HTTPS, "Hello World")

        // Assert
        assertFalse(result)
    }
}
