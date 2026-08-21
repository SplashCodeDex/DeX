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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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

data class SessionEntry(val request: PrepareUploadRequestDto, val createdAt: Long = System.currentTimeMillis())

// Match the C# implementation state variables
val activeUploadSessions = ConcurrentHashMap<String, SessionEntry>()
val activeUploadSessionsProgress = ConcurrentHashMap<String, Int>()
private val shareRoutesFileLock = Any()
private val shareRouteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).apply {
    launch {
        while (true) {
            delay(60_000) // 1 minute
            val now = System.currentTimeMillis()
            val iterator = activeUploadSessions.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.createdAt > 10 * 60_000) { // 10 mins
                    iterator.remove()
                    activeUploadSessionsProgress.remove(entry.key)
                }
            }
        }
    }
}

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

                val authHeader = call.request.header("Authorization")
                val token = authHeader?.removePrefix("Bearer ")?.trim()

                val koin = org.koin.core.context.GlobalContext.get()
                val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()
                
                val isAutoTrusted = !token.isNullOrEmpty() && (token == deviceConfig.identityHash || (deviceConfig.googleSub.isNotEmpty() && token == deviceConfig.googleSub))
                
                val pairedTokens = com.dexstudios.dex.auth.AuthState.pairedTokens.value
                val isPaired = !token.isNullOrEmpty() && pairedTokens[req.info.fingerprint] == token

                if (!isAutoTrusted && !isPaired) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }

                val sessionId = UUID.randomUUID().toString()
                activeUploadSessions[sessionId] = SessionEntry(req)
                
                com.dexstudios.dex.core.network.TransferStateMonitor.updateIncomingProgress(
                    sessionId, 
                    req.info.alias.ifEmpty { "Device" }, 
                    req.files.size, 
                    0
                )

                val resFiles = mutableMapOf<String, String>()
                val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
                downloadsFolder.mkdirs()

                val totalSize = req.files.values.sumOf { it.size }
                if (downloadsFolder.freeSpace < totalSize) {
                    call.respond(HttpStatusCode.InsufficientStorage)
                    return@post
                }

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

            val sessionReq = activeUploadSessions[sessionId]?.request
            if (sessionReq == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val fileMeta = sessionReq.files[fileId]
            if (fileMeta == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val rawFileName = fileMeta.fileName.ifEmpty { "unnamed_file" }
            val safeFileName = rawFileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
            downloadsFolder.mkdirs()

            var destFile = if (fileMeta.relativePath.isNullOrEmpty()) {
                File(downloadsFolder, safeFileName)
            } else {
                val relativePathStr = fileMeta.relativePath
                val relativePath = relativePathStr!!.replace("\\", "/")
                if (relativePath.contains("..")) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val resolvedPath = downloadsFolder.toPath().resolve(relativePath).normalize()
                if (!resolvedPath.startsWith(downloadsFolder.toPath())) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val file = resolvedPath.toFile()
                file.parentFile?.mkdirs()
                file
            }

            synchronized(shareRoutesFileLock) {
                var counter = 1
                val originalName = destFile.nameWithoutExtension
                val ext = destFile.extension
                val extStr = if (ext.isNotEmpty()) ".$ext" else ""
                val parent = destFile.parentFile

                while (destFile.exists()) {
                    destFile = File(parent, "$originalName ($counter)$extStr")
                    counter++
                }
                destFile.createNewFile()
            }

            try {
                val channel: ByteReadChannel = call.receiveChannel()
                withContext(Dispatchers.IO) {
                    destFile.outputStream().use { output ->
                        channel.copyTo(output)
                    }
                }
                
                val senderAlias = sessionReq.info.alias.ifEmpty { "Device" }
                com.dexstudios.dex.core.network.services.RelayService.trackRelayFile(sessionId, safeFileName, destFile.absolutePath, senderAlias)
                
                val count = activeUploadSessionsProgress.merge(sessionId, 1) { a, b -> a + b } ?: 1
                com.dexstudios.dex.core.network.TransferStateMonitor.updateIncomingProgress(sessionId, senderAlias, sessionReq.files.size, count, count == sessionReq.files.size)

                if (count == sessionReq.files.size) {
                    activeUploadSessions.remove(sessionId)
                    activeUploadSessionsProgress.remove(sessionId)
                    
                    try {
                        if (java.awt.SystemTray.isSupported()) {
                            val tray = java.awt.SystemTray.getSystemTray()
                            val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                            val trayIcon = java.awt.TrayIcon(image, "DeX")
                            trayIcon.isImageAutoSize = true
                            tray.add(trayIcon)
                            trayIcon.displayMessage("DeX Transfer Complete", "Received $count file(s) from $senderAlias", java.awt.TrayIcon.MessageType.INFO)
                            
                            shareRouteScope.launch {
                                delay(5000)
                                try { tray.remove(trayIcon) } catch (ignored: Exception) {
                                    println("Failed to remove SystemTray notification: ${ignored.message}")
                                }
                            }
                        }
                    } catch (ignored: Exception) {
                        println("Failed to display SystemTray notification: ${ignored.message}")
                    }

                    shareRouteScope.launch {
                        delay(6000) // Keep in UI for 6s
                        com.dexstudios.dex.core.network.TransferStateMonitor.removeSession(sessionId)
                    }
                }

                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                try { if (destFile.exists()) destFile.delete() } catch (ignored: Exception) {}
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
