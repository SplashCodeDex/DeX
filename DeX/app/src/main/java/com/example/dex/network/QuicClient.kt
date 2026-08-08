package com.example.dex.network

import android.content.Context
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HTTP/3 (QUIC) client for the DeX PC, backed by Cronet (Chromium's network stack).
 *
 * Every PC's certificate is signed by the bundled DeX root CA, so QUIC works with
 * zero user setup — Cronet trusts the CA via the app's network security config.
 */
class QuicClient(private val context: Context) {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private var engine: CronetEngine? = null

    // The PC serves HTTP/3 on UDP 53316 and HTTP/1.1 on TCP 53317
    private companion object {
        const val QUIC_PORT = 53316
        const val HTTPS_PORT = 53317
    }

    // Negotiated protocol ("h3", "http/1.1", ...) of the last completed upload
    @Volatile
    var lastUploadProtocol: String = ""

    fun init() {
        if (engine != null) return
        try {
            val builder = CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(false)
                .enableBrotli(true)
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 8L * 1024 * 1024)
            // Hint remembered PCs so even the first transfer of the day skips the
            // HTTP/1.1 warm-up and attempts QUIC directly; falls back to TCP 53317.
            PcMemory.ip(context)?.let { ip ->
                builder.addQuicHint(ip, QUIC_PORT, HTTPS_PORT)
            }
            engine = builder.build()
        } catch (t: Throwable) {
            Timber.e(t, "Cronet engine init failed; QUIC unavailable")
            engine = null
        }
    }

    fun available(): Boolean {
        init()
        return engine != null
    }

    /**
     * Uploads a file to the PC over HTTP/3 (QUIC). Mirrors ClientEngine.uploadFile's contract:
     * streams [stream] to /api/localsend/v2/upload and reports per-file and aggregate progress.
     */
    fun uploadFile(
        ip: String,
        port: Int,
        sessionId: String,
        fileId: String,
        fileName: String,
        token: String,
        stream: InputStream,
        fileSize: Long,
        fileIndex: Int = 1,
        totalFiles: Int = 1,
        previousBatchBytes: Long = 0L,
        totalBatchSize: Long = fileSize,
        onProgress: (current: Float, aggregate: Float, bytesSent: Long) -> Unit = { _, _, _ -> },
        onResult: (Boolean) -> Unit
    ) {
        val engine = engine
        if (engine == null) {
            onResult(false)
            return
        }
        val url = "https://$ip:$port/api/localsend/v2/upload" +
            "?sessionId=${enc(sessionId)}&fileId=${enc(fileId)}&token=${enc(token)}"

        var sentBytes = 0L
        val provider = object : UploadDataProvider() {
            override fun getLength(): Long = fileSize

            override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
                val toRead = minOf(buffer.remaining().toLong(), fileSize - sentBytes).toInt()
                if (toRead <= 0) {
                    sink.onReadSucceeded(false)
                    return
                }
                val chunk = ByteArray(toRead)
                val read = try {
                    stream.read(chunk)
                } catch (e: Exception) {
                    sink.onReadError(e)
                    return
                }
                if (read <= 0) {
                    sink.onReadSucceeded(false)
                    return
                }
                buffer.put(chunk, 0, read)
                sentBytes += read
                val current = if (fileSize > 0) sentBytes.toFloat() / fileSize else 0f
                val aggregate = if (totalBatchSize > 0) (previousBatchBytes + sentBytes).toFloat() / totalBatchSize else 0f
                onProgress(current, aggregate, sentBytes)
                sink.onReadSucceeded(sentBytes >= fileSize || read == chunk.size)
            }

            override fun rewind(sink: UploadDataSink) {
                // Streams are not resettable; QUIC 0-RTT retries of the upload body are rare
                sink.onRewindError(Exception("rewind unsupported"))
            }
        }

        val listener = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(16384))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                lastUploadProtocol = info.negotiatedProtocol ?: ""
                val ok = info.httpStatusCode in 200..299
                if (!ok) Timber.w("QUIC upload failed with HTTP ${info.httpStatusCode}")
                onResult(ok)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                lastUploadProtocol = info?.negotiatedProtocol ?: ""
                Timber.e(error, "QUIC upload failed")
                onResult(false)
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                onResult(false)
            }
        }

        val request = engine.newUrlRequestBuilder(url, listener, executor)
            .setHttpMethod("POST")
            .addHeader("Content-Type", "application/octet-stream")
            .setUploadDataProvider(provider, executor)
            .build()
        request.start()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Downloads a hosted file from the PC over HTTP/3 (QUIC). Streams GET /download/{fileId}
     * into [output] and reports received bytes. The first request per PC warms up on
     * HTTP/1.1, then Alt-Svc switches it to QUIC permanently — same path as uploads.
     *
     * Returns the started [UrlRequest] so callers can cancel it, or null when the engine
     * is unavailable. [onResult] receives (success, httpStatusCode, negotiatedProtocol);
     * -1 means transport failure, "" means the protocol is unknown.
     */
    fun downloadFile(
        ip: String,
        port: Int,
        fileId: String,
        output: OutputStream,
        onProgress: (bytesReceived: Long) -> Unit = {},
        onResult: (Boolean, Int, String) -> Unit
    ): UrlRequest? {
        val engine = engine
        if (engine == null) return null
        val url = "https://$ip:$port/download/${enc(fileId)}"

        var receivedBytes = 0L
        var reported = false
        val buffer = ByteBuffer.allocateDirect(16384)

        fun report(ok: Boolean, status: Int, protocol: String) {
            if (!reported) {
                reported = true
                onResult(ok, status, protocol)
            }
        }

        val listener = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode !in 200..299) {
                    report(false, info.httpStatusCode, info.negotiatedProtocol ?: "")
                    request.cancel()
                    return
                }
                request.read(buffer)
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                try {
                    val chunk = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(chunk)
                    output.write(chunk)
                    receivedBytes += chunk.size
                    onProgress(receivedBytes)
                } catch (e: Exception) {
                    report(false, -1, "")
                    request.cancel()
                    return
                }
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                report(true, info.httpStatusCode, info.negotiatedProtocol ?: "")
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                Timber.e(error, "QUIC download failed")
                report(false, -1, info?.negotiatedProtocol ?: "")
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                report(false, -1, "")
            }
        }

        val request = engine.newUrlRequestBuilder(url, listener, executor)
            .setHttpMethod("GET")
            .build()
        request.start()
        return request
    }
}
