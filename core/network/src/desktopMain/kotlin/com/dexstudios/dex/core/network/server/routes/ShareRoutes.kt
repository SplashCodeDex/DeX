package com.dexstudios.dex.core.network.server.routes

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.TransferCheckpointRegistry
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.TransferSpeedCalculator
import com.dexstudios.dex.core.network.server.ReceiveStorage
import com.dexstudios.dex.core.network.server.guardLoopback
import com.dexstudios.dex.core.network.services.RelayReceivedFile
import com.dexstudios.dex.core.network.services.RelayService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
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

/**
 * Live upload handler per session id, so `/cancel` can actually STOP the write.
 *
 * Cancelling used to delete only the bookkeeping maps: an in-flight handler kept its captured
 * session reference, wrote to the end, committed the file, and republished completion, so a
 * cancelled transfer could still reappear as successful (and its bytes still landed). The
 * cancel route now cancels this job, which aborts the read loop, and the handler re-checks
 * cancellation before committing and before publishing completion.
 */
val activeUploadJobs = ConcurrentHashMap<String, Job>()

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
            if (!guardLoopback()) return@post
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
                // Files already present here: their paths must still ride the onward relay
                // manifest, or the A -> PC -> B hop silently loses them (see
                // RelayService.trackRelayDeduped).
                val dedupedFiles = mutableListOf<RelayReceivedFile>()
                req.files.forEach { (key, meta) ->
                    // Content-addressed dedupe: tell the sender "[SKIP]" when this exact
                    // content already arrived, instead of duplicating "name (1)" copies.
                    val existing = ReceivedFileIndex.findLive(meta.size, meta.partialHash)
                    if (existing != null) {
                        resFiles[key] = "[SKIP]"
                        dedupedFiles.add(
                            RelayReceivedFile(
                                fileName = meta.fileName.ifEmpty { "unnamed_file" },
                                absolutePath = existing,
                                relativePath = meta.relativePath,
                            ),
                        )
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
                // The relay fallback needs the expected arrival count AFTER this session
                // record dies: finishIncomingSession removes it the moment the last file
                // lands, while the sender's relay-transfer request arrives only afterwards.
                RelayService.trackRelayExpected(sessionId, issuedTokens.size)
                // Deduplicated files are never uploaded, so they are tracked separately from
                // the arrival count and merged into the onward manifest at relay time.
                RelayService.trackRelayDeduped(sessionId, dedupedFiles)

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
            val safeFileName = ReceiveStorage.sanitizeFileName(rawFileName)

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
                val rawResolved = downloadsFolder.toPath().resolve(relativePath).normalize()
                if (!rawResolved.startsWith(downloadsFolder.toPath())) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val segments = relativePath.removePrefix("/").removeSuffix("/").split("/").filter { it.isNotBlank() }
                val safeRelPath = segments.map { ReceiveStorage.sanitizeFileName(it) }.joinToString(File.separator)
                val safeResolvedPath = downloadsFolder.toPath().resolve(safeRelPath).normalize()
                val file = safeResolvedPath.toFile()
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
                destFile = ReceiveStorage.firstFreeDestination(destFile)
            }

            val resumeOffset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
            if (resumeOffset < 0L || resumeOffset > fileMeta.size) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            // Resume contract: only an EXACT match against the staged bytes may continue a
            // partial upload. The old behaviour silently downgraded a mismatched resume to a
            // from-zero write, which committed a file missing its first bytes and reported it
            // as complete. Nothing has been written yet at this point, so refusing is free.
            val stagedLength = TransferCheckpointRegistry.getExistingOffset(sessionId, fileId)
            if (resumeOffset > 0L && stagedLength != resumeOffset) {
                call.respond(HttpStatusCode.Conflict)
                return@post
            }

            val parentDir = destFile.parentFile ?: File(System.getProperty("user.home"), "Downloads/DeX")
            val partFile = TransferCheckpointRegistry.getOrCreatePartFile(parentDir, sessionId, fileId, safeFileName, fileMeta.size)
            val appendMode = resumeOffset > 0L
            val speedCalc = TransferSpeedCalculator()
            // Registered BEFORE the first byte so /cancel can abort this handler mid-write.
            val uploadJob = currentCoroutineContext().job
            activeUploadJobs[sessionId] = uploadJob

            try {
                val channel: ByteReadChannel = call.receiveChannel()
                withContext(Dispatchers.IO) {
                    java.io.FileOutputStream(partFile, appendMode).buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var received = if (appendMode) resumeOffset else 0L
                        var lastReportMs = 0L

                        while (!channel.isClosedForRead) {
                            channel.awaitContent()
                            val packet = channel.readRemaining(buffer.size.toLong())
                            if (packet.exhausted()) break
                            while (!packet.exhausted()) {
                                val n = packet.readAtMostTo(buffer, 0, buffer.size)
                                output.write(buffer, 0, n)
                                received += n
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 100) {
                                lastReportMs = now
                                val sample = speedCalc.sample(received, fileMeta.size, now)
                                val senderAlias = sessionReq.info.alias.ifEmpty { "Device" }
                                val expectedTotal = session.expectedUploads.takeIf { it >= 0 } ?: sessionReq.files.size
                                val currentDone = activeUploadSessionsProgress[sessionId] ?: 0

                                com.dexstudios.dex.core.network.TransferStateMonitor.updateIncomingProgress(
                                    sessionId = sessionId,
                                    alias = senderAlias,
                                    totalFiles = expectedTotal,
                                    filesReceived = currentDone,
                                    isComplete = false,
                                    bytesReceived = received,
                                    totalBytes = fileMeta.size,
                                    speedBps = sample.speedBps,
                                    etaSeconds = sample.etaSeconds,
                                    currentFileName = safeFileName,
                                )
                            }
                        }
                    }
                }

                // A cancel or shutdown that landed between the last byte and the commit must
                // not publish completion.
                currentCoroutineContext().ensureActive()

                // A body that ended cleanly but SHORT must never become a "completed" file:
                // the staged length is the last line of defence behind Content-Length. Staging
                // and checkpoint survive, so the sender can resume the remainder instead of
                // having a truncated file indexed, relayed and reported as delivered.
                val stagedBytes = partFile.length()
                if (fileMeta.size > 0L && stagedBytes != fileMeta.size) {
                    Logger.w("ShareRoutes: refusing truncated upload of $safeFileName: staged $stagedBytes of ${fileMeta.size} bytes")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                // Commit LAST: the staging file is promoted to a destination this PC owns
                // (never an existing file) and EVERY success side effect below is gated on the
                // result. Reporting a failed commit as a successful upload was a false success.
                val committedFile = TransferCheckpointRegistry.commitPartFile(sessionId, fileId, destFile)
                if (committedFile == null) {
                    Logger.e("ShareRoutes: commit failed for $safeFileName; staging preserved for resume")
                    com.dexstudios.dex.core.network.TransferHistoryRecorder.recordFailed(
                        name = safeFileName,
                        size = fileMeta.size,
                        direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_RECEIVED,
                        peerDevice = sessionReq.info.alias.ifEmpty { "Device" },
                    )
                    call.respond(HttpStatusCode.InternalServerError)
                    return@post
                }

                val senderAlias = sessionReq.info.alias.ifEmpty { "Device" }
                RelayService.trackRelayFile(
                    sessionId = sessionId,
                    fileName = committedFile.name,
                    absolutePath = committedFile.absolutePath,
                    senderAlias = senderAlias,
                    relativePath = fileMeta.relativePath,
                )
                ReceivedFileIndex.record(committedFile, fileMeta.size, fileMeta.partialHash)
                com.dexstudios.dex.core.network.TransferHistoryRecorder.recordCompleted(
                    name = committedFile.name,
                    size = committedFile.length(),
                    direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_RECEIVED,
                    uri = committedFile.absolutePath,
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
            } catch (e: CancellationException) {
                // The sender cancelled the transfer (or the server is shutting down). A
                // cancelled upload must never be republished as completion: drop the dashboard
                // entry, leave the staging file resumable, and let the cancellation propagate
                // so the read really stops.
                failIncomingSession(sessionId)
                throw e
            } catch (_: Exception) {
                // A failed upload must not leave a phantom transfer on the dashboard forever
                failIncomingSession(sessionId)
                val senderAlias = sessionReq.info.alias.ifEmpty { "Device" }
                com.dexstudios.dex.core.network.TransferHistoryRecorder.recordFailed(
                    name = safeFileName,
                    size = fileMeta.size,
                    direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_RECEIVED,
                    peerDevice = senderAlias,
                )
                call.respond(HttpStatusCode.InternalServerError)
            } finally {
                // Ownership-checked: a newer handler for the same session id (resume retry)
                // keeps its own registration.
                activeUploadJobs.remove(sessionId, uploadJob)
            }
        }
    }
}

