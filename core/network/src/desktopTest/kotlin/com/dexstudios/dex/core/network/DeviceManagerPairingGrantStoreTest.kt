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
import kotlin.test.assertTrue

/**
 * Plan 026 adapter contract: the domain [com.dexstudios.dex.core.domain.pairing.PairingGrantStore]
 * port, wired to [DeviceManager] via [DeviceManagerPairingGrantStore], must persist BOTH
 * sides of a proven grant — fingerprint AND the freshly minted per-device token — exactly
 * like the engine's pre-extraction inline default did.
 */
class DeviceManagerPairingGrantStoreTest {

    private val tempDir = Files.createTempDirectory("dex_grant_store_test")
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
    fun `grant mints a fresh token and persists fingerprint and token`() = runTest {
        DeviceManager.init(newStore())
        val store = DeviceManagerPairingGrantStore()

        val token = store.grant("fp-phone")

        assertTrue(token.isNotBlank(), "a UUID token must be minted")
        assertTrue(AuthState.pairedFingerprints.value.contains("fp-phone"), "fingerprint must be persisted")
        assertEquals(token, AuthState.pairedTokens.value["fp-phone"], "the minted token must be stored for the peer")
    }

    @Test
    fun `successive grants mint distinct tokens per device`() = runTest {
        DeviceManager.init(newStore())
        val store = DeviceManagerPairingGrantStore()

        val tokenA = store.grant("fp-a")
        val tokenB = store.grant("fp-b")

        assertTrue(tokenA != tokenB, "per-device tokens must be independent")
        assertEquals(tokenA, AuthState.pairedTokens.value["fp-a"])
        assertEquals(tokenB, AuthState.pairedTokens.value["fp-b"])
    }
}
