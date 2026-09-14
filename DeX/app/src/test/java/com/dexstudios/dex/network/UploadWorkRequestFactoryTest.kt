package com.dexstudios.dex.network

import android.content.Context
import androidx.work.Data
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class UploadWorkRequestFactoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store() = UploadManifestStore(temporary.root)

    @Test
    fun createWithRawFieldsPreservesRoutingAndPersistsUris() {
        val payload = "[\"content://media/1\"]"
        val request = UploadWorkRequestFactory.create(
            ip = "192.168.1.50", port = 48424, urisJson = payload,
            targetFingerprint = "fp_test", targetAlias = "Test PC",
            targetIdentityHash = "hash_123", targetGoogleSub = "sub_456", manifestStore = store()
        )
        val input = request.workSpec.input
        assertEquals("192.168.1.50", input.getString(TransferWorkKeys.IP))
        assertEquals(48424, input.getInt(TransferWorkKeys.PORT, 0))
        assertNull(input.getString(TransferWorkKeys.URIS))
        assertTrue(input.getBoolean(TransferWorkKeys.URI_MANIFEST, false))
        assertEquals("fp_test", input.getString(TransferWorkKeys.TARGET_FINGERPRINT))
        assertEquals("Test PC", input.getString(TransferWorkKeys.TARGET_ALIAS))
        assertEquals("hash_123", input.getString(TransferWorkKeys.TARGET_IDENTITY_HASH))
        assertEquals("sub_456", input.getString(TransferWorkKeys.TARGET_GOOGLE_SUB))
        assertEquals(payload, store().read(request.id))
    }

    private fun device() = DiscoveredDevice(
        ip = "10.0.0.5",
        info = RegisterDto(alias = "Laptop", version = "2.0", deviceModel = "Windows PC",
            deviceType = "desktop", fingerprint = "fp_laptop", port = 8443,
            protocol = "https", download = false, identityHash = "id_hash")
    )

    @Test
    fun largeSelectionsWorkForUploadAndPunchAcrossStoreRecreation() {
        val payload = (1..10000).joinToString(prefix = "[", postfix = "]") { "\"content://media/$it\"" }
        assertTrue(payload.toByteArray().size > Data.MAX_DATA_BYTES)
        val request = UploadWorkRequestFactory.create(device(), payload, expedited = false, manifestStore = store())
        val punch = UploadWorkRequestFactory.createPunch(device(), payload, expedited = false, manifestStore = store())
        val context = mockk<Context> { every { filesDir } returns temporary.root }
        for (work in listOf(request, punch)) {
            assertNull(work.workSpec.input.getString(TransferWorkKeys.URIS))
            assertEquals(payload, UploadManifestStore.readInput(context, work.workSpec.input, work.id))
            assertEquals("fp_laptop", work.workSpec.input.getString(TransferWorkKeys.TARGET_FINGERPRINT))
        }
        assertEquals(8443, request.workSpec.input.getInt(TransferWorkKeys.PORT, 0))
        assertEquals("Laptop", request.workSpec.input.getString(TransferWorkKeys.TARGET_ALIAS))
    }

    @Test
    fun oldQueuedJobsStillReadInlineUris() {
        val input = Data.Builder().putString(TransferWorkKeys.URIS, "[]").build()
        assertEquals("[]", UploadManifestStore.readInput(mockk(), input, UUID.randomUUID()))
    }

    @Test
    fun cleanupRetainsUnfinishedAndUnknownWorkAndRemovesOnlyConfirmedTerminalWork() {
        val pending = UUID.randomUUID()
        val unknown = UUID.randomUUID()
        val finished = UUID.randomUUID()
        listOf(pending, unknown, finished).forEach { store().write(it, "[]") }
        store().pruneFinished { it == finished }
        assertEquals("[]", store().read(pending))
        assertEquals("[]", store().read(unknown))
        assertFalse(temporary.root.resolve("upload-manifests/$finished.json").exists())
    }
}
