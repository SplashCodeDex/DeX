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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * sends a batch of files to a trusted phone via the LocalSend v2 prepare-upload
 * protocol, preferring HTTP/3 (QUIC) streams with an HTTP/1.1 fallback.
 *
 * Progress, success and failure states are published through
 * [ClientEngine.updateUploadState] so existing consumers (taskbar window progress,
 * telemetry text) render without any new UI plumbing. A whole-session transport
 * failure retries with capped backoff — a retried session never leaves duplicate
 * files on the phone because the receiver dedupes via hashes and answers "[SKIP]".
 */
class DesktopFileSendService(
    private val clientEngine: ClientEngine,
    private val discoveryEngine: DiscoveryEngine,
    private val deviceConfig: DeviceConfig
) {
    companion object {
        private const val MAX_CONCURRENT_UPLOADS = 3
        private const val PARTIAL_SIZE = 32768
        private const val PROGRESS_THROTTLE_MS = 200L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SKIP_TOKEN = "[SKIP]"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    fun isSessionActive(): Boolean = sessionJob?.isActive == true

    /**
     * Sends [files] to the resolved target device. The target is, in priority order:
     * the device matching [targetFingerprint], the first paired online LAN device, or
     * the first same-account online LAN device. Without a trusted target the request
     * fails fast with a surfaced error state.
     */
    fun sendFiles(files: List<File>, targetFingerprint: String? = null) {
        val regularFiles = files.filter { it.isFile }
        if (regularFiles.isEmpty()) return

        if (isSessionActive()) {
            clientEngine.updateUploadState(
                UploadState(fileName = regularFiles.first().name, error = "A transfer is already in progress", isUploading = false)
            )
            return
        }

        sessionJob = scope.launch {
            runSession(regularFiles, targetFingerprint)
        }
    }

    fun cancelActiveSession() {
        sessionJob?.cancel()
        sessionJob = null
        clientEngine.updateUploadState(
            clientEngine.uploadState.value.copy(isUploading = false, error = "Upload cancelled")
        )
    }

    private suspend fun runSession(files: List<File>, targetFingerprint: String?) {
        val target = resolveTarget(targetFingerprint)
        if (target == null) {
            clientEngine.updateUploadState(
                UploadState(error = "No trusted device online", isUploading = false)
            )
            return
        }
        val peerName = target.info.alias.ifBlank { target.info.deviceModel.ifBlank { "device" } }

        var attempt = 0
        while (true) {
            val outcome = executeSessionAttempt(files, target, peerName, suppressFailurePaint = attempt > 0)
            val shouldRetry = outcome.transportAllFailed && !outcome.wasCancelled && attempt < MAX_RETRY_ATTEMPTS
            if (!shouldRetry) return
            delay(1000L * (attempt + 1))
            attempt++
        }
    }

    /** One full prepare-upload -> parallel upload -> finish pass. */
    private suspend fun executeSessionAttempt(
        files: List<File>,
        target: DiscoveredDevice,
        peerName: String,
        suppressFailurePaint: Boolean
    ): SessionOutcome {
        val fileData = LinkedHashMap<String, File>()
        files.forEach { file -> fileData[UUID.randomUUID().toString()] = file }
        val totalBatchSize = fileData.values.sumOf { it.length() }

        clientEngine.resetUploadState()
        clientEngine.updateUploadState(
            UploadState(
                fileName = if (fileData.size == 1) fileData.values.first().name else "Preparing ${fileData.size} files",
                totalFiles = fileData.size,
                isUploading = true,
                peerName = peerName,
                targetFingerprint = target.info.fingerprint
            )
        )

        val token = clientEngine.authToken(
            targetFingerprint = target.info.fingerprint,
            targetIdentityHash = target.info.identityHash,
            targetGoogleSub = target.info.googleSub
        )
        val prepared = clientEngine.prepareUpload(
            ip = target.ip,
            port = target.info.port,
            request = PrepareUploadRequestDto(
                info = discoveryEngine.localInfo,
                files = fileData.mapValues { (id, file) ->
                    FileDto(
                        id = id,
                        fileName = file.name,
                        size = file.length(),
                        fileType = mimeOf(file),
                        partialHash = computePartialHash(file)
                    )
                }
            ),
            token = token
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
                logAll(fileData.values, peerName, status = "failed")
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
                fileData.forEach { (id, file) ->
                    launch(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
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
                                val useQuic = clientEngine.quicAvailable()
                                val perFile = AtomicLong(0L)
                                val onBytes: (Long) -> Unit = { bytes ->
                                    val delta = bytes - perFile.getAndSet(bytes)
                                    reportProgress(
                                        doneCount.get(), fileData.size,
                                        totalSent.addAndGet(delta), totalBatchSize,
                                        file.name, useQuic, peerName, target.info.fingerprint
                                    )
                                }

                                val outcome = if (useQuic) {
                                    clientEngine.uploadFileQuic(target.ip, target.info.port, response.sessionId, id, file.name, fileToken, input, file.length(), onProgress = onBytes)
                                } else {
                                    clientEngine.uploadFile(target.ip, target.info.port, response.sessionId, id, file.name, fileToken, input, file.length(), onProgress = onBytes)
                                }

                                if (outcome.ok) {
                                    doneCount.incrementAndGet()
                                    logSent(file, peerName)
                                } else {
                                    TransferHistory.log(name = file.name, size = file.length(), direction = "sent", uri = file.absolutePath, peerDevice = peerName, status = "failed")
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

        if (transportAllFailed && suppressFailurePaint) {
            // A retry pass re-runs everything — don't paint a failure state in between
            return SessionOutcome(transportAllFailed = true)
        }

        clientEngine.finishUpload(doneCount.get(), fileData.size)

        if (failed.isNotEmpty() && failed.all { it.second.httpStatus == 403 }) {
            // Sharper than the generic all-failed message when the cause is unreadable files
            clientEngine.updateUploadState(
                clientEngine.uploadState.value.copy(error = "Cannot read one or more files", isUploading = false)
            )
        }
        return SessionOutcome(transportAllFailed = false)
    }

    private data class SessionOutcome(
        val transportAllFailed: Boolean,
        val wasCancelled: Boolean = false
    )

    /**
     * Resolves the drop target among live discoveries. WAN/roster entries carry synthetic
     * IPs and are excluded because direct uploads require a reachable LAN address.
     */
    private fun resolveTarget(targetFingerprint: String?): DiscoveredDevice? {
        val lanDevices = discoveryEngine.devices.value.values.filter { !it.viaWan && it.info.port > 0 }
        if (lanDevices.isEmpty()) return null

        targetFingerprint?.let { fp ->
            return lanDevices.firstOrNull { it.info.fingerprint == fp }
        }
        val paired = AuthState.pairedFingerprints.value
        return lanDevices.firstOrNull { it.info.fingerprint in paired }
            ?: lanDevices.firstOrNull { device ->
                val mySub = deviceConfig.googleSub
                mySub.isNotBlank() && device.info.googleSub == mySub
            }
    }

    private fun mimeOf(file: File): String =
        runCatching { java.nio.file.Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"

    private fun logSent(file: File, peerName: String) {
        TransferHistory.log(name = file.name, size = file.length(), direction = "sent", uri = file.absolutePath, peerDevice = peerName)
    }

    private fun logAll(files: Collection<File>, peerName: String, status: String) {
        files.forEach { file ->
            TransferHistory.log(name = file.name, size = file.length(), direction = "sent", uri = file.absolutePath, peerDevice = peerName, status = status)
        }
    }

    /**
     * SHA-256 over the first and last 32KB of the file — mirrors the Android sender so
     * both sides agree on the dedupe/diff fingerprint contract.
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
    private fun reportProgress(
        doneFiles: Int,
        totalFiles: Int,
        sentBytes: Long,
        totalBytes: Long,
        currentFile: String,
        useQuic: Boolean,
        peerName: String,
        targetFingerprint: String?
    ) {
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
                    protocol = if (useQuic) clientEngine.lastUploadProtocol() else "http/1.1",
                    speedBps = smoothedSpeed.get(),
                    targetFingerprint = targetFingerprint,
                    peerName = peerName
                )
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
