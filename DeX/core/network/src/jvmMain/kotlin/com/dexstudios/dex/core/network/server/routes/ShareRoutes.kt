package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Match the C# implementation state variables
val activeUploadSessions = ConcurrentHashMap<String, PrepareUploadRequestDto>()

fun Route.shareRoutes() {
    route("/api/localsend/v2") {

        post("/prepare-upload") {
            try {
                val req = call.receive<PrepareUploadRequestDto>()

                // TODO: Verify Authorization header with IdentityManager if needed

                val sessionId = UUID.randomUUID().toString()
                activeUploadSessions[sessionId] = req

                val resFiles = mutableMapOf<String, String>()
                val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
                downloadsFolder.mkdirs()

                req.files.forEach { (key, _) ->
                    resFiles[key] = UUID.randomUUID().toString()
                }

                call.respond(PrepareUploadResponseDto(sessionId = sessionId, files = resFiles))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest)
            }
        }

        post("/upload") {
            val sessionId = call.request.queryParameters["sessionId"]
            val fileId = call.request.queryParameters["fileId"]

            if (sessionId == null || fileId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val sessionReq = activeUploadSessions[sessionId]
            if (sessionReq == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val fileMeta = sessionReq.files[fileId]
            if (fileMeta == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val safeFileName = fileMeta.fileName.ifEmpty { "unnamed_file" }

            val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
            downloadsFolder.mkdirs()

            val destFile = File(downloadsFolder, safeFileName)

            try {
                val channel: ByteReadChannel = call.receiveChannel()
                withContext(Dispatchers.IO) {
                    destFile.outputStream().use { output ->
                        channel.copyTo(output)
                    }
                }
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
