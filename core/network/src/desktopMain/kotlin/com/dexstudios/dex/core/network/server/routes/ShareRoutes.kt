package com.dexstudios.dex.core.network.server.routes

import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.server.ReceiveStorage
import com.dexstudios.dex.core.network.services.RelayService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ShareTargetPayload(val files: List<String>, val targetFingerprint: String? = null)

/**
 * Receiver-side dedupe index: content key -> absolute path of the already-received file.
 * Keyed by (size, partialHash) exactly as senders fingerprint their files; files without a
 * partialHash are never indexed, so empty/unknown-content payloads always get fresh names.
 */
object ReceivedFileIndex {
    private val byKey = ConcurrentHashMap<String, String>()

    private fun key(size: Long, partialHash: String?): String? {
        if (partialHash.isNullOrEmpty()) return null
        return "$size:$partialHash"
    }

    /** Returns the stored path when identical content was already received, else null. */
    fun find(size: Long, partialHash: String?): String? = key(size, partialHash)?.let { byKey[it] }

    fun record(file: File, size: Long, partialHash: String?) {
        val k = key(size, partialHash) ?: return
        byKey[k] = file.absolutePath
    }

    /** A previously-indexed file may have been deleted by the user; drop dead entries lazily. */
    private fun live(path: String): Boolean = File(path).exists()

    fun findLive(size: Long, partialHash: String?): String? {
        val path = find(size, partialHash) ?: return null
        if (!live(path)) {
            byKey.remove(key(size, partialHash)!!)
            return null
        }
        return path
    }

    fun clear() = byKey.clear()
}

data class SessionEntry(
    val request: PrepareUploadRequestDto,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * fileId -> issued per-file pull/upload token for THIS session. Null only for sessions
     * constructed outside the real prepare flow (tests/legacy); token enforcement is skipped
     * when null so hand-built sessions keep working.
     */
    val issuedTokens: Map<String, String>? = null,
    /** Bearer token the sender authenticated with; gates /cancel ownership. */
    val ownerToken: String? = null,
) {
    /** Files the sender will actually upload ([SKIP]-deduped ones excluded); -1 when unknown. */
    val expectedUploads: Int get() = issuedTokens?.size ?: -1
}

val activeUploadSessions = ConcurrentHashMap<String, SessionEntry>()
val activeUploadSessionsProgress = ConcurrentHashMap<String, Int>()

/** Constant-time string equality for bearer/pull-token checks (length pre-checked). */
private fun tokenEquals(presented: String?, expected: String?): Boolean {
    if (presented.isNullOrEmpty() || expected.isNullOrEmpty()) return false
    return presented.length == expected.length &&
        MessageDigest.isEqual(presented.toByteArray(), expected.toByteArray())
}

private val shareRoutesFileLock = Any()

/** Removes every trace of an incoming session: session store, progress counters, dashboard entry. */
fun failIncomingSession(sessionId: String) {
    activeUploadSessions.remove(sessionId)
    activeUploadSessionsProgress.remove(sessionId)
    com.dexstudios.dex.core.network.TransferStateMonitor.removeSession(sessionId)
}

/** Shared IO scope for fire-and-forget route work (toasts, cleanup delays, janitor). */
val shareRouteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Self-parking TTL sweeper for incoming upload sessions — same lifecycle pattern as
 * RelayService.ensureMaintenanceLoop: started on demand when a session is registered,
 * exits when nothing is left to watch instead of burning a timer forever.
 */
private val janitorRunning = java.util.concurrent.atomic.AtomicBoolean(false)

internal fun ensureSessionJanitor() {
    if (janitorRunning.compareAndSet(false, true)) {
        shareRouteScope.launch {
            while (true) {
                delay(60_000) // 1 minute
                val now = System.currentTimeMillis()
                val expired = activeUploadSessions.entries
                    .filter { now - it.value.createdAt > 10 * 60_000 }
                    .map { it.key }
                // TTL sweeper must also clear progress + dashboard state, not just the session map
                for (id in expired) failIncomingSession(id)
                if (activeUploadSessions.isEmpty()) break
            }
            janitorRunning.set(false)
            // Re-check under race: a session may have arrived between the check and the reset
            if (activeUploadSessions.isNotEmpty()) ensureSessionJanitor()
        }
    }
}

