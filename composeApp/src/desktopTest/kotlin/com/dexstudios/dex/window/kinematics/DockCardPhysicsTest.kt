package com.dexstudios.dex.window.kinematics

import com.dexstudios.dex.platform.WorkAreaBounds
import java.awt.Insets
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockCardPhysicsTest {

    private val testWorkArea = WorkAreaBounds(
        left = 0,
        top = 0,
        right = 1920,
        bottom = 1040,
        width = 1920,
        height = 1040,
        insets = Insets(0, 0, 40, 0),
        screenBounds = Rectangle(0, 0, 1920, 1080)
    )

    @Test
    fun testPopInEaseBoundaryConditions() {
        val f0 = DockCardPhysics.PopInEase.transform(0f)
        val f1 = DockCardPhysics.PopInEase.transform(1f)
        assertEquals(0f, f0, 0.001f, "PopInEase at 0 must be 0")
        assertEquals(1f, f1, 0.001f, "PopInEase at 1 must be 1")
    }

    @Test
    fun testContractEaseBoundaryConditions() {
        val f0 = DockCardPhysics.ContractEase.transform(0f)
        val f1 = DockCardPhysics.ContractEase.transform(1f)
        assertEquals(0f, f0, 0.001f, "ContractEase at 0 must be 0")
        assertEquals(1f, f1, 0.001f, "ContractEase at 1 must be 1")
    }

    @Test
    fun testHoverEaseTransforms() {
        val f0 = DockCardPhysics.HoverEase.transform(0f)
        val f1 = DockCardPhysics.HoverEase.transform(1f)
        assertEquals(0f, f0, 0.001f, "HoverEase at 0 must be 0")
        assertEquals(1f, f1, 0.001f, "HoverEase at 1 must be 1")
        // Micro-lift overshoot check (CubicBezier 0.34, 1.56, 0.64, 1.0 has values > 1)
        val mid = DockCardPhysics.HoverEase.transform(0.5f)
        assertTrue(mid > 0.5f, "HoverEase should exhibit quick lift")
    }

    @Test
    fun testCalculateExpansionNudgeOnNormalScreen() {
        // Positioned at normal resting dock on 1920x1080:
        // winX = 1920 - 1420 + 12 = 512, winY = 1040 - 760 + 12 = 292
        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = 512,
            currentWindowY = 292,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = testWorkArea,
            canvasWidth = 1420,
            margin = 25
        )

        // Expanded card left: targetX + 1420 - 25 - (300 + 754) = targetX + 1395 - 1054 = targetX + 341
        // For winX = 512: expLeft = 512 + 341 = 853 >= 0 (no clipping on left, targetX remains 512)
        assertEquals(512, targetX)
        // With 760dp canvas, expanding upwards doesn't change bottom bounds.
        val expBottom = targetY + 760 - 25
        assertTrue(expBottom <= testWorkArea.bottom, "expBottom ($expBottom) must not exceed workArea.bottom (1040)")
    }

    @Test
    fun testCalculateExpansionNudgeNearLeftEdge() {
        // If window is dragged near left edge so expansion would go off-screen
        val (targetX, targetY) = DockCardPhysics.calculateExpansionNudge(
            currentWindowX = -200,
            currentWindowY = 292,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = 754,
            expandDeltaHeight = 195,
            workArea = testWorkArea,
            canvasWidth = 1420,
            margin = 25
        )

        // Post-expansion expLeft should be >= workArea.left (0)
        val expLeft = targetX + 1420 - 25 - 1054
        assertTrue(expLeft >= testWorkArea.left, "expLeft ($expLeft) must be >= workArea.left (0)")
    }

    @Test
    fun testEvaluateMagneticSnap() {
        // Card near right edge (within 15px of workArea.right: 1920 - 300 = 1620)
        val (snapLeft, snapTop) = DockCardPhysics.evaluateMagneticSnap(
            candidateContentLeft = 1610,
            candidateContentTop = 500,
            cardWidth = 300,
            cardHeight = 430,
            workArea = testWorkArea,
            snapThreshold = 20
        )
        assertEquals(1620, snapLeft, "Should snap to right boundary 1620")
        assertEquals(500, snapTop)
    }

    @Test
    fun testCalculateSnapAndClamp() {
        // Off-screen left drag attempt: contentLeft = -400
        val (clampedLeft, clampedTop) = DockCardPhysics.calculateSnapAndClamp(
            candidateContentLeft = -400,
            candidateContentTop = 200,
            cardWidth = 300,
            cardHeight = 430,
            workArea = testWorkArea,
            snapThreshold = 20,
            minGrab = 60
        )
        // Grab is max(300 * 0.2, 60) = 60. clampedLeft + cardWidth must be >= workArea.left + grab (60)
        // clampedLeft must be >= 60 - 300 = -240
        assertEquals(-240, clampedLeft, "Must enforce min grab area visible")
    }

    @Test
    fun testCalculateContractionOrigin() {
        // Window placed such that contracted card would be off screen to the right
        val currentWinX = 1000
        val safeWinX = DockCardPhysics.calculateContractionOrigin(
            currentWindowX = currentWinX,
            contractedCardWidth = 300,
            workArea = testWorkArea,
            canvasWidth = 1420,
            margin = 25,
            minGrab = 60
        )

        // Contracted left: safeWinX + 1420 - 25 - 300 = safeWinX + 1095
        // Must not exceed workArea.right - grab (1920 - 60 = 1860)
        val cContractedLeft = safeWinX + 1420 - 25 - 300
        assertTrue(cContractedLeft <= testWorkArea.right - 60, "Contracted card must remain grab-able")
    }
}
