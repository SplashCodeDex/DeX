package com.dexstudios.dex.network

import android.annotation.SuppressLint
import android.content.Context
import timber.log.Timber
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use (TOFU) certificate pinning for the self-signed certificates
 * the PC and peer phones present (LocalSend-style ephemeral TLS).
 *
 * Instead of trusting every certificate (the old behavior — which let anyone on
 * the same network intercept all transfers), the first certificate seen for a
 * given host is pinned and every later connection to that host must present the
 * same certificate. A changed certificate is rejected with a [CertificateException].
 *
 * The pin is the SHA-256 of the leaf certificate's DER encoding, persisted in
 * SharedPreferences keyed by host so it survives process restarts. The host is
 * learned from OkHttp's hostname verifier ([verifyHostname]) or set explicitly via
 * [setExpectedHost] before a raw handshake; when neither is available the leaf
 * cert's CN is used so the pin is still stable. [clear] drops all pins so the next
 * connection re-pins (e.g. after the user reinstalls/resets the PC).
 */
@SuppressLint("CustomX509TrustManager")
class PinnedTrustManager(context: Context) : X509TrustManager {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Set by the hostname verifier / call site so the pin is keyed by the real host. */
    @Volatile
    private var expectedHost: String? = null

    fun setExpectedHost(host: String?) {
        expectedHost = host
    }

    /**
     * OkHttp hostname verifier: capture the host and confirm the leaf matches the pin.
     * The self-signed cert's CN won't match the LAN IP, so instead of CN matching we
     * delegate the trust decision to the TOFU pin check.
     */
    fun verifyHostname(host: String, session: SSLSession): Boolean {
        setExpectedHost(host)
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }
            .getOrNull() ?: return false
        return runCatching { checkServerTrusted(arrayOf(leaf), "TLS") }.isSuccess
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Client certificates are not used by this app.
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")
        val leaf = chain[0]
        val pin = sha256Hex(leaf.encoded)

        // Prefer the host learned from the handshake; fall back to the cert CN so a
        // connection without a known host still gets a stable pin key.
        val host = expectedHost ?: leaf.subjectX500Principal?.name ?: "unknown"
        val key = "$KEY_PREFIX$host"
        val stored = prefs.getString(key, null)

        when {
            stored == null -> {
                // First use: pin it
                prefs.edit().putString(key, pin).apply()
                Timber.i("TLS: pinned certificate for %s (%s…)", host, pin.take(12))
            }
            stored == pin -> {
                // Matches the pinned cert — trusted
            }
            else -> {
                Timber.w("TLS: certificate for %s CHANGED (pinned %s…, got %s…)",
                    host, stored.take(12), pin.take(12))
                throw CertificateException(
                    "Certificate for $host changed. The device may have been reset, " +
                        "or someone on the network is intercepting the connection. " +
                        "Re-pair the device to trust the new certificate."
                )
            }
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** Drop every stored pin so the next connection re-pins (trust-on-first-use again). */
    fun clear() {
        prefs.edit().also { editor ->
            prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { editor.remove(it) }
            editor.apply()
        }
        Timber.i("TLS: cleared all pinned certificates")
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "dex_cert_pins"
        private const val KEY_PREFIX = "pin_"
    }
}
