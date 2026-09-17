package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** One file received into a relay staging session (A -> PC -> B). */
data class RelayReceivedFile(val fileName: String, val absolutePath: String, val relativePath: String? = null)

/**
 * A hosted push (PC hosts files; the phone pulls them over HTTP/3 or the HTTP fallback).
 * [fileIds] must ALL be pulled at least once before [onCompleted] fires; if the sliding
 * TTL lapses first, [onExpired] fires instead. Exactly one of the two ever runs.
 */
class HostedPush internal constructor(val pushId: String, internal val fileIds: Set<String>, internal val onCompleted: (() -> Unit)?, internal val onExpired: (() -> Unit)?) {
    internal val pulled = ConcurrentHashMap.newKeySet<String>()
    internal val completion = CompletableDeferred<Boolean>()

    @Volatile
    internal var finished: Boolean = false
}

object RelayService {
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Shared maps for both PC-to-Phone pushes and phone-to-phone relay hosted files (fileId -> absolute path)
    val hostedFiles = ConcurrentHashMap<String, String>()
    val hostedFileTokens = ConcurrentHashMap<String, String>()
    val hostedFileLastAccess = ConcurrentHashMap<String, Long>()

    // Relay fallback staging (phone-to-phone via PC): sessionId -> received files
    val relaySessionFiles = ConcurrentHashMap<String, MutableList<RelayReceivedFile>>()
    val relaySessionAliases = ConcurrentHashMap<String, String>() // sessionId -> alias
    private val relaySessionTime = ConcurrentHashMap<String, Long>()

    /**
     * Expected relay arrival counts, recorded by ShareRoutes at prepare-upload time.
     *
     * The upload session record itself is removed the moment its LAST file lands
     * (finishIncomingSession), but the sender's `relay-transfer` request arrives only
     * AFTER that — so the count must live in a map that outlives the session record.
     * Without it, relayUploadedSession resolved 0 expected files from a vanished session
     * and answered relay-error for every fully-successful relay upload.
     */
    class ExpectedRelay(val expectedCount: Int, val createdAt: Long = System.currentTimeMillis())

    val relaySessionExpected = ConcurrentHashMap<String, ExpectedRelay>()

    /** Records how many files the sender will actually upload for a relay session. */
    fun trackRelayExpected(sessionId: String, expectedCount: Int) {
        relaySessionExpected[sessionId] = ExpectedRelay(expectedCount)
        ensureMaintenanceLoop()
    }

    /**
     * Files of a relay session that were ALREADY on this PC at prepare time (the `[SKIP]`
     * answers, i.e. files the sender never uploads).
     *
     * The onward manifest must describe the WHOLE batch, not just the freshly uploaded part:
     * building it from staging alone silently dropped deduplicated files from the A -> PC -> B
     * hop, and a fully deduplicated batch forwarded an empty list, so the PC answered
     * relay-error even though every file was sitting right there on disk.
     */
    val relaySessionDeduped = ConcurrentHashMap<String, List<RelayReceivedFile>>()

    /** Records the `[SKIP]`-deduplicated files of a relay session so they ride the onward push. */
    fun trackRelayDeduped(sessionId: String, files: List<RelayReceivedFile>) {
        if (files.isEmpty()) return
        relaySessionDeduped[sessionId] = files
        ensureMaintenanceLoop()
    }

    // Push bookkeeping for delivery confirmation callbacks (H1: never fake "sent")
    private val pushes = ConcurrentHashMap<String, HostedPush>()

    /** Sliding TTLs: a hosted file dies 5 min after its last pull attempt; relay staging 10 min after its last arrival. */
    private const val HOSTED_TTL_MS = 5 * 60 * 1000L
    private const val RELAY_TTL_MS = 10 * 60 * 1000L

    private val maintenanceStarted = AtomicLong(0)

