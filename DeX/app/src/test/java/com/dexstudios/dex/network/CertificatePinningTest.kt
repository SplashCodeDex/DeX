package com.dexstudios.dex.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * TOFU pinning: the first cert for a host is trusted and stored; the same cert is
 * trusted on later connections; a *different* cert for the same host is rejected.
 */
class CertificatePinningTest {

    /** In-memory SharedPreferences stand-in so the pins persist across manager calls. */
    private fun fakePrefs(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg()] = secondArg<String?>(); editor
        }
        every { editor.remove(any()) } answers { store.remove(firstArg<String>()); editor }
        every { editor.apply() } returns Unit

        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers {
            store[firstArg()] as? String ?: secondArg()
        }
        every { prefs.edit() } returns editor
        every { prefs.all } answers { store.toMap() }
        return prefs
    }

    private fun manager(prefs: SharedPreferences): PinnedTrustManager {
        val context = mockk<Context>()
        val appContext = mockk<Context>()
        every { context.applicationContext } returns appContext
        every { appContext.getSharedPreferences(any(), any()) } returns prefs
        return PinnedTrustManager(context)
    }

    /** A fake X509 cert whose DER encoding is the given bytes (drives the SHA-256 pin). */
    private fun cert(derBytes: ByteArray, cn: String = "CN=test-device"): X509Certificate {
        val cert = mockk<X509Certificate>()
        every { cert.encoded } returns derBytes
        every { cert.subjectX500Principal } returns X500Principal(cn)
        return cert
    }

    @Test
    fun `first certificate is pinned and trusted`() {
        val mgr = manager(fakePrefs())
        mgr.setExpectedHost("192.168.1.10")
        // Should not throw
        mgr.checkServerTrusted(arrayOf(cert(byteArrayOf(1, 2, 3))), "TLS")
    }

    @Test
    fun `same certificate on reconnect is trusted`() {
        val prefs = fakePrefs()
        val mgr = manager(prefs)
        mgr.setExpectedHost("192.168.1.10")
        val leaf = cert(byteArrayOf(9, 9, 9))
        mgr.checkServerTrusted(arrayOf(leaf), "TLS")  // first use — pins
        mgr.checkServerTrusted(arrayOf(leaf), "TLS")  // reconnect — same pin, OK
    }

    @Test
    fun `changed certificate for same host is rejected`() {
        val prefs = fakePrefs()
        val mgr = manager(prefs)
        mgr.setExpectedHost("192.168.1.10")
        mgr.checkServerTrusted(arrayOf(cert(byteArrayOf(1, 1, 1))), "TLS")  // pin cert A

        try {
            mgr.checkServerTrusted(arrayOf(cert(byteArrayOf(2, 2, 2))), "TLS")  // cert B
            fail("Expected CertificateException for a changed certificate")
        } catch (e: CertificateException) {
            assertTrue(e.message!!.contains("changed"))
        }
    }

    @Test
    fun `empty chain is rejected`() {
        val mgr = manager(fakePrefs())
        try {
            mgr.checkServerTrusted(emptyArray(), "TLS")
            fail("Expected CertificateException for empty chain")
        } catch (e: CertificateException) {
            assertEquals("Empty certificate chain", e.message)
        }
    }

    @Test
    fun `clear drops pins so next connection re-pins`() {
        val prefs = fakePrefs()
        val mgr = manager(prefs)
        mgr.setExpectedHost("192.168.1.10")
        mgr.checkServerTrusted(arrayOf(cert(byteArrayOf(5, 5, 5))), "TLS")  // pin cert A

        mgr.clear()  // user reset the PC — drop pins

        // A new cert must now be accepted (first use again) rather than rejected
        mgr.checkServerTrusted(arrayOf(cert(byteArrayOf(6, 6, 6))), "TLS")
    }
}
