package com.dexstudios.dex.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientEngineTest {

    @Test
    fun `authToken uses identity hash when target shares the email`() {
        val config = mockk<DeviceConfig>(relaxed = true)
        every { config.identityHash } returns "hash123"
        val engine = ClientEngine(deviceConfig = config)

        assertEquals("hash123", engine.authToken("some_fp", "hash123"))
    }

    @Test
    fun `authToken falls back to pairing token for different identity`() {
        val config = mockk<DeviceConfig>(relaxed = true)
        every { config.identityHash } returns "hash123"
        val engine = ClientEngine(deviceConfig = config)
        AuthState.pairedTokens["some_fp"] = "pairtok"

        assertEquals("pairtok", engine.authToken("some_fp", "other_hash"))
        assertEquals("pairtok", engine.authToken("some_fp", null))
    }

    @Test
    fun `authToken returns null without email or pairing`() {
        val config = mockk<DeviceConfig>(relaxed = true)
        every { config.identityHash } returns ""
        val engine = ClientEngine(deviceConfig = config)

        assertEquals(null, engine.authToken("some_fp", null))
    }

    @Test
    fun `sendClipboard returns true when response is 200 OK`() = runBlocking {
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
        val result = clientEngine.sendClipboard("192.168.1.5", 53317, "Hello World")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `sendClipboard returns false when response is not 200 OK`() = runBlocking {
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
        val result = clientEngine.sendClipboard("192.168.1.5", 53317, "Hello World")

        // Assert
        assertFalse(result)
    }
}
