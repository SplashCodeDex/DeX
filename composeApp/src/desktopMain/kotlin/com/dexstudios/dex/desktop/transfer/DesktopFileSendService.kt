package com.dexstudios.dex.desktop.transfer

import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.DiscoveryEngine
import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.PrepareUploadResponseDto
import com.dexstudios.dex.core.network.TransferHistory
import com.dexstudios.dex.core.network.UploadOutcome
import com.dexstudios.dex.core.network.UploadState
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Desktop counterpart of the Android UploadWorker:
 * sends a batch of files to a trusted device via the LocalSend v2 prepare-upload protocol.
 *
 * Transport selection follows the receiver's own advertisement: devices that host a
 * receiver (`download = true`, i.e. other desktops) receive direct HTTP/1.1 pushes on the
 * LAN; phones advertise `download = false` and are served through the WebSocket pull model
 * ([RelayService.hostAndPushAsync] prompt -> phone pulls over HTTP/3), on LAN and WAN alike.
 *
 * Progress, success and failure states are published through [ClientEngine.updateUploadState]
 * so existing consumers (taskbar window progress, telemetry text) render without any new UI
 * plumbing. A whole-session transport failure retries with capped backoff, then escalates to
 * the relay path before giving up — a retried session never duplicates files because the
 * receiver dedupes via content hashes and answers "[SKIP]".
 */
class DesktopFileSendService(private val clientEngine: ClientEngine, private val discoveryEngine: DiscoveryEngine, private val deviceConfig: DeviceConfig) {
    companion object {
        private const val MAX_CONCURRENT_UPLOADS = 3
        private const val PARTIAL_SIZE = 32768
        private const val PROGRESS_THROTTLE_MS = 200L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SKIP_TOKEN = "[SKIP]"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    @Volatile
    private var sessionCancelled: Boolean = false

    /** User-pinned default send destination; honored for drops and as first relay candidate. */
    private val _preferredTargetFingerprint = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val preferredTargetFlow = _preferredTargetFingerprint.asStateFlow()
    private val preferredTargetFingerprint: String? get() = _preferredTargetFingerprint.value

    fun setPreferredTarget(fingerprint: String?) {
        _preferredTargetFingerprint.value = fingerprint
    }

    fun isSessionActive(): Boolean = sessionJob?.isActive == true

    /**
     * Sends [files] to the resolved target device. LAN direct delivery runs first when the
     * target hosts a receiver; otherwise (or after direct failure) delivery goes through the
     * WebSocket pull relay, where the phone/PC pulls hosted files over its persistent session.
     */
    fun sendFiles(files: List<File>, targetFingerprint: String? = null) {
        val regularFiles = files.filter { it.isFile }
        if (regularFiles.isEmpty()) return

        if (isSessionActive()) {
            clientEngine.updateUploadState(
                UploadState(fileName = regularFiles.first().name, error = "A transfer is already in progress", isUploading = false),
            )
            return
        }

        sessionJob = scope.launch {
            sessionCancelled = false
            val lanTarget = resolveDirectTarget(targetFingerprint)
            if (lanTarget != null) {
                val delivered = runSession(regularFiles.map { it to null as String? }, lanTarget)
                // Direct push failed at transport level (device vanished mid-flight): escalate to
                // the pull path instead of dead-ending the user — but never after user cancel
                if (!delivered && !sessionCancelled) {
                    sendViaRelay(regularFiles.map { it.absolutePath to null }, targetFingerprint ?: lanTarget.info.fingerprint, lanTargetName(lanTarget))
                }
            } else {
                sendViaRelay(regularFiles.map { it.absolutePath to null }, targetFingerprint, null)
            }
        }
    }

    /**
     * Sends every file contained in [folders] (recursively), preserving directory structure:
     * relative paths are computed against the nearest common ancestor so receivers recreate
     * the tree instead of flattening it.
     */
    fun sendFolders(folders: List<File>, targetFingerprint: String? = null) {
        val dirRoots = folders.filter { it.isDirectory }
        if (dirRoots.isEmpty()) {
            sendFiles(folders.filter { it.isFile }, targetFingerprint)
            return
        }

        val ancestor = if (dirRoots.size == 1) {
            dirRoots.first().parentFile ?: dirRoots.first()
        } else {
            commonAncestor(dirRoots) ?: dirRoots.first().parentFile
        }

        val entries = mutableListOf<Pair<File, String?>>()
        for (root in dirRoots) {
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = ancestor?.let { file.relativeToOrNull(it)?.invariantSeparatorsPath } ?: file.name
                entries.add(file to rel)
            }
        }

        if (entries.isEmpty()) {
            clientEngine.updateUploadState(
                UploadState(error = "The selected folder contains no files", isUploading = false),
            )
            return
        }
        sendEntries(entries, targetFingerprint)
    }

