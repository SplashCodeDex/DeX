package com.dexstudios.dex.core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.ktor.client.engine.*
import io.ktor.client.plugins.onUpload
import kotlin.time.Duration.Companion.seconds

class ClientEngine(
    private val client: HttpClient,
    private val quicClient: IQuicClient? = null,
    private val deviceConfig: DeviceConfig? = null,
    private val onCancelUpload: ((String) -> Unit)? = null
) {
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + Job())
    private var resetJob: Job? = null

    private val _uploadState = MutableStateFlow(UploadState())
    val uploadState = _uploadState.asStateFlow()

    /** Progress/final state updates from transfer workers (parallel uploads own the math). */
    fun updateUploadState(state: UploadState) {
        _uploadState.value = state
        if (state.isSuccess) {
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(6.seconds) // Keep success state for 6s
                resetUploadState()
            }
        }
    }

    /** Negotiated protocol ("h3", "http/1.1", ...) of the last completed QUIC upload. */
    fun lastUploadProtocol(): String = quicClient?.lastUploadProtocol ?: ""

    var activeWorkId: String? = null

    fun resetUploadState() {
        resetJob?.cancel()
        _uploadState.value = UploadState()
    }

    fun triggerDemo() {
        resetJob?.cancel()
        scope.launch {
            _uploadState.value = UploadState(
                fileName = "Nico_Photo_Collection.zip",
                progress = 0f,
                aggregateProgress = 0f,
                isUploading = true,
                speedBps = 8388608L, // 8 MB/s
                protocol = "QUIC",
                peerName = "Danny Lopez",
                totalFiles = 5
            )

            // Progress simulation
            for (i in 1..100) {
                delay(60)
                val p = i / 100f
                _uploadState.value = _uploadState.value.copy(
                    progress = p,
                    aggregateProgress = p
                )
            }

            _uploadState.value = _uploadState.value.copy(isUploading = false, isSuccess = true)
            delay(6.seconds)
            resetUploadState()
        }
    }

    /**
     * Picks the bearer token for a target device. Same-email devices are auto-trusted:
     * the Google account ID (unguessable) wins when both sides share it, then the identity
     * hash (manual email), and finally the PIN-pairing token.
     */
    fun authToken(targetFingerprint: String?, targetIdentityHash: String?, targetGoogleSub: String? = null): String? {
        val mySub = deviceConfig?.googleSub
        if (!mySub.isNullOrEmpty() && targetGoogleSub == mySub) return mySub
        val myIdentity = deviceConfig?.identityHash
        if (!myIdentity.isNullOrEmpty() && targetIdentityHash == myIdentity) return myIdentity
        return targetFingerprint?.let { AuthState.pairedTokens[it] }
    }

    fun finishUpload(successCount: Int, totalFiles: Int) {
        if (successCount > 0) {
            _uploadState.value = _uploadState.value.copy(
                isUploading = false,
                isSuccess = true,
                fileName = "$successCount of $totalFiles files"
            )
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(5.seconds)
                resetUploadState()
            }
        } else {
            _uploadState.value = _uploadState.value.copy(
                isUploading = false,
                error = "Upload failed for all files"
            )
        }
    }

    fun cancelUpload() {
        activeWorkId?.let {
            onCancelUpload?.invoke(it)
        }
        activeWorkId = null
        _uploadState.value = _uploadState.value.copy(
            isUploading = false,
            error = "Upload cancelled"
        )
    }

    /** Result of [prepareUpload]: httpStatus is -1 when the transport failed, otherwise the HTTP status. */
    data class PrepareResult(
        val response: PrepareUploadResponseDto?,
        val httpStatus: Int
    )

    suspend fun prepareUpload(ip: String, port: Int, request: PrepareUploadRequestDto, token: String? = null): PrepareResult = withContext(Dispatchers.IO) {
        try {
            val response = client.post("https://$ip:$port/api/localsend/v2/prepare-upload") {
                contentType(ContentType.Application.Json)
                if (!token.isNullOrEmpty()) header(HttpHeaders.Authorization, "Bearer $token")
                setBody(request)
            }
            if (response.status.isSuccess()) {
                PrepareResult(response.body<PrepareUploadResponseDto>(), response.status.value)
            } else {
                PrepareResult(null, response.status.value)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            PrepareResult(null, -1)
        }
    }

    suspend fun uploadFile(
        ip: String, port: Int, sessionId: String, fileId: String, fileName: String,
        token: String, stream: java.io.InputStream, fileSize: Long,
        onProgress: (Long) -> Unit = {}
    ): UploadOutcome = withContext(Dispatchers.IO) {
        try {
            val response = client.post("https://$ip:$port/api/localsend/v2/upload") {
                url {
                    parameters.append("sessionId", sessionId)
                    parameters.append("fileId", fileId)
                    parameters.append("token", token)
                }
                onUpload { bytesSentTotal, _ ->
                    onProgress(bytesSentTotal)
                }
setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength = fileSize
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        withContext(Dispatchers.IO) {
                            val buffer = ByteArray(81920)
                            var bytesRead = stream.read(buffer)
                            while (bytesRead != -1) {
                                channel.writeFully(buffer, 0, bytesRead)
                                bytesRead = stream.read(buffer)
                            }
                            stream.close()
                            channel.flush()
                        }
                    }
                })
            }
            UploadOutcome(response.status.isSuccess(), response.status.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // Let the cancellation bubble up
        } catch (e: Exception) {
            e.printStackTrace()
            UploadOutcome(false, -1)
        }
    }

    fun quicAvailable(): Boolean = quicClient?.available() == true

    /**
     * HTTP/3 (QUIC) upload via Cronet, with the same contract and progress semantics as
     * [uploadFile]. httpStatus of -1 means the transport failed.
     */
    suspend fun uploadFileQuic(
        ip: String, port: Int, sessionId: String, fileId: String, fileName: String,
        token: String, stream: java.io.InputStream, fileSize: Long,
        onProgress: (Long) -> Unit = {}
    ): UploadOutcome {
        val qc = quicClient ?: return UploadOutcome(false, -1)
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val request = qc.uploadFile(
                ip, port, sessionId, fileId, fileName, token, stream, fileSize,
                onProgress = { bytes -> onProgress(bytes) },
                onResult = { ok, status ->
                    if (!cont.isCancelled) cont.resume(UploadOutcome(ok, status))
                }
            )
            if (request == null) {
                if (!cont.isCancelled) cont.resume(UploadOutcome(false, -1))
            } else {
                cont.invokeOnCancellation {
                    try {
                        request.javaClass.getMethod("cancel").invoke(request)
                    } catch (e: Exception) {}
                }
            }
        }
    }

    /**
     * HTTP/3 (QUIC) download from the PC via Cronet. Streams GET /download/{fileId} into
     * [output] and reports received bytes. Returns a [DownloadOutcome]; httpStatus of -1
     * means the transport failed (e.g. Cronet engine unavailable). protocol is the
     * negotiated ALPN ("h3", "http/1.1", ...) and is empty when unknown.
     */
    suspend fun downloadFileQuic(
        ip: String,
        port: Int,
        fileId: String,
        token: String?,
        output: java.nio.channels.WritableByteChannel,
        onProgress: (Long) -> Unit = {}
    ): DownloadOutcome {
        val qc = quicClient ?: return DownloadOutcome(false, -1)
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val request = qc.downloadFile(
                ip, port, fileId, token, output,
                onProgress = { bytes -> onProgress(bytes) },
                onResult = { ok, status, protocol ->
                    if (!cont.isCancelled) cont.resume(DownloadOutcome(ok, status, protocol))
                }
            )
            if (request == null) {
                if (!cont.isCancelled) cont.resume(DownloadOutcome(false, -1))
            } else {
                cont.invokeOnCancellation {
                    try {
                        request.javaClass.getMethod("cancel").invoke(request)
                    } catch (e: Exception) {}
                }
            }
        }
    }
    suspend fun sendClipboard(ip: String, port: Int, text: String, targetFingerprint: String? = null, targetIdentityHash: String? = null, targetGoogleSub: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = authToken(targetFingerprint, targetIdentityHash, targetGoogleSub)
            val response = client.post("https://$ip:$port/api/dex/clipboard") {
                contentType(ContentType.Text.Plain)
                if (!token.isNullOrEmpty()) header(HttpHeaders.Authorization, "Bearer $token")
                setBody(text)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class UploadState(
    val fileName: String = "",
    val currentFileIndex: Int = 1,
    val totalFiles: Int = 1,
    val progress: Float = 0f,
    val aggregateProgress: Float = 0f,
    val isUploading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val protocol: String = "",
    val speedBps: Long = 0L,
    val targetFingerprint: String? = null,
    val peerName: String? = null,
    val peerPicture: String? = null
)

data class DownloadOutcome(
    val ok: Boolean,
    val httpStatus: Int = 0,
    val protocol: String = ""
)

data class UploadOutcome(
    val ok: Boolean,
    val httpStatus: Int = 0
)
