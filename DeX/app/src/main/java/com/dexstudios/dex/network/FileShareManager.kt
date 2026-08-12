package com.dexstudios.dex.network

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The phone side of the PC File Explorer. The phone runs no HTTP server anymore, so the
 * PC browses the phone's SAF-granted shared folders over the existing WebSocket: the PC
 * sends a request (with a requestId), and this manager replies (echoing the requestId)
 * after listing a folder or pushing the requested file back via the standard upload path.
 */
class FileShareManager(
    private val deviceConfig: DeviceConfig,
    private val client: ClientEngine,
    private val context: Context
) : KoinComponent {
    private val wsService: WebSocketClientService by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    /** requestId -> true once the PC asks us to abort that pull. */
    private val cancelledRequests = ConcurrentHashMap<String, Boolean>()

    /** Per-request throttle so live progress messages don't flood the socket. */
    private val lastProgressReport = ConcurrentHashMap<String, AtomicLong>()

    /** Handles an incoming PC request; [data] is the JSON `data` object of the message. */
    fun handleRequest(type: String, data: JsonObject) {
        val requestId = data["requestId"]?.jsonPrimitive?.content ?: return
        when (type) {
            "list-shared-folders" -> replyList(requestId)
            "browse-folder" -> replyBrowse(requestId, data)
            "pull-files" -> scope.launch { replyPull(requestId, data) }
            "pull-cancel" -> cancelledRequests[requestId] = true
            "grant-shared-folder" -> scope.launch { replyGrant(requestId) }
            else -> Timber.w("Unknown FileShare request: $type")
        }
    }

    /** Every granted folder the user can browse: the default Downloads/DeX plus extras. */
    private fun sharedFolders(): List<SharedFolderDto> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SharedFolderDto>()
        val downloads = SafStorage.getDownloadsDexUri(context)
        if (downloads != null && seen.add(downloads.toString())) {
            result.add(SharedFolderDto(
                id = "downloads",
                name = SafStorage.sharedFolderName(context, downloads),
                uri = downloads.toString()
            ))
        }
        for (uriString in SafStorage.listSharedFolderUris(context)) {
            if (!seen.add(uriString)) continue
            result.add(SharedFolderDto(
                id = UUID.randomUUID().toString(),
                name = SafStorage.sharedFolderName(context, uriString.toUri()),
                uri = uriString
            ))
        }
        return result
    }

    private fun replyList(requestId: String) {
        val folders = sharedFolders()
        Timber.i("Replying with ${folders.size} shared folders (request $requestId)")
        send(buildJsonObject {
            put("type", "list-shared-folders-reply")
            putJsonObject("data") {
                put("requestId", requestId)
                putJsonObject("folders") { folders.forEach { f ->
                    putJsonObject(f.id) {
                        put("name", f.name)
                        put("uri", f.uri)
                    }
                } }
            }
        }.toString())
    }

    private fun replyBrowse(requestId: String, data: JsonObject) {
        val folderUri = data["folderUri"]?.jsonPrimitive?.content
        val entries = if (folderUri.isNullOrBlank()) emptyList() else {
            runCatching { SafStorage.listFolderEntries(context, folderUri.toUri()) }.getOrElse {
                Timber.e(it, "Browse failed for $folderUri")
                emptyList()
            }
        }
        send(buildJsonObject {
            put("type", "browse-reply")
            putJsonObject("data") {
                put("requestId", requestId)
                putJsonObject("entries") {
                    var thumbCount = 0
                    entries.forEach { e ->
                        putJsonObject(e.uri) {
                            put("name", e.name)
                            put("isDirectory", e.isDirectory)
                            put("size", e.size)
                            val isImage = !e.isDirectory && e.size in 1..THUMB_MAX_BYTES &&
                                e.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS
                            val thumb = if (isImage && thumbCount < THUMB_CAP) { thumbCount++; thumbnailFor(e) } else null
                            put("thumb", thumb ?: "")
                        }
                    }
                }
            }
        }.toString())
    }

    /** Base64 JPEG thumbnail for small image files, so phone files render like local ones. */
    private fun thumbnailFor(e: FolderEntryDto): String? {
        if (e.isDirectory || e.size > THUMB_MAX_BYTES || e.size <= 0) return null
        val ext = e.name.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTS) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(e.uri.toUri())?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val w = bounds.outWidth
            val h = bounds.outHeight
            while (w / (sample * 2) >= THUMB_SIZE && h / (sample * 2) >= THUMB_SIZE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(e.uri.toUri())?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val scaled = bmp.scale(THUMB_SIZE, THUMB_SIZE, true)
            if (bmp !== scaled) bmp.recycle()
            val out = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
            val bytes = out.toByteArray()
            scaled.recycle()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    /** The PC asks the phone to push the selected file(s) back to it (its own download). */
    private suspend fun replyPull(requestId: String, data: JsonObject) = withContext(Dispatchers.IO) {
        val fileObjects = data["files"] ?: return@withContext
        val files = runCatching { json.decodeFromJsonElement<List<PullFileDto2>>(fileObjects) }
            .getOrElse { emptyList() }
            .filter { it.uri.isNotBlank() }

        if (files.isEmpty()) {
            sendPullReply(requestId, emptyList(), emptyList(), cancelled = false)
            return@withContext
        }

        val pcIp = wsService.connectedIp ?: PcMemory.ip(context)
        if (pcIp == null) {
            sendPullReply(requestId, emptyList(), files.map { resolveName(it) to "no connection" }, cancelled = false)
            return@withContext
        }
        val port = wsService.connectedPort
        val token = client.authToken(wsService.connectedFingerprint, null, null)

        val fileMeta = files.mapIndexed { index, f ->
            index.toString() to FileMeta(f.uri, resolveName(f), f.size)
        }.toMap()
        val totalBytes = fileMeta.values.sumOf { it.size }.coerceAtLeast(1)

        val prepareRequest = PrepareUploadRequestDto(
            info = RegisterDto(
                alias = getDeviceName(context), version = "2.0", deviceModel = android.os.Build.MODEL ?: "Android",
                deviceType = "mobile", fingerprint = deviceConfig.fingerprint,
                port = DeXPorts.HTTPS, protocol = "https", download = false,
                identityHash = deviceConfig.identityHash
            ),
            files = fileMeta.mapValues { (_, m) ->
                FileDto(UUID.randomUUID().toString(), m.name, m.size, "application/octet-stream")
            }
        )
        val response = client.prepareUpload(pcIp, port, prepareRequest, token).response

        if (response == null) {
            sendPullReply(requestId, emptyList(), files.map { resolveName(it) to "prepare failed" }, cancelled = false)
            return@withContext
        }

        val sentBytes = AtomicLong(0L)
        val doneCount = AtomicInteger(0)
        val saved = CopyOnWriteArrayList<String>()
        val failed = ConcurrentHashMap<String, String>()
        val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)
        var cancelled = false

        // Hold a foreground service so Android doesn't kill the process mid-pull.
        PullForegroundService.start(context, requestId, fileMeta.size)

        coroutineScope {
            val jobs = fileMeta.map { (key, m) ->
                launch(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        if (!scope.isActive || isStopped(requestId)) { cancelled = true; return@launch }
                        val fileId = prepareRequest.files.getValue(key).id
                        val fileToken = response.files[fileId]

                        if (fileToken == null || fileToken == "[SKIP]") {
                            saved.add(m.name)
                            doneCount.incrementAndGet()
                            TransferHistory.log(context, m.name, m.size, "sent", m.uri)
                            reportProgress(requestId, doneCount.get(), fileMeta.size, sentBytes.get(), totalBytes, m.name)
                            return@launch
                        }

                        // Per-file retry: re-attempt a failed file once before reporting it.
                        var ok = false
                        var reason = "upload failed"
                        for (attempt in 1..MAX_FILE_RETRIES) {
                            if (!scope.isActive || isStopped(requestId)) { cancelled = true; reason = "cancelled"; break }
                            val stream = runCatching { context.contentResolver.openInputStream(m.uri.toUri()) }.getOrNull()
                            if (stream == null) { reason = "unreadable"; break }
                            val outcome = stream.use { input ->
                                if (client.quicAvailable()) {
                                    client.uploadFileQuic(pcIp, port, response.sessionId, fileId, m.name, fileToken, input, m.size) { d ->
                                        sentBytes.addAndGet(d)
                                        reportProgress(requestId, doneCount.get(), fileMeta.size, sentBytes.get(), totalBytes, m.name)
                                    }
                                } else {
                                    client.uploadFile(pcIp, port, response.sessionId, fileId, m.name, fileToken, input, m.size) { d ->
                                        sentBytes.addAndGet(d)
                                        reportProgress(requestId, doneCount.get(), fileMeta.size, sentBytes.get(), totalBytes, m.name)
                                    }
                                }
                            }
                            if (outcome.ok) { ok = true; break }
                            reason = if (outcome.httpStatus == -1) "network error" else "http ${outcome.httpStatus}"
                        }

                        doneCount.incrementAndGet()
                        if (ok) {
                            saved.add(m.name)
                            TransferHistory.log(context, m.name, m.size, "sent", m.uri)
                        } else {
                            failed[m.name] = reason
                        }
                        reportProgress(requestId, doneCount.get(), fileMeta.size, sentBytes.get(), totalBytes, m.name)
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        sendPullReply(requestId, saved.toList(), failed.entries.map { it.key to it.value }, cancelled)
    }

    private fun isStopped(requestId: String): Boolean = cancelledRequests[requestId] == true

    /** Throttled live progress so the PC can render a progress dock. */
    private fun reportProgress(
        requestId: String,
        doneFiles: Int,
        totalFiles: Int,
        sentBytes: Long,
        totalBytes: Long,
        currentFile: String
    ) {
        val now = System.currentTimeMillis()
        val last = lastProgressReport.getOrPut(requestId) { AtomicLong(0L) }
        if (sentBytes < totalBytes && now - last.get() < 200) return
        last.set(now)
        send(buildJsonObject {
            put("type", "pull-progress")
            putJsonObject("data") {
                put("requestId", requestId)
                put("doneFiles", doneFiles)
                put("totalFiles", totalFiles)
                put("sentBytes", sentBytes)
                put("totalBytes", totalBytes)
                put("currentFile", currentFile)
                put("state", "running")
            }
        }.toString())
    }

    private fun sendPullReply(requestId: String, saved: List<String>, failed: List<Pair<String, String>>, cancelled: Boolean) {
        cancelledRequests.remove(requestId)
        lastProgressReport.remove(requestId)
        PullForegroundService.stop(context)
        // Final progress frame so the PC can close the dock with the terminal state.
        send(buildJsonObject {
            put("type", "pull-progress")
            putJsonObject("data") {
                put("requestId", requestId)
                put("state", when {
                    cancelled -> "cancelled"
                    failed.isEmpty() -> "done"
                    else -> "failed"
                })
                put("doneFiles", saved.size)
                put("totalFiles", saved.size + failed.size)
            }
        }.toString())
        send(buildJsonObject {
            put("type", "pull-reply")
            putJsonObject("data") {
                put("requestId", requestId)
                put("success", failed.isEmpty() && !cancelled)
                put("cancelled", cancelled)
                putJsonObject("saved") { saved.forEach { put(it, true) } }
                putJsonObject("failed") { failed.forEach { put(it.first, it.second) } }
            }
        }.toString())
    }

    /** SAF display name for a file, falling back to the name the PC provided. */
    private fun resolveName(f: PullFileDto2): String {
        return runCatching {
            context.contentResolver.query(f.uri.toUri(), null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0) c.getString(n) ?: f.name else f.name
                } else f.name
            } ?: f.name
        }.getOrNull() ?: f.name
    }

    private suspend fun replyGrant(requestId: String) {
        // Open the SAF picker and wait for the user to pick (or cancel) a folder.
        val deferred = SharedFolderGrantState.register()
        SafStorage.promptForSharedFolderGrant(context)
        val granted = withTimeoutOrNull(GRANT_WAIT_MS) { deferred.await() }
        val name = granted?.name ?: ""
        val uri = granted?.uri ?: ""
        send(buildJsonObject {
            put("type", "grant-reply")
            putJsonObject("data") {
                put("requestId", requestId)
                put("granted", granted != null)
                put("name", name)
                put("uri", uri)
            }
        }.toString())
    }

    private fun send(message: String) {
        wsService.sendMessage(message)
    }

    private companion object {
        const val GRANT_WAIT_MS = 180_000L
        const val MAX_CONCURRENT_UPLOADS = 3
        const val MAX_FILE_RETRIES = 2
        const val THUMB_SIZE = 128
        const val THUMB_MAX_BYTES = 512L * 1024L
        const val THUMB_CAP = 30
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    }
}

/** Coordinates the SAF picker (opened in MainActivity) with the FileShare grant request. */
object SharedFolderGrantState {
    private val lock = Any()
    var pending: CompletableDeferred<SharedFolderDto?>? = null

    /** Registers a fresh deferred that the SAF picker completes when the user decides. */
    fun register(): CompletableDeferred<SharedFolderDto?> {
        val deferred = CompletableDeferred<SharedFolderDto?>()
        synchronized(lock) { pending = deferred }
        return deferred
    }

    /** The SAF picker returned; complete the pending request (null when cancelled). */
    fun complete(uri: Uri?, name: String) {
        synchronized(lock) {
            pending?.complete(if (uri != null) SharedFolderDto(name = name, uri = uri.toString()) else null)
            pending = null
        }
    }
}

/** Resolved details for a file the PC wants pulled back. */
private data class FileMeta(val uri: String, val name: String, val size: Long)