    /** Entry-point wrapper used by drag-drop / dialogs (no relative paths). */
    private fun sendEntries(entries: List<Pair<File, String?>>, targetFingerprint: String?) {
        if (entries.isEmpty()) return

        if (isSessionActive()) {
            clientEngine.updateUploadState(
                UploadState(fileName = entries.first().first.name, error = "A transfer is already in progress", isUploading = false),
            )
            return
        }

        sessionJob = scope.launch {
            sessionCancelled = false
            val lanTarget = resolveDirectTarget(targetFingerprint)
            if (lanTarget != null) {
                val delivered = runSession(entries, lanTarget)
                if (!delivered && !sessionCancelled) {
                    sendViaRelay(
                        entries.map { it.first.absolutePath to it.second },
                        targetFingerprint ?: lanTarget.info.fingerprint,
                        lanTargetName(lanTarget),
                    )
                }
            } else {
                sendViaRelay(entries.map { it.first.absolutePath to it.second }, targetFingerprint, null)
            }
        }
    }

    private fun lanTargetName(target: DiscoveredDevice): String = target.info.alias.ifBlank { target.info.deviceModel.ifBlank { "device" } }

    private fun commonAncestor(dirs: List<File>): File? {
        var candidate: File? = dirs.first().absoluteFile.parentFile
        outer@ while (candidate != null) {
            val current = candidate
            for (dir in dirs) {
                if (dir.relativeToOrNull(current) == null) {
                    candidate = current.parentFile
                    continue@outer
                }
            }
            return current
        }
        return null
    }

    fun cancelActiveSession() {
        sessionCancelled = true
        sessionJob?.cancel()
        sessionJob = null
        clientEngine.updateUploadState(
            clientEngine.uploadState.value.copy(isUploading = false, error = "Upload cancelled"),
        )
    }

    /** Runs one direct-push session with capped transport retries. Returns true when delivered. */
    private suspend fun runSession(entries: List<Pair<File, String?>>, target: DiscoveredDevice): Boolean {
        val peerName = lanTargetName(target)

        var attempt = 0
        while (true) {
            val outcome = executeSessionAttempt(entries, target, peerName, suppressFailurePaint = attempt > 0)
            val shouldRetry = outcome.transportAllFailed && !outcome.wasCancelled && attempt < MAX_RETRY_ATTEMPTS
            if (!shouldRetry) {
                return !outcome.transportAllFailed || outcome.deliveredAny
            }
            delay(1000L * (attempt + 1))
            attempt++
        }
    }

