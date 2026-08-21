package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.FileDto
import com.dexstudios.dex.core.network.PrepareUploadRequestDto
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import com.dexstudios.dex.core.network.auth.IdentityManager

object RelayService {
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Shared maps for both PC-to-Phone pushes and phone-to-phone Relay hosted files (fileId -> absolute path)
    val hostedFiles = ConcurrentHashMap<String, String>()
    val hostedFileTokens = ConcurrentHashMap<String, String>()
    val hostedFileLastAccess = ConcurrentHashMap<String, Long>()

    // Relay fallback maps (phone-to-phone via PC)
    val relaySessionFiles = ConcurrentHashMap<String, MutableList<Pair<String, String>>>() // sessionId -> [(fileName, absolutePath)]
    val relaySessionAliases = ConcurrentHashMap<String, String>() // sessionId -> alias
    private val relaySessionTime = ConcurrentHashMap<String, Long>()
    private var relayCleanupJob: kotlinx.coroutines.Job? = null

    fun trackRelayFile(sessionId: String, fileName: String, absolutePath: String, senderAlias: String) {
        val list = relaySessionFiles.getOrPut(sessionId) { mutableListOf() }
        synchronized(list) {
            list.add(fileName to absolutePath)
        }
        relaySessionAliases[sessionId] = senderAlias
        relaySessionTime[sessionId] = System.currentTimeMillis()

        if (relayCleanupJob?.isActive != true) {
            relayCleanupJob = serviceScope.launch {
                while (true) {
                    delay(60_000L)
                    val now = System.currentTimeMillis()
                    val stale = relaySessionTime.entries.filter { (now - it.value) > 10 * 60 * 1000L }.map { it.key }
                    for (id in stale) {
                        relaySessionFiles.remove(id)
                        relaySessionAliases.remove(id)
                        relaySessionTime.remove(id)
                    }
                    if (relaySessionTime.isEmpty()) break
                }
            }
        }
    }

    suspend fun hostAndPushAsync(
        targetFingerprint: String,
        files: List<Pair<String, String?>>,
        senderAlias: String
    ): Boolean {
        if (targetFingerprint.isEmpty() || files.isEmpty()) return false

        val fileMap = mutableMapOf<String, FileDto>()

        for ((path, relativePath) in files) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val fileId = UUID.randomUUID().toString()
                val pullToken = UUID.randomUUID().toString()

                hostedFiles[fileId] = file.absolutePath
                hostedFileTokens[fileId] = pullToken
                hostedFileLastAccess[fileId] = System.currentTimeMillis()

                fileMap[fileId] = FileDto(
                    id = fileId,
                    fileName = file.name,
                    size = file.length(),
                    fileType = "application/octet-stream",
                    token = pullToken,
                    relativePath = if (relativePath.isNullOrEmpty()) null else relativePath
                )
            }
        }

        if (fileMap.isEmpty()) return false

        val prepareReq = PrepareUploadRequestDto(
            info = RegisterDto(
                alias = senderAlias,
                version = "2.0",
                deviceModel = "PC",
                deviceType = "desktop",
                fingerprint = IdentityManager.fingerprint.ifEmpty { "desktop-migration" },
                port = 48424,
                protocol = "localsend",
                download = false
            ),
            files = fileMap
        )

        val jsonStr = buildJsonObject {
            put("type", "prepare-upload")
            put("data", Json.encodeToJsonElement(PrepareUploadRequestDto.serializer(), prepareReq))
        }.toString()

        // Sliding TTL cleanup: files expire 5 minutes after their last pull request
        val hostedIds = fileMap.keys.toList()
        serviceScope.launch {
            while (true) {
                delay(60_000L) // Check every minute
                val now = System.currentTimeMillis()
                val stale = hostedIds.filter { id ->
                    val last = hostedFileLastAccess[id]
                    last == null || (now - last) > 5 * 60 * 1000L
                }
                
                for (id in stale) {
                    hostedFiles.remove(id)
                    hostedFileTokens.remove(id)
                    hostedFileLastAccess.remove(id)
                }
                
                if (hostedIds.none { hostedFiles.containsKey(it) }) break
            }
        }

        return WebSocketConnectionManager.sendRequest(targetFingerprint, jsonStr)
    }
}
