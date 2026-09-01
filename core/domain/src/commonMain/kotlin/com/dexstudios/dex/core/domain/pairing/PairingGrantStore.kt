package com.dexstudios.dex.core.domain.pairing

/**
 * Persistence port for pairing grants (plan 026).
 *
 * The domain engine needs exactly one thing from infrastructure: when a PIN pairing is
 * proven, a per-device credential must be minted and stored on BOTH sides. HOW that
 * happens (DataStore, keystore, remote registry) is an adapter concern — desktop wires
 * [com.dexstudios.dex.core.network.DeviceManagerPairingGrantStore]; future platforms
 * supply their own.
 */
interface PairingGrantStore {
    /**
     * Persists the pairing for [fingerprint] and returns the freshly minted per-device
     * token to hand back to the peer so it can persist its side too.
     */
    suspend fun grant(fingerprint: String): String
}
