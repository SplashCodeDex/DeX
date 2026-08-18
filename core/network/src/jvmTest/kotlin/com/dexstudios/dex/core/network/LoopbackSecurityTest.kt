package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.network.server.LoopbackSecurityPlugin
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LoopbackSecurityTest {

    @Test
    fun testLocalPathAllowsLoopback() = testApplication {
        application {
            install(LoopbackSecurityPlugin)
            routing {
                get("/local/test") {
                    call.respond(HttpStatusCode.OK, "Success")
                }
            }
        }
        val response = client.get("/local/test")
        // Since testApplication uses localhost/127.0.0.1 by default
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
