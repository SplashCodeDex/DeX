package com.dexstudios.dex.window.components

import com.dexstudios.dex.window.kinematics.DockCardAnimations
import com.dexstudios.dex.window.kinematics.DockCardPhysics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Milestone3ComponentsTest {



    @Test
    fun testPinPairingUiStateProperties() {
        val pinView = PinPairingUiState.PinView(
            pinCode = "123456",
            enteredDigitCount = 3,
            remainingSeconds = 45,
            isError = false
        )
        assertEquals("123456", pinView.pinCode)
        assertEquals(3, pinView.enteredDigitCount)
        assertEquals(45, pinView.remainingSeconds)
        assertFalse(pinView.isError)

        val qrView = PinPairingUiState.QrView(
            qrPayload = "dex://192.168.1.5:53317",
            remainingSeconds = 60
        )
        assertEquals("dex://192.168.1.5:53317", qrView.qrPayload)
        assertEquals(60, qrView.remainingSeconds)

        val success = PinPairingUiState.Success
        assertNotNull(success)
    }

    @Test
    fun testExitConfirmationStageEnum() {
        val idle = ExitConfirmationStage.Idle
        val confirming = ExitConfirmationStage.Confirming

        assertEquals(0, idle.ordinal)
        assertEquals(1, confirming.ordinal)
    }

    @Test
    fun testDangerousFileExtensionClassification() {
        val dangerousExtensions = setOf(
            ".exe", ".bat", ".cmd", ".ps1", ".vbs", ".vbe",
            ".msi", ".scr", ".com", ".pif", ".wsf"
        )

        fun isDangerous(filename: String): Boolean {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return dangerousExtensions.contains(".$ext")
        }

        assertTrue(isDangerous("installer.exe"))
        assertTrue(isDangerous("script.bat"))
        assertTrue(isDangerous("deploy.ps1"))
        assertTrue(isDangerous("command.cmd"))
        assertTrue(isDangerous("setup.msi"))

        assertFalse(isDangerous("document.pdf"))
        assertFalse(isDangerous("photo.jpg"))
        assertFalse(isDangerous("archive.zip"))
        assertFalse(isDangerous("video.mp4"))
        assertFalse(isDangerous("notes.txt"))
    }

    @Test
    fun testKinematicConstantsParity() {
        assertEquals(0.65f, DockCardPhysics.SPRING_DAMPING_RATIO)
        assertEquals(300f, DockCardPhysics.SPRING_STIFFNESS)

        assertEquals(320f, DockCardAnimations.CARD_WIDTH_CONTRACTED.value)
        assertEquals(1054f, DockCardAnimations.CARD_WIDTH_EXPANDED.value)
        assertEquals(675f, DockCardAnimations.SETTINGS_WIDTH_EXPANDED.value)
        assertEquals(400f, DockCardAnimations.PAIRING_WIDTH_EXPANDED.value)
        assertEquals(430f, DockCardAnimations.CARD_HEIGHT_CONTRACTED.value)
        assertEquals(625f, DockCardAnimations.CARD_HEIGHT_EXPANDED.value)
    }
}
