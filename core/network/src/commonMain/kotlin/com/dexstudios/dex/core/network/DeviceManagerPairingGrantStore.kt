package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.domain.pairing.PairingGrantStore

/**
 * Desktop adapter for the domain pairing-grant port (plan 026): mints a per-device
 * UUID token and persists BOTH sides of the pairing through [DeviceManager] — the exact
 * behavior the PairingEngine previously performed inline before the extraction.
 */
class DeviceManagerPairingGrantStore : PairingGrantStore {
    override suspend fun grant(fingerprint: String): String {
        val pairToken = HashUtils.generateUUID()
        DeviceManager.savePairedFingerprint(fingerprint)
        DeviceManager.savePairedToken(fingerprint, pairToken)
        return pairToken
    }
}
