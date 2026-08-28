package com.dexstudios.dex.overlay

import com.dexstudios.dex.core.designsystem.components.overlay.BannerMorphState
import com.dexstudios.dex.core.designsystem.components.overlay.ToastVariant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayManagerTest {

    @Test
    fun testBannerEnqueuingAndDismissal() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val manager = OverlayManager(scope = testScope, soundService = null)

        val id = manager.showBanner(
            title = "Lyft Arriving",
            subtitle = "Alex 5.0 - Chevy Malibu",
            initialMorphState = BannerMorphState.Compact,
            autoDismissTimeoutMs = 5_000L,
            playSound = false,
        )

        assertEquals(1, manager.bottomCenterNotifications.value.size)
        assertEquals(id, manager.bottomCenterNotifications.value.first().id)

        // Advance past 5s timeout
        testScope.advanceTimeBy(5_001L)

        // Should be automatically dismissed
        assertEquals(0, manager.bottomCenterNotifications.value.size)
    }

    @Test
    fun testToastStreamSegregation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val manager = OverlayManager(scope = testScope, soundService = null)

        val toastId = manager.showToast(
            message = "Clipboard synced",
            variant = ToastVariant.Success,
            autoDismissTimeoutMs = 3_000L,
            playSound = false,
        )

        val bannerId = manager.showBanner(
            title = "Incoming Transfer",
            playSound = false,
        )

        // Corner stream only contains toasts
        assertEquals(1, manager.statusToasts.value.size)
        assertEquals(toastId, manager.statusToasts.value.first().id)

        // Action alerts stream only contains banners
        assertEquals(1, manager.actionAlerts.value.size)
        assertEquals(bannerId, manager.actionAlerts.value.first().id)

        // After 3s, toast auto-dismisses but banner remains
        testScope.advanceTimeBy(3_001L)
        assertEquals(0, manager.statusToasts.value.size)
        assertEquals(1, manager.actionAlerts.value.size)
    }

    @Test
    fun testHoverPausesAutoDismissTimers() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        var virtualTime = 1_000_000L
        val manager = OverlayManager(scope = testScope, soundService = null, timeProvider = { virtualTime })

        manager.showBanner(
            title = "Download Complete",
            autoDismissTimeoutMs = 5_000L,
            playSound = false,
        )

        // Advance 3 seconds
        virtualTime += 3_000L
        testScope.advanceTimeBy(3_000L)
        assertEquals(1, manager.bottomCenterNotifications.value.size)

        // User hovers the stack -> timers pause
        manager.setBottomCenterHovered(true)

        // Advance another 10 seconds while hovered
        virtualTime += 10_000L
        testScope.advanceTimeBy(10_000L)

        // Must NOT dismiss while hovered
        assertEquals(1, manager.bottomCenterNotifications.value.size)

        // User unhovers -> timer resumes with 2.5s grace period
        manager.setBottomCenterHovered(false)

        virtualTime += 1_000L
        testScope.advanceTimeBy(1_000L)
        assertEquals(1, manager.bottomCenterNotifications.value.size)

        virtualTime += 1_501L
        testScope.advanceTimeBy(1_501L)
        // Now dismissed
        assertEquals(0, manager.bottomCenterNotifications.value.size)
    }

    @Test
    fun testModalAlertsArePersistentUntilAction() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val manager = OverlayManager(scope = testScope, soundService = null)

        var accepted = false
        manager.showAlert(
            title = "AirDrop Request",
            message = "Danny would like to share 23 photos",
            onPositiveAction = { accepted = true },
            playSound = false,
        )

        assertEquals(1, manager.bottomCenterNotifications.value.size)

        // Advance 1 hour -> should still be present
        testScope.advanceTimeBy(3_600_000L)
        assertEquals(1, manager.bottomCenterNotifications.value.size)
        assertFalse(accepted)

        // Trigger action
        val alert = manager.bottomCenterNotifications.value.first() as AlertNotification
        alert.onPositiveAction()

        assertTrue(accepted)
        // Automatically closes after action
        assertEquals(0, manager.bottomCenterNotifications.value.size)
    }

    @Test
    fun testHistoryRetentionCapAt20() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val manager = OverlayManager(scope = testScope, soundService = null)

        for (i in 1..25) {
            manager.showToast(
                message = "Toast #$i",
                playSound = false,
            )
        }

        // History buffer must cap at 20 entries
        assertEquals(20, manager.history.value.size)
        // Most recent toast (#25) is at index 0
        assertEquals("Toast #25", manager.history.value.first().message)
    }

    @Test
    fun testDeduplicationWithin3SecondsDiscardsDuplicates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        var virtualTime = 1_000_000L
        val manager = OverlayManager(scope = testScope, soundService = null, timeProvider = { virtualTime })

        // Dispatch 5 identical toasts rapidly
        for (i in 1..5) {
            manager.showToast(
                message = "Clipboard Synced",
                variant = ToastVariant.Success,
                playSound = false,
            )
            virtualTime += 100L // 100ms apart
        }

        // Only 1 should be visible on screen (4 were discarded)
        assertEquals(1, manager.statusToasts.value.size)

        // Advance past 3-second deduplication window
        virtualTime += 3_500L
        testScope.advanceTimeBy(3_500L)

        // Dispatch again -> accepted now
        manager.showToast(
            message = "Clipboard Synced",
            variant = ToastVariant.Success,
            playSound = false,
        )

        assertEquals(1, manager.statusToasts.value.size)
    }

    @Test
    fun testDoNotDisturbSilentlyLogsToastsToHistory() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val manager = OverlayManager(scope = testScope, soundService = null)

        manager.setDoNotDisturb(true)

        manager.showToast(
            message = "Background Sync Complete",
            variant = ToastVariant.Info,
            playSound = false,
        )

        // Should NOT appear in active status toasts on screen
        assertEquals(0, manager.statusToasts.value.size)

        // But MUST be recorded in notification history
        assertEquals(1, manager.history.value.size)
        assertEquals("Background Sync Complete", manager.history.value.first().message)
    }
}
