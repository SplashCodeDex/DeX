package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.network.server.DexRequestStore
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable
data class ExplorerFolderItem(
    val id: String,
    val name: String,
    val uri: String
)

@Serializable
data class ExplorerFileEntry(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val thumbBase64: String? = null
)

@Serializable
data class PullFileItem(
    val uri: String,
    val name: String,
    val size: Long
)

data class PullProgressState(
    val requestId: String = "",
    val activeFileName: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val isPulling: Boolean = false,
    val isDone: Boolean = false
)

class FileExplorerService {
    private val json = Json { ignoreUnknownKeys = true }

    private val _pullProgress = MutableStateFlow(PullProgressState())
    val pullProgress = _pullProgress.asStateFlow()

    /**
     * Queries the connected phone over WebSocket for its SAF-granted shared folders.
     */
    suspend fun listFolders(fingerprint: String): List<ExplorerFolderItem> = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank()) return@withContext emptyList()

        val requestId = UUID.randomUUID().toString()
        val requestPayload = buildJsonObject {
            put("type", "list-shared-folders")
            putJsonObject("data") {
                put("requestId", requestId)
            }
        }.toString()

        val deferred = DexRequestStore.createRequest(requestId)
        val sent = WebSocketConnectionManager.sendRequest(fingerprint, requestPayload)
        if (!sent) {
            DexRequestStore.cancelRequest(requestId)
            return@withContext emptyList()
        }

        val reply = withTimeoutOrNull(25000) { deferred.await() } ?: run {
            DexRequestStore.cancelRequest(requestId)
            return@withContext emptyList()
        }

        try {
            val foldersObj = reply["folders"]?.jsonObject ?: return@withContext emptyList()
            foldersObj.entries.map { (id, elem) ->
                val fObj = elem.jsonObject
                ExplorerFolderItem(
                    id = id,
                    name = fObj["name"]?.jsonPrimitive?.content ?: id,
                    uri = fObj["uri"]?.jsonPrimitive?.content ?: ""
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Queries the connected phone to list children inside the given SAF folder URI.
     */
    suspend fun browseFolder(fingerprint: String, folderUri: String): List<ExplorerFileEntry> = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank() || folderUri.isBlank()) return@withContext emptyList()

        val requestId = UUID.randomUUID().toString()
        val requestPayload = buildJsonObject {
            put("type", "browse-folder")
            putJsonObject("data") {
                put("requestId", requestId)
                put("folderUri", folderUri)
            }
        }.toString()

        val deferred = DexRequestStore.createRequest(requestId)
        val sent = WebSocketConnectionManager.sendRequest(fingerprint, requestPayload)
        if (!sent) {
            DexRequestStore.cancelRequest(requestId)
            return@withContext emptyList()
        }

        val reply = withTimeoutOrNull(25000) { deferred.await() } ?: run {
            DexRequestStore.cancelRequest(requestId)
            return@withContext emptyList()
        }

        try {
            val entriesObj = reply["entries"]?.jsonObject ?: return@withContext emptyList()
            entriesObj.entries.map { (uri, elem) ->
                val eObj = elem.jsonObject
                val thumb = eObj["thumb"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ExplorerFileEntry(
                    uri = uri,
                    name = eObj["name"]?.jsonPrimitive?.content ?: "unnamed",
                    isDirectory = eObj["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false,
                    size = eObj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    thumbBase64 = thumb
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Prompts the user on their phone to pick and grant access to a new folder via SAF picker.
     */
    suspend fun grantFolder(fingerprint: String): ExplorerFolderItem? = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank()) return@withContext null

        val requestId = UUID.randomUUID().toString()
        val requestPayload = buildJsonObject {
            put("type", "grant-shared-folder")
            putJsonObject("data") {
                put("requestId", requestId)
            }
        }.toString()

        val deferred = DexRequestStore.createRequest(requestId)
        val sent = WebSocketConnectionManager.sendRequest(fingerprint, requestPayload)
        if (!sent) {
            DexRequestStore.cancelRequest(requestId)
            return@withContext null
        }

        // Granting folder requires user interaction on the phone (up to 190s)
        val reply = withTimeoutOrNull(190000) { deferred.await() } ?: run {
            DexRequestStore.cancelRequest(requestId)
            return@withContext null
        }

        try {
            val uri = reply["uri"]?.jsonPrimitive?.content ?: return@withContext null
            val name = reply["name"]?.jsonPrimitive?.content ?: "Shared Folder"
            ExplorerFolderItem(id = UUID.randomUUID().toString(), name = name, uri = uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Requests the phone to push the specified files to the PC via standard LocalSend upload.
     */
    suspend fun pullFiles(fingerprint: String, files: List<PullFileItem>): String? = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank() || files.isEmpty()) return@withContext null

        val requestId = UUID.randomUUID().toString()
        val requestPayload = buildJsonObject {
            put("type", "pull-files")
            putJsonObject("data") {
                put("requestId", requestId)
                putJsonArray("files") {
                    files.forEach { f ->
                        addJsonObject {
                            put("uri", f.uri)
                            put("name", f.name)
                            put("size", f.size)
                        }
                    }
                }
            }
        }.toString()

        val sent = WebSocketConnectionManager.sendRequest(fingerprint, requestPayload)
        if (!sent) return@withContext null

        _pullProgress.value = PullProgressState(
            requestId = requestId,
            totalFiles = files.size,
            totalBytes = files.sumOf { it.size },
            isPulling = true
        )

        requestId
    }

    /**
     * Cancels an in-flight pull operation.
     */
    suspend fun cancelPull(fingerprint: String, requestId: String) = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank() || requestId.isBlank()) return@withContext

        val cancelPayload = buildJsonObject {
            put("type", "pull-cancel")
            putJsonObject("data") {
                put("requestId", requestId)
            }
        }.toString()

        WebSocketConnectionManager.sendRequest(fingerprint, cancelPayload)
        DexRequestStore.cancelRequest(requestId)
        _pullProgress.value = _pullProgress.value.copy(isPulling = false, isDone = true)
    }

    fun updatePullProgress(progress: PullProgressState) {
        _pullProgress.value = progress
    }
}
