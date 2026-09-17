package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.network.server.ReceiveStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPullServiceTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dex-pull-service-test").toFile()
        ReceiveStorage.overridePath = tempDir.absolutePath
    }

    @After
    fun tearDown() {
        ReceiveStorage.overridePath = null
        tempDir.deleteRecursively()
    }

    @Test
    fun `successful pull commits file non-destructively and cleans up part file`() = runBlocking {
        val payload = "Hello world pull content"
        val engine = MockEngine { request ->
            respond(
                content = payload.toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, payload.length.toString()),
            )
        }
        val pullService = DesktopPullService(HttpClient(engine), sessionTtlMillis = 0L)
        val file = PullFileDto(
            fileId = "f1",
            fileName = "hello.txt",
            size = payload.length.toLong(),
            token = "tok1",
        )

        val job = pullService.downloadBatch("127.0.0.1", 48424, 48426, listOf(file), "fp-sender", "SenderDevice")
        job.join()

        val target = File(tempDir, "hello.txt")
        assertTrue(target.exists(), "Target file must be committed")
        assertEquals(payload, target.readText())

        // Ensure no leftover .part files
        val parts = tempDir.listFiles { f -> f.name.contains(".part") } ?: emptyArray()
        assertEquals(0, parts.size, "No .part files should remain")
    }

    @Test
    fun `pull never deletes pre-existing file on collision and commits to sequential name`() = runBlocking {
        val existing = File(tempDir, "sample.txt").apply { writeText("original content") }
        val newPayload = "fresh content from peer"

        val engine = MockEngine { request ->
            respond(
                content = newPayload.toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, newPayload.length.toString()),
            )
        }
        val pullService = DesktopPullService(HttpClient(engine), sessionTtlMillis = 0L)
        val file = PullFileDto(
            fileId = "f1",
            fileName = "sample.txt",
            size = newPayload.length.toLong(),
            token = "tok1",
        )

        val job = pullService.downloadBatch("127.0.0.1", 48424, 48426, listOf(file), "fp-sender", "SenderDevice")
        job.join()

        val copyTarget = File(tempDir, "sample (1).txt")

        // Original file must NOT be deleted
        assertTrue(existing.exists(), "Pre-existing file must not be deleted")
        assertEquals("original content", existing.readText())

        // New file lands as sample (1).txt
        assertTrue(copyTarget.exists(), "New file must land as sequential copy")
        assertEquals(newPayload, copyTarget.readText())
    }

    @Test
    fun `failed pull never deletes pre-existing destination file and cleans up part file`() = runBlocking {
        val existing = File(tempDir, "important.doc").apply { writeText("critical data") }

        val engine = MockEngine { request ->
            respondError(HttpStatusCode.InternalServerError)
        }
        val pullService = DesktopPullService(HttpClient(engine), sessionTtlMillis = 0L)
        val file = PullFileDto(
            fileId = "f1",
            fileName = "important.doc",
            size = 100L,
            token = "tok1",
        )

        val job = pullService.downloadBatch("127.0.0.1", 48424, 48426, listOf(file), "fp-sender", "SenderDevice")
        job.join()

        // Existing file must survive
        assertTrue(existing.exists(), "Pre-existing file must never be deleted on download failure")
        assertEquals("critical data", existing.readText())

        // No .part file leaked
        val parts = tempDir.listFiles { f -> f.name.contains(".part") } ?: emptyArray()
        assertEquals(0, parts.size, "Failed transfer must not leak .part staging file")
    }

    @Test
    fun `pull properly encodes token and fileId in download URL`() = runBlocking {
        val payload = "Encoded token content"
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = payload.toByteArray(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, payload.length.toString()),
            )
        }
        val pullService = DesktopPullService(HttpClient(engine), sessionTtlMillis = 0L)
        val file = PullFileDto(
            fileId = "item 1",
            fileName = "item.txt",
            size = payload.length.toLong(),
            token = "secret+token/value==",
        )

        val job = pullService.downloadBatch("127.0.0.1", 48424, 0, listOf(file), "fp-sender", "SenderDevice")
        job.join()

        assertTrue(capturedUrl.contains("item+1") || capturedUrl.contains("item%201"), "fileId must be URL encoded in $capturedUrl")
        assertTrue(capturedUrl.contains("token=secret%2Btoken%2Fvalue%3D%3D") || capturedUrl.contains("secret%2Btoken"), "Token must be URL encoded in $capturedUrl")
    }
}