/**
 * Completion path for an incoming session: removes bookkeeping and
 * lets the dashboard entry linger briefly before removal.
 */
private fun finishIncomingSession(sessionId: String, senderAlias: String, count: Int) {
    activeUploadSessions.remove(sessionId)
    activeUploadSessionsProgress.remove(sessionId)

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

    val file = File(filePath)
    if (!file.isFile) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val length = file.length()
    val mimeType = withContext(Dispatchers.IO) {
        java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    }
    call.respondBytesWriter(contentType = io.ktor.http.ContentType.parse(mimeType), contentLength = length) {
        val downloadJob = currentCoroutineContext().job
        if (!RelayService.registerDownload(fileId, downloadJob)) throw CancellationException("Hosted transfer revoked")
        try {
            withContext(Dispatchers.IO) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var remaining = length
                    while (remaining > 0L) {
                        currentCoroutineContext().ensureActive()
                        if (!RelayService.touchHosted(fileId)) throw CancellationException("Hosted transfer revoked")
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) throw java.io.EOFException("Hosted file changed during download")
                        writeFully(buffer, 0, count)
                        remaining -= count
                    }
                    flush()
                    currentCoroutineContext().ensureActive()
                    RelayService.markPulled(fileId)
                }
            }
        } finally {
            RelayService.unregisterDownload(fileId, downloadJob)
        }
    }
}
