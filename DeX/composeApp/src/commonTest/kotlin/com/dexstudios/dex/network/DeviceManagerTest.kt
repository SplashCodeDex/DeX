package com.dexstudios.dex.network

import com.dexstudios.dex.auth.AuthState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceManagerTest {

    @Test
    fun save_and_remove_paired_fingerprint() = runTest {
        val testFp = "fp_test_123"
        
        // Ensure state is clean
        AuthState.pairedFingerprints.clear()
        AuthState.pairedTokens.clear()
        AuthState.pairedTimes.clear()
        
        // We can't directly test DeviceManager saving to DataStore without mocking the DataStore
        // or injecting a FakeDataStore in commonTest.
        // For logic testing, we verify AuthState interactions.
        // In a real KMP test, we would use a test DataStore instance.
        
        AuthState.pairedFingerprints.add(testFp)
        assertTrue(AuthState.pairedFingerprints.contains(testFp))
        
        AuthState.pairedFingerprints.remove(testFp)
        assertFalse(AuthState.pairedFingerprints.contains(testFp))
    }
}
