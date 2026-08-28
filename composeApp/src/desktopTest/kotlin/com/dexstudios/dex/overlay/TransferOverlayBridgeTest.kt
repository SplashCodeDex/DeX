package com.dexstudios.dex.overlay

import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.UploadState
import com.dexstudios.dex.core.network.services.FileExplorerService
import com.dexstudios.dex.core.network.services.PullProgressState
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransferOverlayBridgeTest {

    @Test
    fun testUploadStateSpawnsBannerAndUpdatesProgress() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val overlayManager = OverlayManager(scope = testScope, soundService = null)
        val uploadFlow = MutableStateFlow(UploadState())
        val pullFlow = MutableStateFlow(PullProgressState())

        val clientEngine = mockk<ClientEngine>(relaxed = true) {
            io.mockk.every { uploadState } returns uploadFlow
        }
        val fileExplorerService = mockk<FileExplorerService>(relaxed = true) {
            io.mockk.every { pullProgress } returns pullFlow
        }

        val bridge = TransferOverlayBridge(
            overlayManager = overlayManager,
            clientEngine = clientEngine,
            fileExplorerService = fileExplorerService,
            scope = testScope,
        )
        bridge.start()

        // 1. Upload starts
        uploadFlow.value = UploadState(
            isUploading = true,
            fileName = "vacation_2026.mp4",
            progress = 0.25f,
            speedBps = 32_000_000L,
        )
        testScope.advanceTimeBy(100L)

        assertEquals(1, overlayManager.actionAlerts.value.size)
        val banner = overlayManager.actionAlerts.value.first() as BannerNotification
        assertEquals("vacation_2026.mp4", banner.title)
        assertEquals(0.25f, banner.progress)

        // 2. Progress updates
        uploadFlow.value = UploadState(
            isUploading = true,
            fileName = "vacation_2026.mp4",
            progress = 0.85f,
            speedBps = 45_000_000L,
        )
        testScope.advanceTimeBy(100L)

        val updatedBanner = overlayManager.actionAlerts.value.first() as BannerNotification
        assertEquals(0.85f, updatedBanner.progress)

        // 3. Upload finishes
        uploadFlow.value = UploadState(
            isUploading = false,
            isSuccess = true,
            fileName = "vacation_2026.mp4",
        )
        testScope.advanceTimeBy(100L)

        // Active banner should be dismissed
        assertEquals(0, overlayManager.actionAlerts.value.size)
        // Success toast should appear
        assertEquals(1, overlayManager.statusToasts.value.size)
        assertTrue(overlayManager.statusToasts.value.first().message.contains("vacation_2026.mp4"))

        bridge.stop()
    }

    @Test
    fun testPullSuccessSpawnsAirDropCompletionCardWithOpenAndFolder() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val overlayManager = OverlayManager(scope = testScope, soundService = null)
        val uploadFlow = MutableStateFlow(UploadState())
        val pullFlow = MutableStateFlow(PullProgressState())

        val clientEngine = mockk<ClientEngine>(relaxed = true) {
            io.mockk.every { uploadState } returns uploadFlow
        }
        val fileExplorerService = mockk<FileExplorerService>(relaxed = true) {
            io.mockk.every { pullProgress } returns pullFlow
        }

        val bridge = TransferOverlayBridge(
            overlayManager = overlayManager,
            clientEngine = clientEngine,
            fileExplorerService = fileExplorerService,
            scope = testScope,
        )
        bridge.start()

        // 1. Pull active
        pullFlow.value = PullProgressState(
            isPulling = true,
            activeFileName = "report.pdf",
            totalFiles = 1,
            completedFiles = 0,
            progress = 0.5f,
        )
        testScope.advanceUntilIdle()

        assertEquals(1, overlayManager.actionAlerts.value.size)
        val banner = overlayManager.actionAlerts.value.first() as BannerNotification
        assertEquals("report.pdf", banner.title)

        // 2. Pull done
        pullFlow.value = PullProgressState(
            isPulling = false,
            isDone = true,
            activeFileName = "report.pdf",
            totalFiles = 1,
            completedFiles = 1,
            progress = 1.0f,
        )
        testScope.advanceUntilIdle()

        // Banner dismissed and AirDrop Alert Card shown
        assertEquals(1, overlayManager.actionAlerts.value.size)
        val alert = overlayManager.actionAlerts.value.first() as AlertNotification
        assertEquals("Received 'report.pdf'", alert.title)
        assertEquals("Open", alert.positiveButtonText)
        assertEquals("Folder", alert.negativeButtonText)

        bridge.stop()
    }

    @Test
    fun testBatchPullSuccessSpawnsBatchCompletionCard() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val overlayManager = OverlayManager(scope = testScope, soundService = null)
        val uploadFlow = MutableStateFlow(UploadState())
        val pullFlow = MutableStateFlow(PullProgressState())

        val clientEngine = mockk<ClientEngine>(relaxed = true) {
            io.mockk.every { uploadState } returns uploadFlow
        }
        val fileExplorerService = mockk<FileExplorerService>(relaxed = true) {
            io.mockk.every { pullProgress } returns pullFlow
        }

        val bridge = TransferOverlayBridge(
            overlayManager = overlayManager,
            clientEngine = clientEngine,
            fileExplorerService = fileExplorerService,
            scope = testScope,
        )
        bridge.start()

        // Batch Pull done (12 files)
        pullFlow.value = PullProgressState(
            isPulling = false,
            isDone = true,
            activeFileName = "photo_12.jpg",
            totalFiles = 12,
            completedFiles = 12,
            progress = 1.0f,
        )
        testScope.advanceUntilIdle()

        assertEquals(1, overlayManager.actionAlerts.value.size)
        val alert = overlayManager.actionAlerts.value.first() as AlertNotification
        assertEquals("Received 12 Files", alert.title)
        assertEquals("Open Folder", alert.positiveButtonText)
        assertEquals("Dismiss", alert.negativeButtonText)

        bridge.stop()
    }
}
