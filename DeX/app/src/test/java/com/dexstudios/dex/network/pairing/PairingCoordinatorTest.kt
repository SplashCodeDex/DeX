package com.dexstudios.dex.network.pairing

import android.content.Context
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.NotificationHelper
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDeviceConfig = mockk<DeviceConfig>(relaxed = true)
    private val mockNotificationHelper = mockk<NotificationHelper>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendPairResponseConstructsValidJsonEnvelope() = runTest(testDispatcher) {
        var sentMessage: String? = null
        val coordinator = PairingCoordinator(
            deviceConfig = mockDeviceConfig,
            context = mockContext,
            notificationHelper = mockNotificationHelper,
            sendMessage = { sentMessage = it }
        )

        coordinator.sendPairResponse(accepted = true, enteredPin = "12345")

        assertNotNull(sentMessage)
        assertTrue(sentMessage!!.contains("\"type\":\"pair-response\""))
        assertTrue(sentMessage!!.contains("\"accepted\":true"))
        assertTrue(sentMessage!!.contains("\"pin\":\"12345\""))
    }

    @Test
    fun sendPinDigitEnteredEmitsCorrectTelemetryEnvelope() = runTest(testDispatcher) {
        var sentMessage: String? = null
        val coordinator = PairingCoordinator(
            deviceConfig = mockDeviceConfig,
            context = mockContext,
            notificationHelper = mockNotificationHelper,
            sendMessage = { sentMessage = it }
        )

        coordinator.sendPinDigitEntered(4)

        assertNotNull(sentMessage)
        assertTrue(sentMessage!!.contains("\"type\":\"pin-digit-entered\""))
        assertTrue(sentMessage!!.contains("\"digitCount\":4"))
    }
}
