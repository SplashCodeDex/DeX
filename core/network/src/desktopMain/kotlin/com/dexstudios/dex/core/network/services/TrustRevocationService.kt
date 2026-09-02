package com.dexstudios.dex.core.network.services

import com.dexstudios.dex.core.domain.pairing.PairingEngine
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.network.DeviceManager
import com.dexstudios.dex.core.network.server.WebSocketConnectionManager
import com.dexstudios.dex.core.protocol.FieldNames
import com.dexstudios.dex.core.protocol.MessageTypes
import com.dexstudios.dex.core.protocol.ProtocolEnvelope
import kotlinx.serialization.json.put

/**
 * Single owner of local trust revocation (plan 026). "Forget device" (device list) and
 * "Reset Identity & Trust" (settings) previously hand-rolled the same sequence — send the
 * `unpair` frame so the peer downgrades too, downgrade any live session, drop the
 * persisted fingerprint/token. One implementation, two callers, identical semantics.
 */
object TrustRevocationService {

    /**
     * Revokes one device: notifies the peer (`unpair` carries OUR fingerprint — peers
     * only ever revoke themselves), downgrades the live session, removes persisted trust.
     * Failures of the peer notification never block local revocation.
     */
    suspend fun revokeDevice(fingerprint: String, deviceConfig: DeviceConfig) {
        runCatching {
            WebSocketConnectionManager.sendRequest(
                fingerprint,
                ProtocolEnvelope.envelopeOf(MessageTypes.UNPAIR) {
                    put(FieldNames.FINGERPRINT, deviceConfig.fingerprint)
                },
            )
        }
        WebSocketConnectionManager.markUntrusted(fingerprint)
        DeviceManager.removePairedFingerprint(fingerprint)
    }

    /**
     * Reset-all path: revokes every persisted pairing, then rotates the identity hash so
     * a previously known auto-trust credential dies with the reset. The active pairing
     * state machine (if mid-offer) is also returned to Idle so no stale panel survives.
     */
    suspend fun revokeAll(deviceConfig: DeviceConfig, pairingEngine: PairingEngine? = null) {
        val paired = com.dexstudios.dex.auth.AuthState.pairedFingerprints.value.toList()
        paired.forEach { fp -> revokeDevice(fp, deviceConfig) }
        pairingEngine?.reset()
        // The OLD fingerprint's roster card must vanish from every peer's synced roster
        // (a tombstone, not a purge — a purge would let offline peers resurrect it).
        com.dexstudios.dex.core.network.SyncBridge.tombstoneDevice(deviceConfig.fingerprint)
        deviceConfig.resetIdentity()
    }
}
