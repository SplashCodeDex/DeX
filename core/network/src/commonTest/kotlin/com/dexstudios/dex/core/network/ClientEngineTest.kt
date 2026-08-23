package com.dexstudios.dex.core.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.dexstudios.dex.auth.AuthState

class ClientEngineTest {

    @AfterTest
    fun resetAuthState() {
        AuthState.updateTokens(emptyMap())
    }

    @Test
    fun `authToken falls back to pairing token when config is null`() {
        val engine = ClientEngine(client = HttpClient(MockEngine { respondOk() }), deviceConfig = null)
        AuthState.updateTokens(mapOf("some_fp" to "pairtok"))

        assertEquals("pairtok", engine.authToken("some_fp", "other_hash"))
        assertEquals("pairtok", engine.authToken("some_fp", null))
    }

    @Test
    fun `authToken returns null without pairing token and no config`() {
        val engine = ClientEngine(client = HttpClient(MockEngine { respondOk() }), deviceConfig = null)

        assertEquals(null, engine.authToken("unpaired_fp", null))
    }

    @Test
    fun `sendClipboard returns true when response is 200 OK`() = runTest {
        // Arrange
        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val clientEngine = ClientEngine(client = HttpClient(mockEngine))

        // Act
        val result = clientEngine.sendClipboard("192.168.1.5", 8080, "Hello World")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `sendClipboard returns false when response is not 200 OK`() = runTest {
        // Arrange
        val mockEngine = MockEngine { request ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val clientEngine = ClientEngine(client = HttpClient(mockEngine))

        // Act
        val result = clientEngine.sendClipboard("192.168.1.5", 8080, "Hello World")

        // Assert
        assertFalse(result)
    }
}
