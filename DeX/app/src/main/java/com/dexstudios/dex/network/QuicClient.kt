package com.dexstudios.dex.network

import android.content.Context
import com.google.android.gms.net.CronetProviderInstaller
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
import java.util.concurrent.TimeUnit

/**
 * HTTP/3 (QUIC) client for the DeX PC, backed by Cronet (Chromium's network stack).
 *
 * Every PC's certificate is signed by the bundled DeX root CA, so QUIC works with
 * zero user setup — Cronet trusts the CA via the app's network security config.
 */
class QuicClient(private val context: Context) : java.io.Closeable {

    private val executor: ExecutorService = Executors.newFixedThreadPool(4)

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
    private var engine: CronetEngine? = null



    // Negotiated protocol ("h3", "http/1.1", ...) of the last completed upload
    @Volatile
    var lastUploadProtocol: String = ""

    init {
        executor.execute { init() }
    }

    fun init() {
        if (engine != null) return
        try {
            runCatching { CronetProviderInstaller.installProvider(context) }
            val builder = CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(false)
                .enableBrotli(true)
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 8L * 1024 * 1024)
            // Hint remembered PCs so even the first transfer of the day skips the
            // HTTP/1.1 warm-up and attempts QUIC directly; falls back to TCP 48424.
            PcMemory.ip(context)?.let { ip ->
                builder.addQuicHint(ip, PcMemory.quicPort(context), PcMemory.port(context))
            }
            engine = builder.build()
            Timber.i("Cronet Play Services engine initialized successfully")
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
     * Uploads a file to the PC over HTTP/3 (QUIC). Streams [stream] to
     * /api/localsend/v2/upload and reports sent bytes (cumulative per request).
     *
     * Returns the started [UrlRequest] so callers can cancel it, or null when the engine
     * is unavailable. [onResult] receives (success, httpStatusCode); -1 means transport failure.
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
        onProgress: (bytesSent: Long) -> Unit = {},
        onResult: (Boolean, Int) -> Unit
    ): UrlRequest? {
        val engine = engine ?: return null
        val url = ApiRoutes.httpsUrl(ip, port, ApiRoutes.UPLOAD) +
            "?sessionId=${enc(sessionId)}&fileId=${enc(fileId)}&token=${enc(token)}"

        var sentBytes = 0L
        var reported = false

        fun report(ok: Boolean, status: Int) {
            if (!reported) {
                reported = true
                onResult(ok, status)
            }
        }

        val provider = object : UploadDataProvider() {
            private val inChannel = java.nio.channels.Channels.newChannel(stream)
            override fun getLength(): Long = fileSize

            override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
                if (buffer.remaining() <= 0 || sentBytes >= fileSize) {
                    sink.onReadSucceeded(false)
                    return
                }
                val read = try {
                    inChannel.read(buffer)
                } catch (e: Exception) {
                    sink.onReadError(e)
                    return
                }
                if (read <= 0) {
                    sink.onReadSucceeded(false)
                    return
                }
                sentBytes += read
                onProgress(sentBytes)
                sink.onReadSucceeded(sentBytes >= fileSize)
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
                request.read(ByteBuffer.allocateDirect(65536))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                lastUploadProtocol = info.negotiatedProtocol
                val ok = info.httpStatusCode in 200..299
                if (!ok) Timber.w("QUIC upload failed with HTTP ${info.httpStatusCode}")
                report(ok, info.httpStatusCode)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
                lastUploadProtocol = info.negotiatedProtocol
                Timber.e(error, "QUIC upload failed")
                report(false, -1)
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
                report(false, -1)
            }
        }

        val request = engine.newUrlRequestBuilder(url, listener, executor)
            .setHttpMethod("POST")
            .addHeader("Content-Type", "application/octet-stream")
            .setUploadDataProvider(provider, executor)
            .build()
        request.start()
        return request
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
        token: String?,
        output: java.nio.channels.WritableByteChannel,
        onProgress: (bytesReceived: Long) -> Unit = {},
        onResult: (Boolean, Int, String) -> Unit
    ): UrlRequest? {
        val engine = engine ?: return null
        val url = ApiRoutes.httpsUrl(ip, port, "${ApiRoutes.DOWNLOAD}/${enc(fileId)}") + (if (!token.isNullOrEmpty()) "?token=${enc(token)}" else "")

        var receivedBytes = 0L
        var reported = false
        val buffer = ByteBuffer.allocateDirect(65536)

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
                    report(false, info.httpStatusCode, info.negotiatedProtocol)
                    request.cancel()
                    return
                }
                request.read(buffer)
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                try {
                    val n = byteBuffer.remaining()
                    while (byteBuffer.hasRemaining()) {
                        output.write(byteBuffer)
                    }
                    receivedBytes += n
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
                report(true, info.httpStatusCode, info.negotiatedProtocol)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
                Timber.e(error, "QUIC download failed")
                report(false, -1, info.negotiatedProtocol)
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
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