    /** Starts the single shared maintenance loop exactly once for the process lifetime. */
    private fun ensureMaintenanceLoop() {
        if (maintenanceStarted.compareAndSet(0, 1)) {
            serviceScope.launch {
                while (true) {
                    delay(60_000L)
                    val now = System.currentTimeMillis()

                    // Expire hosted pulls whose token was never (or not recently) exercised
                    val staleHosted = hostedFileLastAccess.entries
                        .filter { now - it.value > HOSTED_TTL_MS }
                        .map { it.key }
                    for (id in staleHosted) removeHosted(id)
                    // Defensive: entries whose lastAccess vanished entirely
                    val orphaned = hostedFiles.keys.filterNot { hostedFileLastAccess.containsKey(it) }
                    for (id in orphaned) removeHosted(id)

                    // Settle pushes: complete when everything was pulled, expired when the
                    // never-pulled remainder lost its hosting slot
                    for (push in pushes.values.toList()) {
                        val remaining = push.fileIds.filterNot { push.pulled.contains(it) }
                        when {
                            remaining.isEmpty() -> finishPush(push, completed = true)
                            remaining.none { hostedFiles.containsKey(it) } -> finishPush(push, completed = false)
                        }
                    }

                    // Drop stale relay staging sessions
                    val staleRelay = relaySessionTime.entries
                        .filter { now - it.value > RELAY_TTL_MS }
                        .map { it.key }
                    for (id in staleRelay) {
                        relaySessionFiles.remove(id)
                        relaySessionAliases.remove(id)
                        relaySessionTime.remove(id)
                        relaySessionExpected.remove(id)
                        relaySessionDeduped.remove(id)
                    }

                    // Expected counts with no staging yet (all-deduped sessions) still expire
                    val staleExpected = relaySessionExpected.entries
                        .filter { now - it.value.createdAt > RELAY_TTL_MS }
                        .map { it.key }
                    for (id in staleExpected) {
                        relaySessionExpected.remove(id)
                        // An all-deduped session never stages anything, so its manifest is
                        // bounded only by this sweep (relaySessionTime is never set for it).
                        relaySessionDeduped.remove(id)
                    }

                    if (hostedFileLastAccess.isEmpty() && relaySessionTime.isEmpty() && relaySessionExpected.isEmpty() && pushes.isEmpty()) {
                        maintenanceStarted.set(0)
                        // Re-check under race: a producer may have arrived between the check and reset
                        if (hostedFileLastAccess.isEmpty() && relaySessionTime.isEmpty() && relaySessionExpected.isEmpty() && pushes.isEmpty()) break
                        ensureMaintenanceLoop()
                        return@launch
                    }
                }
            }
        }
    }

    private val hostedLock = Any()
    private val activeDownloads = mutableMapOf<String, MutableSet<Job>>()

    fun registerDownload(fileId: String, job: Job): Boolean = synchronized(hostedLock) {
        if (!hostedFiles.containsKey(fileId)) return@synchronized false
        activeDownloads.getOrPut(fileId) { mutableSetOf() }.add(job)
        true
    }

    fun unregisterDownload(fileId: String, job: Job) = synchronized(hostedLock) {
        activeDownloads[fileId]?.let { jobs ->
            jobs.remove(job)
            if (jobs.isEmpty()) activeDownloads.remove(fileId)
        }
    }

    fun touchHosted(fileId: String): Boolean = synchronized(hostedLock) {
        if (!hostedFiles.containsKey(fileId)) return@synchronized false
        hostedFileLastAccess[fileId] = System.currentTimeMillis()
        true
    }

    private fun removeHosted(id: String, cancelDownloads: Boolean = true) {
        val jobs = synchronized(hostedLock) {
            hostedFiles.remove(id)
            hostedFileTokens.remove(id)
            hostedFileLastAccess.remove(id)
            activeDownloads.remove(id)?.toList().orEmpty()
        }
        if (cancelDownloads) jobs.forEach { it.cancel(CancellationException("Hosted transfer ended")) }
    }

    private fun finishPush(push: HostedPush, completed: Boolean, notifyCallbacks: Boolean = true) {
        synchronized(push) {
            if (push.finished) return
            push.finished = true
        }
        pushes.remove(push.pushId)
        for (id in push.fileIds) removeHosted(id, cancelDownloads = !completed)
        try {
            if (notifyCallbacks) {
                if (completed) push.onCompleted?.invoke() else push.onExpired?.invoke()
            }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.w("Hosted transfer callback failed: ${e.message}")
        } finally {
            push.completion.complete(completed)
        }
    }

    /** Call only after the complete response body has been written and flushed. */
    fun markPulled(fileId: String) {
        if (!touchHosted(fileId)) return
        for (push in pushes.values) {
            synchronized(push) {
                if (!push.finished && push.fileIds.contains(fileId)) {
                    push.pulled.add(fileId)
                    if (push.pulled.containsAll(push.fileIds)) finishPush(push, completed = true)
                }
            }
        }
    }

    /**
     * Stages one uploaded file under a relay session so a later `relay-transfer` request can
     * host-and-push the whole batch toward the final target.
     */
    fun trackRelayFile(sessionId: String, fileName: String, absolutePath: String, senderAlias: String, relativePath: String? = null) {
        val list = relaySessionFiles.getOrPut(sessionId) { mutableListOf() }
        synchronized(list) {
            list.add(RelayReceivedFile(fileName, absolutePath, relativePath))
        }
        relaySessionAliases[sessionId] = senderAlias
        relaySessionTime[sessionId] = System.currentTimeMillis()
        ensureMaintenanceLoop()
    }

