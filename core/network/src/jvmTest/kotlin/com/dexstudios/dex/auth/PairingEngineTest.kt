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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
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
                download = true
            ),
            viaWan = false,
            viaRoster = false
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
}