fun Route.shareRoutes() {
    hostedDownloadRoutes()

    route("/local") {
        post("/share-target") {
            // OS share-target integration is a LOCAL automation surface; it must never be
            // reachable from the network listeners. The TLS listener serves 0.0.0.0, the
            // maintenance listener 127.0.0.1 — gate on the local bind address.
            if (call.request.local.serverHost != "127.0.0.1" && call.request.local.serverHost != "::1" &&
                call.request.local.serverHost != "0:0:0:0:0:0:0:1"
            ) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }
            try {
                val payload = call.receive<ShareTargetPayload>()

                // The route cannot guess a destination: without a target the relay no-ops.
                val target = payload.targetFingerprint
                if (target.isNullOrBlank()) {
                    call.respond(HttpStatusCode.UnprocessableEntity, "targetFingerprint is required")
                    return@post
                }

                val fileList = payload.files.map { Pair(it, null as String?) }
                RelayService.hostAndPushAsync(
                    targetFingerprint = target,
                    files = fileList,
                    senderAlias = System.getProperty("user.name") ?: "PC",
                )
                call.respond(HttpStatusCode.OK)
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    route("/api/localsend/v2") {
        post("/prepare-upload") {
            try {
                val req = call.receive<PrepareUploadRequestDto>()

                val authHeader = call.request.header("Authorization")
                val token = authHeader?.removePrefix("Bearer ")?.trim()

                val koin = org.koin.core.context.GlobalContext.get()
                val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()

                val isAutoTrusted = tokenEquals(token, deviceConfig.identityHash) ||
                    (deviceConfig.googleSub.isNotEmpty() && tokenEquals(token, deviceConfig.googleSub))

                val pairedTokens = com.dexstudios.dex.auth.AuthState.pairedTokens.value
                val isPaired = tokenEquals(token, pairedTokens[req.info.fingerprint])

                if (!isAutoTrusted && !isPaired) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }

                // Do Not Disturb intentionally does NOT refuse transfers: it mutes the
                // alerting layer (DesktopPlatformEngine) while files still arrive silently.

                val downloadsFolder = ReceiveStorage.downloadsDir()

                // Validate capacity BEFORE registering anything, otherwise rejected requests
                // leak dashboard entries until the TTL sweep
                val totalSize = req.files.values.sumOf { it.size }
                if (downloadsFolder.freeSpace < totalSize) {
                    call.respond(HttpStatusCode.InsufficientStorage)
                    return@post
                }

                val sessionId = UUID.randomUUID().toString()
                val issuedTokens = mutableMapOf<String, String>()
                val resFiles = mutableMapOf<String, String>()
                req.files.forEach { (key, meta) ->
                    // Content-addressed dedupe: tell the sender "[SKIP]" when this exact
                    // content already arrived, instead of duplicating "name (1)" copies.
                    val existing = ReceivedFileIndex.findLive(meta.size, meta.partialHash)
                    if (existing != null) {
                        resFiles[key] = "[SKIP]"
                    } else {
                        val fresh = UUID.randomUUID().toString()
                        resFiles[key] = fresh
                        issuedTokens[key] = fresh
                    }
                }

                activeUploadSessions[sessionId] = SessionEntry(
                    request = req,
                    issuedTokens = issuedTokens,
                    ownerToken = token?.takeIf { it.isNotEmpty() },
                )
                ensureSessionJanitor()

                com.dexstudios.dex.core.network.TransferStateMonitor.updateIncomingProgress(
                    sessionId,
                    req.info.alias.ifEmpty { "Device" },
                    issuedTokens.size,
                    0,
                )
                if (issuedTokens.isEmpty()) {
                    // Everything deduped away: nothing will ever be uploaded; close cleanly
                    // without the completion toast.
                    shareRouteScope.launch {
                        delay(6000)
                        com.dexstudios.dex.core.network.TransferStateMonitor.removeSession(sessionId)
                        activeUploadSessions.remove(sessionId)
                    }
                }

                call.respond(PrepareUploadResponseDto(sessionId = sessionId, files = resFiles))
            } catch (_: Exception) {
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

            val session = activeUploadSessions[sessionId]
            val sessionReq = session?.request
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

            val downloadsFolder = ReceiveStorage.downloadsDir()

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

            // Per-file token proof: the presented query token must equal the one minted at
            // prepare time, so another authenticated peer cannot inject into this session.
            // Sessions created outside prepare (issuedTokens == null) stay unenforced.
            val issued = session.issuedTokens
            if (issued != null) {
                val expected = issued[fileId]
                val presented = call.request.queryParameters["token"]
                if (expected == null || !tokenEquals(presented, expected)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
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
                        // Large staged pump: bounded readRemaining packets keep syscall
                        // counts low on multi-GB uploads
                        val buffer = ByteArray(256 * 1024)
                        var received = 0L
                        while (!channel.isClosedForRead) {
                            channel.awaitContent()
                            val packet = channel.readRemaining(buffer.size.toLong())
                            if (packet.exhausted()) break
                            while (!packet.exhausted()) {
                                val n = packet.readAtMostTo(buffer, 0, buffer.size)
                                output.write(buffer, 0, n)
                                received += n
                            }
                        }
                    }
                }

                val senderAlias = sessionReq.info.alias.ifEmpty { "Device" }
                RelayService.trackRelayFile(
                    sessionId = sessionId,
                    fileName = safeFileName,
                    absolutePath = destFile.absolutePath,
                    senderAlias = senderAlias,
                    relativePath = fileMeta.relativePath,
                )
                ReceivedFileIndex.record(destFile, fileMeta.size, fileMeta.partialHash)
                TransferHistory.log(
                    name = destFile.name,
                    size = destFile.length(),
                    direction = "received",
                    uri = destFile.absolutePath,
                    peerDevice = senderAlias,
                )

                val count = activeUploadSessionsProgress.merge(sessionId, 1) { a, b -> a + b } ?: 1
                val expectedTotal = session.expectedUploads.takeIf { it >= 0 } ?: sessionReq.files.size
                com.dexstudios.dex.core.network.TransferStateMonitor.updateIncomingProgress(
                    sessionId,
                    senderAlias,
                    expectedTotal,
                    count,
                    count >= expectedTotal,
                )

                if (count >= expectedTotal) {
                    finishIncomingSession(sessionId, senderAlias, count)
                }

                call.respond(HttpStatusCode.OK)
            } catch (_: Exception) {
                runCatching { if (destFile.exists()) destFile.delete() }
                // A failed upload must not leave a phantom transfer on the dashboard forever
                failIncomingSession(sessionId)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

/**
 * Completion path for an incoming session: removes bookkeeping, shows the tray toast and
 * lets the dashboard entry linger briefly before removal.
 */
private fun finishIncomingSession(sessionId: String, senderAlias: String, count: Int) {
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
                runCatching { tray.remove(trayIcon) }
            }
        }
    } catch (_: Exception) {
    }

    shareRouteScope.launch {
        delay(6000) // Keep in UI for 6s
        com.dexstudios.dex.core.network.TransferStateMonitor.removeSession(sessionId)
    }
}

/**
 * GET endpoints serving PC-hosted files to pulling peers (phone HTTP/3 pulls, HTTP fallback
 * and desktop pull-service). Both the v2 and legacy paths share one handler; each keeps its
 * historical status-code semantics for missing/bad tokens.
 *
 * Every successful serve reports [RelayService.markPulled] so hosted-push completion can fire.
 * Registered on the TLS listener AND as the ONLY routes on the plain-HTTP 48426 fallback.
 */
fun Route.hostedDownloadRoutes() {
    route("/api/localsend/v2") {
        get("/download") {
            respondHostedFile(call, legacyTokenSemantics = false)
        }
    }

    // Legacy route preservation for older clients
    get("/download/{fileId}") {
        respondHostedFile(call, legacyTokenSemantics = true)
    }
}

private suspend fun respondHostedFile(call: ApplicationCall, legacyTokenSemantics: Boolean) {
    val sessionId = call.request.queryParameters["sessionId"]
    val fileId = if (legacyTokenSemantics) call.parameters["fileId"] else call.request.queryParameters["fileId"]
    val token = call.request.queryParameters["token"]

    if (fileId == null || token == null) {
        call.respond(HttpStatusCode.BadRequest)
        return
    }

    val expectedToken = RelayService.hostedFileTokens[fileId]
    val filePath = RelayService.hostedFiles[fileId]

    val tokenOk = tokenEquals(token, expectedToken)
    if (!tokenOk || filePath == null) {
        call.respond(if (legacyTokenSemantics) HttpStatusCode.NotFound else HttpStatusCode.Forbidden)
        return
    }

    RelayService.markPulled(fileId)
    val file = File(filePath)
    if (!file.exists()) {
        call.respond(HttpStatusCode.NotFound)
        return
    }

    call.respondFile(file)
}
