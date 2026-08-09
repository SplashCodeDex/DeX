package com.dexstudios.dex.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Direct phone-to-phone transfers over a NAT-punched TCP connection, mediated by the
 * user's own PC (the rendezvous): the PC reflects each phone's public endpoint, the
 * two phones punch through their NATs with simultaneous-open connects, and the file
 * stream flows peer-to-peer — no relay, no cloud copies.
 *
 * Same-email only: the receiver verifies the sender's identity hash before accepting.
 * If the punch fails (symmetric NAT / CGNAT) the transfer reports a clear error.
 */
class PunchSession(
    private val deviceConfig: DeviceConfig,
    private val wsService: WebSocketClientService,
    private val notificationHelper: NotificationHelper,
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val json = Json { ignoreUnknownKeys = true }

    @android.annotation.SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val sslContext: SSLContext by lazy {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(trustAllManager), SecureRandom())
        ctx
    }

    fun start() {
        if (serverSocket != null) return
        try {
            serverSocket = ServerSocket(0)
            Timber.i("Punch listener open on port ${serverSocket!!.localPort}")
        } catch (e: Exception) {
            Timber.e(e, "Cannot open punch listener")
            return
        }
        scope.launch {
            // Register our public endpoint and refresh before the PC's 5-minute TTL expires
            while (isActive) {
                registerEndpoint()
                delay(120_000)
            }
        }
        scope.launch {
            // Prune stale resume sessions (dropped transfers we gave up waiting for)
            while (isActive) {
                delay(60_000)
                PunchResumeState.prune()
            }
        }
        scope.launch { receiveLoop() }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
    }

    /**
     * Reflects our public TCP endpoint via the PC: we connect FROM the listener port, so
     * the PC answers with the source address of this connection — our NAT public endpoint.
     */
    private suspend fun registerEndpoint() = withContext(Dispatchers.IO) {
        val ss = serverSocket ?: return@withContext
        try {
            val pcIp = wsService.connectedIp ?: PcMemory.ip(context) ?: return@withContext
            val localPort = ss.localPort
            val socket = Socket()
            socket.bind(InetSocketAddress("0.0.0.0", localPort))
            socket.connect(InetSocketAddress(pcIp, 53317), 5000)
            val ssl = sslContext.socketFactory.createSocket(socket, pcIp, 53317, true) as SSLSocket
            ssl.startHandshake()
            val request = "GET /punch/endpoint?fingerprint=${URLEncoder.encode(deviceConfig.fingerprint, "UTF-8")} HTTP/1.1\r\n" +
                "Host: $pcIp\r\nConnection: close\r\n\r\n"
            ssl.getOutputStream().write(request.toByteArray())
            ssl.getOutputStream().flush()
            val body = String(ssl.getInputStream().readBytes()).substringAfter("\r\n\r\n")
            val reflected = json.decodeFromString<EndpointInfoDto>(body)
            ssl.close()
            Timber.i("Punch endpoint registered: ${reflected.ip}:${reflected.port}")
        } catch (e: Exception) {
            Timber.e(e, "Punch endpoint registration failed")
        }
    }

    // ---- Receiver side ----

    private suspend fun receiveLoop() {
        val ss = serverSocket ?: return
        while (scope.isActive) {
            try {
                val socket = withContext(Dispatchers.IO) { ss.accept() }
                scope.launch { handleIncoming(socket) }
            } catch (e: Exception) {
                if (scope.isActive) Timber.e(e, "Punch accept failed")
            }
        }
    }

    private suspend fun handleIncoming(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val manifestLine = withTimeoutOrNull(10_000) { withContext(Dispatchers.IO) { readLine(input) } }
                ?: return closeQuietly(socket)
            val manifest = json.decodeFromString<PunchManifestDto>(manifestLine)

            // Same-email only: the sender's identity must match ours
            if (manifest.identityHash.isBlank() || manifest.identityHash != deviceConfig.identityHash) {
                writeLine(output, """{"type":"reject","reason":"identity"}""")
                Timber.w("Rejected punch transfer from non-same-email device ${manifest.alias}")
                return closeQuietly(socket)
            }

            val sessionId = manifest.sessionId
            val resumeMap = PunchResumeState.mapFor(sessionId)

            // Only prompt for sessions we have not already accepted (auto-resume skips the prompt)
            if (!PunchResumeState.isAccepted(sessionId)) {
                val deferred = CompletableDeferred<Boolean>()
                TransferState.pendingPrompts[sessionId] = deferred
                val notificationId = sessionId.hashCode()
                notificationHelper.showIncomingFileNotification(sessionId, notificationId, manifest.files.size)

                val accepted = withTimeoutOrNull(60_000) { deferred.await() } == true
                TransferState.pendingPrompts.remove(sessionId)
                if (!accepted) {
                    writeLine(output, """{"type":"reject","reason":"declined"}""")
                    return closeQuietly(socket)
                }
                PunchResumeState.markAccepted(sessionId)
            }

            // Tell the sender how many bytes we already have per file, so it resumes — not restarts
            val resumeInfo = PunchResumeInfoDto(
                sessionId = sessionId,
                files = manifest.files.associate { file -> file.id to (resumeMap[file.id]?.received ?: 0L) }
            )
            writeLine(output, json.encodeToString(resumeInfo))

            var dirUri = SafStorage.getDownloadsDexUri(context)
            if (dirUri == null) {
                SafStorage.promptForDownloadsDexGrant(context)
                val deadline = System.currentTimeMillis() + 180_000
                while (System.currentTimeMillis() < deadline) {
                    delay(500)
                    dirUri = SafStorage.getDownloadsDexUri(context)
                    if (dirUri != null) break
                }
                if (dirUri == null) return closeQuietly(socket)
            }

            var doneFiles = manifest.files.count { file -> (resumeMap[file.id]?.received ?: 0L) >= file.size }
            for (file in manifest.files) {
                val headerLine = withTimeoutOrNull(30_000) { withContext(Dispatchers.IO) { readLine(input) } }
                    ?: break
                val header = json.decodeFromString<PunchFileHeaderDto>(headerLine)

                val existing = resumeMap[file.id]
                if (existing != null && existing.received >= existing.size) {
                    doneFiles++
                    continue
                }

                // Folder bundles: recreate the relative path structure under Downloads/DeX
                val docUri = existing?.docUri ?: if (!file.relativePath.isNullOrBlank()) {
                    SafStorage.createDocumentWithPath(context, dirUri, file.relativePath)
                } else {
                    SafStorage.createDocumentUri(context, dirUri, file.fileName)
                } ?: continue
                val out = context.contentResolver.openOutputStream(docUri, "wa")
                    ?: continue
                if (existing == null) resumeMap[file.id] = ResumeEntry(docUri, 0L, file.size)

                var received = resumeMap[file.id]!!.received
                try {
                    val buffer = ByteArray(64 * 1024)
                    while (received < header.size) {
                        val toRead = minOf(buffer.size.toLong(), header.size - received).toInt()
                        val n = input.read(buffer, 0, toRead)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        received += n
                        resumeMap[file.id]!!.received = received
                    }
                    if (received < header.size) break // dropped mid-file — the sender will resume
                    doneFiles++
                    TransferHistory.log(context, file.fileName, received, "received", docUri.toString())
                    TcpDownloadService.updateState(
                        DownloadState(
                            fileName = if (manifest.files.size == 1) file.fileName else "$doneFiles of ${manifest.files.size} files",
                            progress = doneFiles.toFloat() / manifest.files.size,
                            isDownloading = true,
                            doneFiles = doneFiles,
                            totalFiles = manifest.files.size,
                            protocol = "direct"
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Punch receive failed for ${file.fileName}")
                    break // keep the resume entry so a retry can append
                } finally {
                    out.close()
                }
            }

            // Await the sender's completion marker, then clear the resume state
            withTimeoutOrNull(10_000) { withContext(Dispatchers.IO) { readLine(input) } }
            PunchResumeState.complete(sessionId)
            TcpDownloadService.updateState(DownloadState(fileName = "$doneFiles of ${manifest.files.size} files", progress = 1f, isSuccess = true, doneFiles = doneFiles, totalFiles = manifest.files.size))
            Timber.i("Punch transfer received: $doneFiles files from ${manifest.alias}")
        } catch (e: Exception) {
            Timber.e(e, "Punch receive session failed")
        } finally {
            closeQuietly(socket)
        }
    }

    // ---- Sender side ----

    private val maxTransferAttempts = 3

    /** Sends [uris] directly to the target device via a punched connection. Returns error text or null on success. */
    suspend fun sendTo(
        targetFingerprint: String,
        uris: List<Uri>,
        relativePaths: List<String>? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: suspend (Float, String) -> Unit = { _, _ -> }
    ): String? = withContext(Dispatchers.IO) {
        // One session across retries: a dropped connection resumes from the last received byte
        val sessionId = UUID.randomUUID().toString()

        val files = uris.mapIndexed { index, uri ->
            var name = "shared_file"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            PunchFileDto(
                id = UUID.randomUUID().toString(),
                fileName = name,
                size = size,
                relativePath = relativePaths?.getOrNull(index)
            )
        }
        val totalSize = files.sumOf { it.size }

        var lastError: String? = null
        repeat(maxTransferAttempts) { attempt ->
            if (isCancelled()) return@withContext "Transfer cancelled"
            if (attempt > 0) delay(1500)

            registerEndpoint()

            // 1. Ask the PC for the target's public endpoint (and give it ours)
            val deferred = CompletableDeferred<EndpointInfoDto>()
            PunchState.pendingEndpointInfo.value = deferred
            wsService.sendMessage(
                buildJsonObject {
                    put("type", "resolve-endpoint")
                    putJsonObject("data") { put("targetFingerprint", targetFingerprint) }
                }.toString()
            )
            val info = withTimeoutOrNull(15_000) { deferred.await() }
            PunchState.pendingEndpointInfo.value = null
            if (info == null || info.ip.isBlank() || info.port <= 0) {
                return@withContext "The target device is offline or not registered"
            }

            // 2. Punch through the NATs (simultaneous-open)
            val socket = punch(info.ip, info.port, isCancelled)
            if (socket == null) {
                lastError = "Direct connection failed — your network blocks punching"
                return@withContext lastError
            }

            // 3. Transfer with resume support; a mid-transfer drop retries the whole session
            try {
                val outcome = runTransfer(socket, sessionId, uris, files, totalSize, isCancelled, onProgress)
                when (outcome) {
                    TransferOutcome.SUCCESS -> return@withContext null
                    TransferOutcome.REJECTED -> return@withContext "The recipient declined the transfer"
                    TransferOutcome.DROP -> {
                        lastError = "Connection lost — resuming transfer"
                        Timber.i("Punch connection dropped mid-transfer; retrying (attempt ${attempt + 1}/$maxTransferAttempts)")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Punch transfer failed; retrying")
                lastError = "Connection lost — resuming transfer"
            } finally {
                closeQuietly(socket)
            }
        }
        lastError ?: "Transfer failed"
    }

    private enum class TransferOutcome { SUCCESS, REJECTED, DROP }

    private suspend fun runTransfer(
        socket: Socket,
        sessionId: String,
        uris: List<Uri>,
        files: List<PunchFileDto>,
        totalSize: Long,
        isCancelled: () -> Boolean,
        onProgress: suspend (Float, String) -> Unit
    ): TransferOutcome {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()

        // Announce the transfer (with the resume session id)
        val manifest = PunchManifestDto(
            sessionId = sessionId,
            fingerprint = deviceConfig.fingerprint,
            identityHash = deviceConfig.identityHash,
            alias = getDeviceName(context),
            files = files
        )
        writeLine(output, json.encodeToString(manifest))

        val reply = withTimeoutOrNull(60_000) { withContext(Dispatchers.IO) { readLine(input) } }
            ?: return TransferOutcome.DROP
        if (reply.contains("\"reject\"")) return TransferOutcome.REJECTED

        // The receiver tells us how many bytes it already has per file
        val resumeInfo = try {
            json.decodeFromString<PunchResumeInfoDto>(reply)
        } catch (e: Exception) {
            PunchResumeInfoDto()
        }

        val alreadyDone = files.sumOf { minOf(resumeInfo.files[it.id] ?: 0L, it.size) }
        val grandTotal = totalSize.coerceAtLeast(1)
        var sentNew = 0L

        for ((index, file) in files.withIndex()) {
            if (isCancelled()) return TransferOutcome.DROP

            val resume = minOf(resumeInfo.files[file.id] ?: 0L, file.size)
            writeLine(output, json.encodeToString(PunchFileHeaderDto(fileId = file.id, size = file.size, offset = resume)))
            if (resume >= file.size) continue

            val remaining = file.size - resume
            val streamed = if (resume > 0) {
                val afd = context.contentResolver.openAssetFileDescriptor(uris[index], "r")
                if (afd == null) return TransferOutcome.DROP
                afd.use { descriptor ->
                    val stream = descriptor.createInputStream()
                    stream.use { s ->
                        var skipped = 0L
                        while (skipped < resume) {
                            val n = s.skip(resume - skipped)
                            if (n <= 0) break
                            skipped += n
                        }
                        if (skipped < resume) return@use false
                        streamBytes(s, output, remaining) { bytes ->
                            sentNew += bytes
                            onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                        }
                    }
                }
            } else {
                val stream = context.contentResolver.openInputStream(uris[index])
                if (stream == null) return TransferOutcome.DROP
                stream.use { s ->
                    streamBytes(s, output, remaining) { bytes ->
                        sentNew += bytes
                        onProgress((alreadyDone + sentNew).toFloat() / grandTotal, file.fileName)
                    }
                }
            }
            if (streamed != true) return TransferOutcome.DROP
            TransferHistory.log(context, file.fileName, file.size, "sent", uris[index].toString())
        }

        writeLine(output, json.encodeToString(PunchDoneDto(sessionId = sessionId)))
        return TransferOutcome.SUCCESS
    }

    /** Copies [length] bytes from [input] to [output], reporting the delta per chunk. */
    private suspend fun streamBytes(input: InputStream, output: OutputStream, length: Long, onDelta: suspend (Long) -> Unit): Boolean {
        val buffer = ByteArray(64 * 1024)
        var sent = 0L
        while (sent < length) {
            val toRead = minOf(buffer.size.toLong(), length - sent).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n <= 0) return false
            output.write(buffer, 0, n)
            sent += n
            onDelta(n.toLong())
        }
        return true
    }

    /**
     * Simultaneous-open NAT punch: outbound connects bound to the listener port, racing an
     * accept on the same listener. Returns the first usable socket or null on timeout.
     */
    private suspend fun punch(ip: String, port: Int, isCancelled: () -> Boolean): Socket? = withContext(Dispatchers.IO) {
        val ss = serverSocket ?: return@withContext null
        val localPort = ss.localPort
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline && !isCancelled()) {
            // Outbound simultaneous-open attempt, source port = listener port
            try {
                val s = Socket()
                s.bind(InetSocketAddress("0.0.0.0", localPort))
                s.connect(InetSocketAddress(ip, port), 800)
                s.tcpNoDelay = true
                Timber.i("Punch connect succeeded to $ip:$port")
                return@withContext s
            } catch (e: Exception) {
                // expected while the NAT mapping is being established
            }

            // The peer's own punch may have landed on our listener first
            try {
                val accepted = ss.accept()
                if (accepted.inetAddress.hostAddress == ip) {
                    accepted.tcpNoDelay = true
                    Timber.i("Punch accept succeeded from $ip")
                    return@withContext accepted
                }
                scope.launch { handleIncoming(accepted) }
            } catch (e: Exception) {
                if (!scope.isActive) return@withContext null
            }
            delay(250)
        }
        Timber.w("Punch failed for $ip:$port")
        null
    }

    private fun writeLine(output: OutputStream, line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Line reader over a raw stream (never mixes with binary reads on the same stream). */
    private fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            bytes.write(b)
            if (bytes.size() > 64 * 1024) return null
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun closeQuietly(socket: Socket) {
        try { socket.close() } catch (_: Exception) {}
    }
}
