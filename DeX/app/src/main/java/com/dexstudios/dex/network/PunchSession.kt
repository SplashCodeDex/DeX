package com.dexstudios.dex.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dexstudios.dex.network.PunchTransferChannel.TransferOutcome
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
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.put
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID

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
    private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val json = DexJson

    // Plan 042 seams: rendezvous/socket acquisition, and the TCP transfer data plane.
    private val connector = PunchSocketConnector(
        context = context,
        wsService = wsService,
        deviceConfig = deviceConfig,
        serverSocketProvider = { serverSocket },
        isActive = { scope.isActive },
        onForeignConnection = { socket -> scope.launch { handleIncoming(socket) } },
    )
    private val transferChannel = PunchTransferChannel(context, deviceConfig)

    fun start() {
        if (serverSocket != null) return
        try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                // accept() must never block forever: the 800ms timeout lets the
                // punch loop (and stop()) break out of it promptly instead of hanging.
                soTimeout = 800
            }
            serverSocket = ss
            Timber.i("Punch listener open on port ${ss.localPort}")
        } catch (e: Exception) {
            Timber.e(e, "Cannot open punch listener")
            return
        }
        scope.launch {
            // Register our public endpoint and refresh before the PC's 5-minute TTL expires
            while (isActive) {
                connector.registerEndpoint()
                delay(120_000.milliseconds)
            }
        }
        scope.launch {
            // Prune stale resume sessions (dropped transfers we gave up waiting for)
            while (isActive) {
                delay(60_000.milliseconds)
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

    // ---- Receiver side ----

    private suspend fun receiveLoop() {
        val ss = serverSocket ?: return
        while (scope.isActive) {
            try {
                val socket = withContext(Dispatchers.IO) { ss.accept() }
                scope.launch { handleIncoming(socket) }
            } catch (_: SocketTimeoutException) {
                // No inbound connection within the timeout — loop back and re-check scope
                continue
            } catch (_: SocketException) {
                // Listener was closed (stop()) — exit quietly
                break
            } catch (e: Exception) {
                if (scope.isActive) Timber.e(e, "Punch accept failed") else break
            }
        }
    }

    private suspend fun handleIncoming(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val manifestLine = withTimeoutOrNull(10_000.milliseconds) { PunchLineProtocol.readLine(input) }
                ?: return@withContext closeQuietly(socket)
            val manifest = json.decodeFromString<PunchManifestDto>(manifestLine)

            // Same-email only: the sender's identity must match ours
            if ((manifest.identityHash.isBlank() || manifest.identityHash != deviceConfig.identityHash)) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"identity"}""")
                Timber.w("Rejected punch transfer from non-same-email device ${manifest.alias}")
                return@withContext closeQuietly(socket)
            }
            val sessionId = manifest.sessionId
            val resumeMap = PunchResumeState.mapFor(sessionId)

            // Only prompt for sessions we have not already accepted (auto-resume skips the prompt)
            if (!PunchResumeState.isAccepted(sessionId)) {
                val deferred = CompletableDeferred<Boolean>()
                TransferState.pendingPrompts[sessionId] = deferred
                val notificationId = sessionId.hashCode()
                notificationHelper.showIncomingFileNotification(sessionId, notificationId, manifest.files.size)

                val accepted = withTimeoutOrNull(60_000.milliseconds) { deferred.await() } == true
                TransferState.pendingPrompts.remove(sessionId)
                if (!accepted) {
                    PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"declined"}""")
                    return@withContext closeQuietly(socket)
                }
                PunchResumeState.markAccepted(sessionId)
            }

            // Tell the sender how many bytes we already have per file, so it resumes — not restarts
            val resumeInfo = PunchResumeInfoDto(
                sessionId = sessionId,
                files = manifest.files.associateBy({ it.id }, { resumeMap[it.id]?.received ?: 0L })
            )
            PunchLineProtocol.writeLine(output, json.encodeToString(resumeInfo))

            val dirUri = SafStorage.getDownloadsDexUri(context)

            var doneFiles = manifest.files.count { file -> (resumeMap[file.id]?.received ?: 0L) >= file.size }
            for (file in manifest.files) {
                val headerLine = withTimeoutOrNull(30_000.milliseconds) { PunchLineProtocol.readLine(input) }
                    ?: break
                val header = json.decodeFromString<PunchFileHeaderDto>(headerLine)

                val existing = resumeMap[file.id]
                if (existing != null && existing.received >= existing.size) {
                    doneFiles++
                    continue
                }

                // Folder bundles: recreate the relative path structure under Downloads/DeX
                val docUri = existing?.docUri ?: if (dirUri != null) {
                    if (!file.relativePath.isNullOrBlank()) {
                        SafStorage.createDocumentWithPath(context, dirUri, file.relativePath)
                    } else {
                        SafStorage.createDocumentUri(context, dirUri, file.fileName)
                    }
                } else {
                    SafStorage.createMediaStoreUri(context, file.fileName, file.relativePath)
                } ?: continue
                val out = context.contentResolver.openOutputStream(docUri, "wa")
                    ?: continue
                if (existing == null) resumeMap[file.id] = ResumeEntry(docUri, 0L, file.size)

                val resumeEntry = resumeMap[file.id] ?: continue
                var received = resumeEntry.received
                try {
                    val buffer = ByteArray(64 * 1024)
                    while (received < header.size) {
                        val toRead = minOf(buffer.size.toLong(), header.size - received).toInt()
                        val n = input.read(buffer, 0, toRead)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        received += n
                        resumeEntry.received = received
                    }
                    if (received < header.size) break // dropped mid-file — the sender will resume
                    doneFiles++
                    val senderAlias = manifest.alias.ifBlank { "Device" }
                    TransferHistory.log(context, file.fileName, received, "received", docUri.toString(), peerDevice = senderAlias)
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
            withTimeoutOrNull(10_000.milliseconds) { PunchLineProtocol.readLine(input) }
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
            if (attempt > 0) delay(1500.milliseconds)

            connector.registerEndpoint()

            // 1. Ask the PC for the target's public endpoint (and give it ours)
            val deferred = CompletableDeferred<EndpointInfoDto>()
            PunchState.pendingEndpointInfo.value = deferred
            wsService.sendMessage(
                ProtocolKeys.envelopeOf(ProtocolKeys.RESOLVE_ENDPOINT) {
                    put(ProtocolKeys.TARGET_FINGERPRINT, targetFingerprint)
                }
            )
            val info = withTimeoutOrNull(15_000.milliseconds) { deferred.await() }
            PunchState.pendingEndpointInfo.value = null
            if (info == null || info.ip.isBlank() || info.port <= 0) {
                return@withContext "The target device is offline or not registered"
            }

            // 2. Punch through the NATs (simultaneous-open)
            val socket = connector.punch(info.ip, info.port, isCancelled)
            if (socket == null) {
                lastError = "Direct connection failed — your network blocks punching"
                return@withContext lastError
            }

            // 3. Transfer with resume support; a mid-transfer drop retries the whole session
            try {
                val targetAlias = PunchState.devices.value.firstOrNull { it.info.fingerprint == targetFingerprint }?.info?.alias ?: "Device"
                when (transferChannel.runTransfer(socket, sessionId, uris, files, totalSize, isCancelled, onProgress, targetAlias)) {
                    TransferOutcome.SUCCESS -> return@withContext null
                    TransferOutcome.REJECTED -> return@withContext "The recipient declined the transfer"
                    TransferOutcome.DROP -> {
                        lastError = "Connection lost — resuming transfer"
                        Timber.i("Punch connection dropped mid-transfer; retrying (attempt ${attempt + 1}/$maxTransferAttempts)")
                    }
                }
            } catch (_: Exception) {
                Timber.e("Punch transfer failed; retrying")
                lastError = "Connection lost — resuming transfer"
            } finally {
                closeQuietly(socket)
            }
        }
        lastError ?: "Transfer failed"
    }
}