    /** One full prepare-upload -> parallel upload -> finish pass. */
    private suspend fun executeSessionAttempt(entries: List<Pair<File, String?>>, target: DiscoveredDevice, peerName: String, suppressFailurePaint: Boolean): SessionOutcome {
        val fileData = LinkedHashMap<String, Pair<File, String?>>()
        entries.forEach { (file, rel) -> fileData[UUID.randomUUID().toString()] = file to rel }
        val totalBatchSize = fileData.values.sumOf { it.first.length() }

        clientEngine.resetUploadState()
        clientEngine.updateUploadState(
            UploadState(
                fileName = if (fileData.size == 1) fileData.values.first().first.name else "Preparing ${fileData.size} files",
                totalFiles = fileData.size,
                isUploading = true,
                peerName = peerName,
                targetFingerprint = target.info.fingerprint,
            ),
        )

        val token = clientEngine.authToken(
            targetFingerprint = target.info.fingerprint,
            targetIdentityHash = target.info.identityHash,
            targetGoogleSub = target.info.googleSub,
        )
        val prepared = clientEngine.prepareUpload(
            ip = target.ip,
            port = target.info.port,
            request = PrepareUploadRequestDto(
                info = discoveryEngine.localInfo,
                files = fileData.mapValues { (id, pair) ->
                    val file = pair.first
                    FileDto(
                        id = id,
                        fileName = file.name,
                        size = file.length(),
                        fileType = mimeOf(file),
                        partialHash = computePartialHash(file),
                        relativePath = pair.second?.takeIf { it.isNotBlank() },
                    )
                },
            ),
            token = token,
        )
        val response = prepared.response ?: run {
            // Transport failure (-1) is retryable; auth/HTTP rejections are terminal
            if (prepared.httpStatus == -1 && !suppressFailurePaint) {
                clientEngine.updateUploadState(UploadState(error = "Could not reach $peerName", isUploading = false))
            } else if (prepared.httpStatus != -1) {
                val message = when (prepared.httpStatus) {
                    401, 403 -> "Not authorized to send to $peerName"
                    else -> "$peerName rejected the transfer (HTTP ${prepared.httpStatus})"
                }
                logAll(fileData.values.map { it.first }, peerName, status = "failed")
                clientEngine.updateUploadState(UploadState(error = message, isUploading = false))
            }
            return SessionOutcome(transportAllFailed = prepared.httpStatus == -1)
        }

        val totalSent = AtomicLong(0L)
        val doneCount = AtomicInteger(0)
        val outcomes = CopyOnWriteArrayList<Pair<String, UploadOutcome>>()
        val semaphore = Semaphore(MAX_CONCURRENT_UPLOADS)

        try {
            coroutineScope {
                fileData.forEach { (id, pair) ->
                    launch(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val file = pair.first
                            val fileToken = response.files[id] ?: run {
                                outcomes.add(id to UploadOutcome(false, 403))
                                return@launch
                            }
                            if (fileToken == SKIP_TOKEN) {
                                doneCount.incrementAndGet()
                                logSent(file, peerName)
                                outcomes.add(id to UploadOutcome(true))
                                return@launch
                            }

                            val stream = try {
                                FileInputStream(file)
                            } catch (_: Exception) {
                                // Not a transport failure: never retry a file we cannot read
                                outcomes.add(id to UploadOutcome(false, 403))
                                return@launch
                            }

                            stream.use { input ->
                                val perFile = AtomicLong(0L)
                                val onBytes: (Long) -> Unit = { bytes ->
                                    val delta = bytes - perFile.getAndSet(bytes)
                                    reportProgress(
                                        doneCount.get(),
                                        fileData.size,
                                        totalSent.addAndGet(delta),
                                        totalBatchSize,
                                        file.name,
                                        peerName,
                                        target.info.fingerprint,
                                    )
                                }

                                // The desktop build has no QUIC engine; uploads ride the CIO
                                // HTTP/1.1 stack. (Phones prefer Cronet HTTP/3.)
                                val outcome = clientEngine.uploadFile(
                                    target.ip, target.info.port, response.sessionId, id, file.name, fileToken,
                                    input, file.length(), onProgress = onBytes,
                                )

                                if (outcome.ok) {
                                    doneCount.incrementAndGet()
                                    logSent(file, peerName)
                                } else {
                                    com.dexstudios.dex.core.network.TransferHistoryRecorder.recordFailed(
                                        name = file.name,
                                        size = file.length(),
                                        direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_SENT,
                                        peerDevice = peerName,
                                    )
                                }
                                outcomes.add(id to outcome)
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            return SessionOutcome(transportAllFailed = false, wasCancelled = true)
        }

        val failed = outcomes.filter { !it.second.ok }
        val anyHttpError = failed.any { it.second.httpStatus > 0 }
        val transportAllFailed = failed.isNotEmpty() && failed.size == outcomes.size && !anyHttpError
        val deliveredAny = outcomes.any { it.second.ok }

        if (transportAllFailed && suppressFailurePaint) {
            // A retry pass re-runs everything — don't paint a failure state in between
            return SessionOutcome(transportAllFailed = true)
        }

        clientEngine.finishUpload(doneCount.get(), fileData.size)

        if (failed.isNotEmpty() && failed.all { it.second.httpStatus == 403 }) {
            // Sharper than the generic all-failed message when the cause is unreadable files
            clientEngine.updateUploadState(
                clientEngine.uploadState.value.copy(error = "Cannot read one or more files", isUploading = false),
            )
        }
        return SessionOutcome(transportAllFailed = false, deliveredAny = deliveredAny)
    }

    private data class SessionOutcome(val transportAllFailed: Boolean, val wasCancelled: Boolean = false, val deliveredAny: Boolean = false)

    /**
     * Resolves the DIRECT push target among live discoveries: only LAN devices that host a
     * receiver qualify (`download = true`). Phones advertise `download = false` and are
     * handled by [sendViaRelay]; WAN/roster entries carry synthetic IPs and never route here.
     */
    private fun resolveDirectTarget(targetFingerprint: String?): DiscoveredDevice? {
        val lanDevices = discoveryEngine.devices.value.values.filter { !it.viaWan && !it.viaRoster && it.info.download && it.info.port > 0 }
        if (lanDevices.isEmpty()) return null

        targetFingerprint?.let { fp ->
            return lanDevices.firstOrNull { it.info.fingerprint == fp }
        }
        preferredTargetFingerprint?.let { fp ->
            lanDevices.firstOrNull { it.info.fingerprint == fp }?.let { return it }
        }
        val paired = AuthState.pairedFingerprints.value
        return lanDevices.firstOrNull { it.info.fingerprint in paired }
            ?: lanDevices.firstOrNull { device ->
                val mySub = deviceConfig.googleSub
                mySub.isNotBlank() && device.info.googleSub == mySub
            }
    }

    /**
     * Pull-model delivery: host files locally, push a prepare-upload prompt over the peer's
     * persistent WebSocket session, and let it download. Candidate order: explicit
     * fingerprint -> preferred pin -> trusted WS-connected peers (paired or same-account).
     * Exactly ONE candidate receives the prompt — spraying every connected phone would
     * trigger duplicate pulls.
     *
     * Success here means ONLY "the peer was prompted"; real completion arrives via the
     * relay callbacks once every byte has been pulled (or the offer expires untouched).
     */
    private suspend fun sendViaRelay(files: List<Pair<String, String?>>, explicitFingerprint: String?, knownPeerName: String?): Boolean {
        val pairedSet = AuthState.pairedFingerprints.value
        val mySub = deviceConfig.googleSub.takeIf { it.isNotBlank() }
        val remoteDevices = discoveryEngine.devices.value.values.filter { it.viaWan || it.viaRoster }

        fun isTrustedCandidate(fp: String): Boolean = fp in pairedSet || remoteDevices.any { it.info.fingerprint == fp && mySub != null && it.info.googleSub == mySub }

        val candidates = buildList {
            explicitFingerprint?.takeIf { it.isNotBlank() }?.let(::add)
            preferredTargetFingerprint?.let { fp -> if (fp !in this && isTrustedCandidate(fp)) add(fp) }
            // Live control-channel sessions first — these peers can actually be prompted now
            WebSocketConnectionManager.trustedFingerprints()
                .filter { it !in this && isTrustedCandidate(it) }
                .forEach(::add)
            remoteDevices.map { it.info.fingerprint }.filter { it !in this && isTrustedCandidate(it) }.forEach(::add)
        }

        val chosenFingerprint = candidates.firstOrNull()
            ?: run {
                clientEngine.updateUploadState(
                    UploadState(error = "No trusted device online", isUploading = false),
                )
                return false
            }
        val peerName = remoteDevices.firstOrNull { it.info.fingerprint == chosenFingerprint }
            ?.info?.alias?.ifBlank { null }
            ?: knownPeerName?.takeIf { chosenFingerprint == explicitFingerprint }
            ?: "device"

        val alias = deviceConfig.alias.ifBlank { "PC" }

        clientEngine.resetUploadState()
        clientEngine.updateUploadState(
            UploadState(
                fileName = if (files.size == 1) files.first().first.substringAfterLast('/') else "Preparing ${files.size} files",
                totalFiles = files.size,
                isUploading = true,
                peerName = peerName,
                targetFingerprint = chosenFingerprint,
            ),
        )

        val delivered = com.dexstudios.dex.core.network.services.RelayService.hostAndPushAsync(
            targetFingerprint = chosenFingerprint,
            files = files,
            senderAlias = alias,
            onCompleted = {
                files.forEach { logSent(File(it.first), peerName) }
                clientEngine.finishUpload(files.size, files.size)
            },
            onExpired = {
                clientEngine.updateUploadState(
                    UploadState(
                        fileName = if (files.size == 1) files.first().first.substringAfterLast('/') else "${files.size} files",
                        error = "$peerName did not pick up the files - the offer expired",
                        isUploading = false,
                    ),
                )
            },
        )

        if (delivered) {
            // Prompt reached the device; completion is reported by the callbacks above
            clientEngine.updateUploadState(
                UploadState(
                    fileName = if (files.size == 1) files.first().first.substringAfterLast('/') else "Waiting for $peerName",
                    totalFiles = files.size,
                    isUploading = true,
                    peerName = peerName,
                    targetFingerprint = chosenFingerprint,
                ),
            )
        } else {
            clientEngine.updateUploadState(
                UploadState(
                    fileName = if (files.size == 1) files.first().first.substringAfterLast('/') else "${files.size} files",
                    error = "$peerName is not connected - open DeX on the phone and try again",
                    isUploading = false,
                ),
            )
        }
        return delivered
    }

    private fun mimeOf(file: File): String = runCatching { java.nio.file.Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"

    private fun logSent(file: File, peerName: String) {
        com.dexstudios.dex.core.network.TransferHistoryRecorder.recordCompleted(
            name = file.name,
            size = file.length(),
            direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_SENT,
            uri = file.absolutePath,
            peerDevice = peerName,
        )
    }

    private fun logAll(files: Collection<File>, peerName: String, status: String) {
        // status here is the raw TransferHistory status ("success"/"failed"/partial variants)
        files.forEach { file ->
            com.dexstudios.dex.core.network.TransferHistory.log(
                name = file.name,
                size = file.length(),
                direction = com.dexstudios.dex.core.domain.transfer.TransferUseCase.DIRECTION_SENT,
                uri = file.absolutePath,
                peerDevice = peerName,
                status = status,
            )
        }
    }

    /**
     * SHA-256 over the first and last 32KB of the file — mirrors the Android sender so
     * both sides agree on the dedupe/diff fingerprint contract. The receiver uses this to
     * answer "[SKIP]" for identical content instead of duplicating files.
     */
    private fun computePartialHash(file: File): String? {
        val fileSize = file.length()
        if (fileSize == 0L) return null
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(PARTIAL_SIZE)
            FileInputStream(file).use { stream ->
                val headBytes = stream.read(buffer, 0, PARTIAL_SIZE)
                if (headBytes > 0) md.update(buffer, 0, headBytes)

                if (fileSize > PARTIAL_SIZE * 2) {
                    val bytesToSkip = fileSize - headBytes - PARTIAL_SIZE
                    var skipped = 0L
                    while (skipped < bytesToSkip) {
                        val s = stream.skip(bytesToSkip - skipped)
                        if (s <= 0) {
                            val readBytes = stream.read(buffer, 0, min(buffer.size.toLong(), bytesToSkip - skipped).toInt())
                            if (readBytes == -1) break
                            skipped += readBytes
                        } else {
                            skipped += s
                        }
                    }
                    val tailBytes = stream.read(buffer, 0, PARTIAL_SIZE)
                    if (tailBytes > 0) md.update(buffer, 0, tailBytes)
                } else if (fileSize > headBytes) {
                    var remaining = fileSize - headBytes
                    while (remaining > 0) {
                        val read = stream.read(buffer, 0, min(remaining, PARTIAL_SIZE.toLong()).toInt())
                        if (read == -1) break
                        md.update(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            md.digest().joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Throttled aggregate progress publisher with smoothed speed (worker parity). */
    private fun reportProgress(doneFiles: Int, totalFiles: Int, sentBytes: Long, totalBytes: Long, currentFile: String, peerName: String, targetFingerprint: String?) {
        val now = System.currentTimeMillis()
        if (sentBytes < totalBytes && now - lastUiUpdate.get() < PROGRESS_THROTTLE_MS) return

        val lastTime = speedTime.get()
        if (lastTime != 0L && now - lastTime > PROGRESS_THROTTLE_MS) {
            val instant = ((sentBytes - speedBytes.get()) * 1000L) / (now - lastTime)
            val prev = smoothedSpeed.get()
            smoothedSpeed.set(if (prev == 0L) instant else (prev * 7 + instant * 3) / 10)
        }
        lastUiUpdate.set(now)
        speedBytes.set(sentBytes)
        speedTime.set(now)

        val aggregate = if (totalBytes > 0) sentBytes.toFloat() / totalBytes else 0f
        val displayName = if (totalFiles == 1) currentFile else "$doneFiles of $totalFiles files"
        try {
            clientEngine.updateUploadState(
                UploadState(
                    fileName = displayName,
                    currentFileIndex = doneFiles + 1,
                    totalFiles = totalFiles,
                    progress = aggregate,
                    aggregateProgress = aggregate,
                    isUploading = true,
                    protocol = "http/1.1",
                    speedBps = smoothedSpeed.get(),
                    targetFingerprint = targetFingerprint,
                    peerName = peerName,
                ),
            )
        } catch (_: Exception) {
            // UI updates must never kill the transfer
        }
    }

    private val lastUiUpdate = AtomicLong(0L)
    private val speedBytes = AtomicLong(0L)
    private val speedTime = AtomicLong(0L)
    private val smoothedSpeed = AtomicLong(0L)
}
