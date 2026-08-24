package com.dexstudios.dex.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dexstudios.dex.auth.AuthState
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the trust persistence contract: pairing grants survive a full hydration cycle
 * from disk (the regression behind desktop launches starting with empty AuthState), and
 * revocation removes every trace — fingerprint, token, and first-pair timestamp.
 */
class DeviceManagerPersistenceTest {

    private val tempDir = Files.createTempDirectory("dex_devicemanager_test")
    private val storePath = tempDir.resolve("trust.preferences_pb")
    private lateinit var scope: kotlinx.coroutines.CoroutineScope

    @Before
    fun setUp() {
        AuthState.updateFingerprints(emptySet())
        AuthState.updateTokens(emptyMap())
        AuthState.updateTimes(emptyMap())
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.coroutineContext.cancelChildren()
        AuthState.updateFingerprints(emptySet())
        AuthState.updateTokens(emptyMap())
        AuthState.updateTimes(emptyMap())
        tempDir.toFile().deleteRecursively()
    }

    private fun TestScope.newStore() = PreferenceDataStoreFactory.createWithPath(
        produceFile = { storePath.toString().toPath() },
    )

    @Test
    fun `paired fingerprint and token hydrate from disk after re-init`() = runTest {
        val store = newStore()
        DeviceManager.init(store)
        DeviceManager.savePairedFingerprint("fp-phone")
        DeviceManager.savePairedToken("fp-phone", "pairtok-1")

        // Simulate a process restart: wipe the in-memory mirror, re-read the SAME disk store.
        AuthState.updateFingerprints(emptySet())
        AuthState.updateTokens(emptyMap())
        AuthState.updateTimes(emptyMap())
        DeviceManager.init(store)

        assertTrue(AuthState.pairedFingerprints.value.contains("fp-phone"), "fingerprint must survive restart")
        assertEquals("pairtok-1", AuthState.pairedTokens.value["fp-phone"], "token must survive restart")
        assertTrue(AuthState.pairedTimes.value.containsKey("fp-phone"))
    }

    @Test
    fun `removePairedFingerprint clears every trace on disk and memory`() = runTest {
        val store = newStore()
        DeviceManager.init(store)
        DeviceManager.savePairedFingerprint("fp-gone")
        DeviceManager.savePairedToken("fp-gone", "tok-gone")

        DeviceManager.removePairedFingerprint("fp-gone")
        assertFalse(AuthState.pairedFingerprints.value.contains("fp-gone"))
        assertFalse(AuthState.pairedTokens.value.containsKey("fp-gone"))

        // Re-hydration must NOT resurrect the revoked pairing.
        AuthState.updateFingerprints(setOf("stale-mirror-entry"))
        DeviceManager.init(store)
        assertFalse(
            AuthState.pairedFingerprints.value.contains("fp-gone"),
            "revoked pairing must stay revoked across restart",
        )
    }

    @Test
    fun `first-pair timestamp is preserved across re-grants`() = runTest {
        val store = newStore()
        DeviceManager.init(store)
        DeviceManager.savePairedFingerprint("fp-stable")
        val first = requireNotNull(AuthState.pairedTimes.value["fp-stable"])

        Thread.sleep(5)
        DeviceManager.savePairedFingerprint("fp-stable")
        assertEquals(first, AuthState.pairedTimes.value["fp-stable"], "re-pairing must not reset the first-pair time")
    }
}
