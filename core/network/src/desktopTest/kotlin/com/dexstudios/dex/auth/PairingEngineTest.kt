package com.dexstudios.dex.auth

import com.dexstudios.dex.core.network.DiscoveredDevice
import com.dexstudios.dex.core.network.RegisterDto
import com.dexstudios.dex.core.network.WebSocketEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairingEngineTest {

    private lateinit var webSocketEngine: WebSocketEngine
    private lateinit var pairingEngine: PairingEngine
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        webSocketEngine = mockk(relaxed = true)
        pairingEngine = PairingEngine()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(PairingState.Idle, pairingEngine.state.value)
    }

    @Test
    fun `initiatePairing transitions to QrPhase`() = runTest {
        val device = DiscoveredDevice(
            ip = "192.168.1.100",
            info = RegisterDto(
                alias = "TestPhone",
                version = "1.0",
                deviceModel = "Pixel",
                deviceType = "phone",
                fingerprint = "test-fingerprint",
                port = 53317,
                protocol = "http",
                download = true,
            ),
            viaWan = false,
            viaRoster = false,
        )

        pairingEngine.initiatePairing(device)

        // Verify state is QrPhase
        val state = pairingEngine.state.value
        assertIs<PairingState.QrPhase>(state)
        assertEquals("192.168.1.100", state.ip)
        assertEquals("test-fingerprint", state.fingerprint)
    }

    @Test
    fun `handleInboundPairingRequest transitions to PinPhase and returns 6-digit PIN`() = runTest {
        val ip = "192.168.1.200"
        val fingerprint = "inbound-fingerprint"

        val pin = pairingEngine.handleInboundPairingRequest(ip, fingerprint)

        // Verify PIN is exactly 6 digits
        assertTrue(pin.length == 6, "PIN should be 6 digits")
        assertTrue(pin.all { it.isDigit() }, "PIN should contain only digits")

        // Verify state is PinPhase
        val state = pairingEngine.state.value
        assertIs<PairingState.PinPhase>(state)
        assertEquals(ip, state.ip)
        assertEquals(fingerprint, state.fingerprint)
        assertEquals(pin, state.pinCode)
        assertEquals(0, state.digitCount)
    }

    @Test
    fun `reset transitions to Idle`() = runTest {
        pairingEngine.handleInboundPairingRequest("192.168.1.200", "fingerprint")
        assertIs<PairingState.PinPhase>(pairingEngine.state.value)

        pairingEngine.reset()
        assertEquals(PairingState.Idle, pairingEngine.state.value)
    }

    @Test
    fun `handlePinDigitEntered updates digit count`() = runTest {
        pairingEngine.handleInboundPairingRequest("192.168.1.200", "fingerprint")

        pairingEngine.handlePinDigitEntered(1)
        var state = pairingEngine.state.value
        assertIs<PairingState.PinPhase>(state)
        assertEquals(1, state.digitCount)

        pairingEngine.handlePinDigitEntered(6)
        state = pairingEngine.state.value
        assertIs<PairingState.PinPhase>(state)
        assertEquals(6, state.digitCount)
    }

    @Test
    fun `verifyInboundPin accepts only the exact PIN for the requesting fingerprint`() = runTest {
        val pin = pairingEngine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")

        assertTrue(pairingEngine.verifyInboundPin("inbound-fp", pin), "Correct PIN must verify")
    }

    @Test
    fun `verifyInboundPin rejects wrong PIN blank PIN or wrong fingerprint`() = runTest {
        val pin = pairingEngine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")

        assertTrue(!pairingEngine.verifyInboundPin("inbound-fp", pin.drop(1) + if (pin.last() != '0') '0' else '1'), "Wrong PIN must not verify")
        assertTrue(!pairingEngine.verifyInboundPin("inbound-fp", ""), "Blank PIN must not verify")
        assertTrue(!pairingEngine.verifyInboundPin("other-fp", pin), "Wrong fingerprint must not verify")
    }

    @Test
    fun `verifyInboundPin fails once pairing already resolved`() = runTest {
        val pin = pairingEngine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        pairingEngine.handlePairResponse(true)
        assertIs<PairingState.Success>(pairingEngine.state.value)

        assertTrue(!pairingEngine.verifyInboundPin("inbound-fp", pin), "PIN must not verify after pairing resolved")
    }

    @Test
    fun `handlePairResponse ignores stray responses after resolution`() = runTest {
        pairingEngine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        pairingEngine.handlePairResponse(true)
        assertIs<PairingState.Success>(pairingEngine.state.value)

        // A late duplicate rejection must never flip Success back to Error.
        pairingEngine.handlePairResponse(false)
        assertIs<PairingState.Success>(pairingEngine.state.value)
    }

    @Test
    fun `handlePairResponse rejects while awaiting resolution`() = runTest {
        pairingEngine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")

        pairingEngine.handlePairResponse(false)
        assertIs<PairingState.Error>(pairingEngine.state.value)
    }

    @Test
    fun `verifyInboundPin rejects an expired PIN`() = runTest {
        var now = 1_000_000L
        val engine = PairingEngine(scope = backgroundScope, nowMillis = { now })
        val pin = engine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")

        assertTrue(engine.verifyInboundPin("inbound-fp", pin), "Fresh PIN must verify")

        now += 60_001L
        assertTrue(!engine.verifyInboundPin("inbound-fp", pin), "Expired PIN must not verify")
    }

    @Test
    fun `unresolved pairing offer expires to Error after the TTL`() = runTest {
        var now = 1_000_000L
        val engine = PairingEngine(scope = backgroundScope, nowMillis = { now })
        engine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        assertIs<PairingState.PinPhase>(engine.state.value)

        advanceTimeBy(59_000L)
        assertIs<PairingState.PinPhase>(engine.state.value)

        advanceTimeBy(2_000L)
        val state = engine.state.value
        assertIs<PairingState.Error>(state)
        assertEquals("Pairing timed out", state.message)
    }

    @Test
    fun `resolved pairings are immune to the expiry sweep`() = runTest {
        var now = 1_000_000L
        val engine = PairingEngine(scope = backgroundScope, nowMillis = { now })
        engine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        engine.handlePairResponse(true)
        assertIs<PairingState.Success>(engine.state.value)

        advanceTimeBy(120_000L)
        assertIs<PairingState.Success>(engine.state.value)
    }

    @Test
    fun `unscanned QR phase offer also expires to Error after the TTL`() = runTest {
        var now = 2_000_000L
        val engine = PairingEngine(scope = backgroundScope, nowMillis = { now })
        val device = DiscoveredDevice(
            ip = "192.168.1.100",
            info = RegisterDto(
                alias = "TestPhone",
                version = "2.0",
                deviceModel = "Pixel",
                deviceType = "phone",
                fingerprint = "qr-fp",
                port = 48424,
                protocol = "https",
                download = true,
            ),
            viaWan = false,
            viaRoster = false,
        )

        engine.initiatePairing(device)
        assertIs<PairingState.QrPhase>(engine.state.value)

        advanceTimeBy(59_000L)
        assertIs<PairingState.QrPhase>(engine.state.value)

        advanceTimeBy(2_000L)
        val state = engine.state.value
        assertIs<PairingState.Error>(state)
        assertEquals("Pairing timed out", state.message)
    }

    @Test
    fun `persistent accept mints a pairing token and replies pair-accepted`() = runTest {
        val engine = PairingEngine(scope = backgroundScope)
        val sent = mutableListOf<String>()
        engine.outboundSender = { _, json ->
            sent.add(json)
            true
        }
        engine.deviceFingerprintProvider = { "pc-fp" }
        engine.persistentGrant = { fp -> "minted-token-for-$fp" }

        engine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        engine.acceptInboundPairing(isOneTime = false)

        advanceTimeBy(1_000L)
        assertIs<PairingState.Success>(engine.state.value)
        assertEquals(1, sent.size)
        val frame = kotlinx.serialization.json.Json.parseToJsonElement(sent.single()).jsonObject
        assertEquals("pair-accepted", frame["type"]?.toString()?.trim('"'))
        val data = requireNotNull(frame["data"]).jsonObject
        assertEquals("minted-token-for-inbound-fp", data["token"]?.toString()?.trim('"'))
        assertEquals("pc-fp", data["fingerprint"]?.toString()?.trim('"'))
    }

    @Test
    fun `one-time accept stays session scoped without minting a token`() = runTest {
        val engine = PairingEngine(scope = backgroundScope)
        val sent = mutableListOf<String>()
        engine.outboundSender = { _, json ->
            sent.add(json)
            true
        }
        var grantInvoked = false
        engine.persistentGrant = { _ ->
            grantInvoked = true
            "should-not-mint"
        }

        engine.handleInboundPairingRequest("192.168.1.200", "inbound-fp")
        engine.acceptInboundPairing(isOneTime = true)

        advanceTimeBy(1_000L)
        assertIs<PairingState.Success>(engine.state.value)
        assertTrue(!grantInvoked, "one-time pairing must not persist anything")
        val frame = kotlinx.serialization.json.Json.parseToJsonElement(sent.single()).jsonObject
        assertEquals("pair-response", frame["type"]?.toString()?.trim('"'))
        assertTrue(frame["data"]?.jsonObject?.get("token") == null, "session-scope reply must carry no token")
    }
}
