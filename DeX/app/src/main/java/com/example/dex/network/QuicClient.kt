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
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HTTP/3 (QUIC) client for the DeX PC, backed by Cronet (Chromium's network stack).
 *
 * QUIC only works once the PC's certificate is installed as a trusted CA on this device
 * (see the Settings -> QUIC flow). Until then [certInstalled] is false and callers
 * should fall back to the plain HTTP/1.1 path.
 */
class QuicClient(private val context: Context) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var engine: CronetEngine? = null

    var certInstalled: Boolean
        get() = context.getSharedPreferences("dex_quic_prefs", Context.MODE_PRIVATE)
            .getBoolean("pc_cert_installed", false)
        set(value) {
            context.getSharedPreferences("dex_quic_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("pc_cert_installed", value).apply()
        }

    fun init() {
        if (engine != null) return
        try {
            engine = CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(false)
                .enableBrotli(true)
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 8L * 1024 * 1024)
                .build()
        } catch (t: Throwable) {
            Timber.e(t, "Cronet engine init failed; QUIC unavailable")
            engine = null
        }
    }

    fun available(): Boolean {
        init()
        return certInstalled && engine != null
    }

    /** Fetches the PC's stable certificate (DER) from /local/cert over HTTPS. */
    fun fetchCert(ip: String, port: Int = 53317): ByteArray? {
        return try {
            val url = java.net.URL("https://$ip:$port/local/cert")
            val conn = url.openConnection() as javax.net.ssl.HttpsURLConnection
            conn.sslSocketFactory = trustAllSslContext.socketFactory
            conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } catch (t: Throwable) {
            Timber.e(t, "Certificate fetch from PC failed")
            null
        }
    }

    private val trustAllSslContext: javax.net.ssl.SSLContext by lazy {
        val tm = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(tm), java.security.SecureRandom())
        ctx
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
        onProgress: (current: Float, aggregate: Float) -> Unit = { _, _ -> },
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
                onProgress(current, aggregate)
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
                val ok = info.httpStatusCode in 200..299
                if (!ok) Timber.w("QUIC upload failed with HTTP ${info.httpStatusCode}")
                onResult(ok)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
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
}
