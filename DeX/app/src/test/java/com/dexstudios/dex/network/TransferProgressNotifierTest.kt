package com.dexstudios.dex.network

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class TransferProgressNotifierTest {

    private val mockContext = mockk<Context>(relaxed = true)

    @Test
    fun createForegroundInfoConstructsValidForegroundInfo() {
        val workId = UUID.randomUUID()
        val info = TransferProgressNotifier.createForegroundInfo(
            context = mockContext,
            workId = workId,
            title = "Sending Files",
            text = "Uploading test.txt (50%)",
            progress = 50,
            notificationId = 1005
        )

        assertNotNull(info)
        assertEquals(1005, info.notificationId)
        assertNotNull(info.notification)
    }
}
