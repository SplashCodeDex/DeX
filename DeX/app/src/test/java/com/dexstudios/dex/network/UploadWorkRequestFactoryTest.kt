package com.dexstudios.dex.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UploadWorkRequestFactoryTest {

    @Test
    fun createWithRawFieldsBuildsCorrectInputData() {
        val request = UploadWorkRequestFactory.create(
            ip = "192.168.1.50",
            port = 48424,
            urisJson = "[\"content://media/1\"]",
            targetFingerprint = "fp_test",
            targetAlias = "Test PC",
            targetIdentityHash = "hash_123",
            targetGoogleSub = "sub_456"
        )

        val input = request.workSpec.input
        assertEquals("192.168.1.50", input.getString(TransferWorkKeys.IP))
        assertEquals(48424, input.getInt(TransferWorkKeys.PORT, 0))
        assertEquals("[\"content://media/1\"]", input.getString(TransferWorkKeys.URIS))
        assertEquals("fp_test", input.getString(TransferWorkKeys.TARGET_FINGERPRINT))
        assertEquals("Test PC", input.getString(TransferWorkKeys.TARGET_ALIAS))
        assertEquals("hash_123", input.getString(TransferWorkKeys.TARGET_IDENTITY_HASH))
        assertEquals("sub_456", input.getString(TransferWorkKeys.TARGET_GOOGLE_SUB))
        assertNotNull(request.id)
    }

    @Test
    fun createWithDeviceBuildsCorrectInputData() {
        val device = DiscoveredDevice(
            ip = "10.0.0.5",
            info = RegisterDto(
                alias = "Laptop",
                version = "2.0",
                deviceModel = "Windows PC",
                deviceType = "desktop",
                fingerprint = "fp_laptop",
                port = 8443,
                protocol = "https",
                download = false,
                identityHash = "id_hash"
            )
        )

        val request = UploadWorkRequestFactory.create(
            device = device,
            urisJson = "[\"content://media/2\"]",
            expedited = false
        )

        val input = request.workSpec.input
        assertEquals("10.0.0.5", input.getString(TransferWorkKeys.IP))
        assertEquals(8443, input.getInt(TransferWorkKeys.PORT, 0))
        assertEquals("[\"content://media/2\"]", input.getString(TransferWorkKeys.URIS))
        assertEquals("fp_laptop", input.getString(TransferWorkKeys.TARGET_FINGERPRINT))
        assertEquals("Laptop", input.getString(TransferWorkKeys.TARGET_ALIAS))
    }
}
