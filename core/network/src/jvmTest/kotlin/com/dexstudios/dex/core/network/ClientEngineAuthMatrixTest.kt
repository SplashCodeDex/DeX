package com.dexstudios.dex.core.network

import com.dexstudios.dex.auth.AuthState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the [ClientEngine.authToken] trust-priority contract against the REAL shared
 * token store ([AuthState]):
 * same-account googleSub > identity hash (manual email) > PIN-pairing token.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientEngineAuthMatrixTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // ClientEngine owns a scope on Dispatchers.Main
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        AuthState.updateTokens(emptyMap())
        Dispatchers.resetMain()
    }

    private fun engineWithConfig(googleSub: String? = null, identityHash: String? = null): ClientEngine {
        val config = mockk<DeviceConfig> {
            every { this@mockk.googleSub } returns googleSub.orEmpty()
            every { this@mockk.identityHash } returns identityHash.orEmpty()
        }
        return ClientEngine(client = HttpClient(MockEngine { respondOk() }), deviceConfig = config)
    }

    @Test
    fun `authToken prefers googleSub when both sides share the account`() {
        val engine = engineWithConfig(googleSub = "sub_abc", identityHash = "hash_abc")

        AuthState.updateTokens(mapOf("fp_x" to "pairtok"))

        assertEquals("sub_abc", engine.authToken("fp_x", "hash_abc", targetGoogleSub = "sub_abc"))
    }

    @Test
    fun `authToken uses identityHash when target shares the manual email but no account`() {
        val engine = engineWithConfig(identityHash = "hash_manual")

        AuthState.updateTokens(mapOf("fp_x" to "pairtok"))

        assertEquals("hash_manual", engine.authToken("fp_x", "hash_manual"))
    }

    @Test
    fun `authToken falls through to pairing token when googleSub mismatches`() {
        val engine = engineWithConfig(googleSub = "sub_mine")

        AuthState.updateTokens(mapOf("fp_paired" to "pairtok_paired"))

        assertEquals(
            "pairtok_paired",
            engine.authToken("fp_paired", "other_hash", targetGoogleSub = "sub_theirs")
        )
    }

    @Test
    fun `authToken falls through to pairing token when identityHash mismatches`() {
        val engine = engineWithConfig(identityHash = "hash_mine")

        AuthState.updateTokens(mapOf("fp_paired" to "pairtok_paired"))

        assertEquals("pairtok_paired", engine.authToken("fp_paired", "hash_other"))
    }

    @Test
    fun `authToken resolves each fingerprint from the shared token store independently`() {
        val engine = engineWithConfig()

        AuthState.updateTokens(mapOf("fp_alpha" to "tok_a", "fp_beta" to "tok_b"))

        assertEquals("tok_a", engine.authToken("fp_alpha", null))
        assertEquals("tok_b", engine.authToken("fp_beta", null))
    }

    @Test
    fun `authToken returns null for unpaired target even with identity configured`() {
        val engine = engineWithConfig(identityHash = "hash_mine")

        assertNull(engine.authToken("fp_unknown", "hash_other"))
    }

    @Test
    fun `authToken ignores empty local googleSub even if target sends one`() {
        // Local has never signed in — a matching sub claimed from the wire must not be trusted.
        val engine = engineWithConfig(googleSub = "", identityHash = "")

        assertNull(engine.authToken("fp_unknown", null, targetGoogleSub = "sub_from_wire"))
    }
}