    /**
     * Completes the A->PC->B fallback: verifies session [sessionId] fully arrived at this PC,
     * then hosts every staged file and pushes a prepare-upload prompt to the target.
     *
     * The expected arrival count comes from the prepare-time record
     * ([trackRelayExpected]) — the upload session record is already gone by the time the
     * sender's relay-transfer request arrives, because ShareRoutes removes it the moment
     * the last file lands. The live-session lookup is kept only as a legacy fallback.
     *
     * Returns true when the prompt was delivered to the target's trusted session.
     */
    suspend fun relayUploadedSession(sessionId: String, targetFingerprint: String): Boolean {
        // Deduped files ([SKIP] at prepare time) are never re-uploaded, so the arrival wait
        // must count only files that were actually minted an upload token.
        val session = com.dexstudios.dex.core.network.server.routes.activeUploadSessions[sessionId]
        val expectedCount = relaySessionExpected[sessionId]?.expectedCount
            ?: session?.issuedTokens?.size
            ?: session?.request?.files?.size
            ?: return false
        val alias = relaySessionAliases[sessionId] ?: "Phone"

        // Wait until every file of the upload session physically landed here
        val deadline = System.currentTimeMillis() + 60_000L
        var staged: List<RelayReceivedFile>
        while (true) {
            staged = relaySessionFiles[sessionId].orEmpty().toList()
            if (staged.size >= expectedCount) break
            if (System.currentTimeMillis() >= deadline) return false
            delay(500L)
        }

        // Complete manifest: the deduplicated files first (their paths already exist here),
        // then the files that just arrived. Order is irrelevant to the target; membership is
        // not — forwarding the staged subset is what dropped files from the A -> PC -> B hop.
        val manifest = relaySessionDeduped[sessionId].orEmpty() + staged
        return hostAndPushAsync(
            targetFingerprint = targetFingerprint,
            files = manifest.map { it.absolutePath to it.relativePath },
            senderAlias = alias,
        )
    }

    /**
     * Hosts [files] locally and pushes a `prepare-upload` prompt to [targetFingerprint]'s
     * WebSocket session. Delivery is only attempted over TRUSTED sessions; untrusted peers
     * never learn the hosted pull tokens.
     *
     * The returned boolean means ONLY that the prompt reached the device. Transfer reality is
     * reported through [onCompleted] (every file pulled by the peer) / [onExpired] (TTL lapsed
     * without a complete pull). With [awaitCompletion], the caller stays suspended until
     * delivery/expiry, and cancelling it revokes the hosted files and active responses.
     */
    suspend fun hostAndPushAsync(
        targetFingerprint: String,
        files: List<Pair<String, String?>>,
        senderAlias: String,
        onCompleted: (() -> Unit)? = null,
        onExpired: (() -> Unit)? = null,
        awaitCompletion: Boolean = false,
    ): Boolean {
        if (targetFingerprint.isEmpty() || files.isEmpty()) return false
        if (!WebSocketConnectionManager.isTrusted(targetFingerprint)) return false

        val pushId = UUID.randomUUID().toString()
        val fileMap = mutableMapOf<String, FileDto>()
        val ids = mutableSetOf<String>()

        for ((path, relativePath) in files) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val fileId = UUID.randomUUID().toString()
                val pullToken = UUID.randomUUID().toString()

                hostedFiles[fileId] = file.absolutePath
                hostedFileTokens[fileId] = pullToken
                hostedFileLastAccess[fileId] = System.currentTimeMillis()

                ids.add(fileId)
                fileMap[fileId] = FileDto(
                    id = fileId,
                    fileName = file.name,
                    size = file.length(),
                    fileType = "application/octet-stream",
                    token = pullToken,
                    relativePath = if (relativePath.isNullOrEmpty()) null else relativePath,
                )
            }
        }

        if (fileMap.isEmpty()) return false
        val push = HostedPush(pushId, ids, onCompleted, onExpired)
        pushes[pushId] = push
        ensureMaintenanceLoop()
        var delivered = false
        try {
            val deviceConfig = org.koin.core.context.GlobalContext.get().get<com.dexstudios.dex.core.network.DeviceConfig>()
            val prepareReq = PrepareUploadRequestDto(
                info = RegisterDto(
                    alias = senderAlias,
                    version = "2.0",
                    deviceModel = "PC",
                    deviceType = "desktop",
                    fingerprint = deviceConfig.fingerprint.ifEmpty { "desktop-migration" },
                    port = com.dexstudios.dex.core.network.DeXPorts.HTTPS,
                    quicPort = com.dexstudios.dex.core.network.DeXPorts.QUIC,
                    tcpFallbackPort = com.dexstudios.dex.core.network.DeXPorts.PULL,
                    protocol = "localsend",
                    download = true,
                ),
                files = fileMap,
            )
            val jsonStr = buildJsonObject {
                put(FieldNames.TYPE, MessageTypes.PREPARE_UPLOAD)
                put(FieldNames.DATA, Json.encodeToJsonElement(PrepareUploadRequestDto.serializer(), prepareReq))
            }.toString()
            delivered = WebSocketConnectionManager.sendToTrusted(targetFingerprint, jsonStr)
            if (!delivered) finishPush(push, completed = false)
            return if (delivered && awaitCompletion) push.completion.await() else delivered
        } finally {
            // Awaiting callers own the hosted lifetime. Cancellation revokes tokens and
            // active streams without firing completion/expiry callbacks into another session.
            if (!delivered || awaitCompletion) finishPush(push, completed = false, notifyCallbacks = false)
        }
    }
}
