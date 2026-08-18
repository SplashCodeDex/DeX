package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class ShareTargetPayload(val files: List<String>)

// Match the C# implementation state variables
val activeUploadSessions = ConcurrentHashMap<String, PrepareUploadRequestDto>()

@OptIn(DelicateCoroutinesApi::class)
fun Route.shareRoutes() {
    route("/local") {
        post("/share-target") {
            try {
                val payload = call.receive<ShareTargetPayload>()
                
                val fileList = payload.files.map { Pair(it, null) }
                com.dexstudios.dex.core.network.services.RelayService.hostAndPushAsync(
                    targetFingerprint = "", // Target device should be sent in payload if specific
                    files = fileList,
                    senderAlias = System.getProperty("user.name") ?: "PC"
                )
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    route("/api/localsend/v2") {

        get("/download") {
            val sessionId = call.request.queryParameters["sessionId"]
            val fileId = call.request.queryParameters["fileId"]
            val token = call.request.queryParameters["token"]
            
            if (fileId == null || token == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val expectedToken = com.dexstudios.dex.core.network.services.RelayService.hostedFileTokens[fileId]
            val filePath = com.dexstudios.dex.core.network.services.RelayService.hostedFiles[fileId]
            
            if (expectedToken == null || expectedToken != token || filePath == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            
            com.dexstudios.dex.core.network.services.RelayService.hostedFileLastAccess[fileId] = System.currentTimeMillis()
            val file = File(filePath)
            if (!file.exists()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            
            call.respondFile(file)
        }

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

    // Legacy route preservation for older clients
    get("/download/{fileId}") {
        val fileId = call.parameters["fileId"]
        val token = call.request.queryParameters["token"]
        
        if (fileId == null || token == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        
        val expectedToken = com.dexstudios.dex.core.network.services.RelayService.hostedFileTokens[fileId]
        val filePath = com.dexstudios.dex.core.network.services.RelayService.hostedFiles[fileId]
        
        if (expectedToken == null || expectedToken != token || filePath == null) {
            call.respond(HttpStatusCode.NotFound) // Original C# used NotFound for missing/invalid token
            return@get
        }
        
        com.dexstudios.dex.core.network.services.RelayService.hostedFileLastAccess[fileId] = System.currentTimeMillis()
        val file = File(filePath)
        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        
        call.respondFile(file)
    }
}
