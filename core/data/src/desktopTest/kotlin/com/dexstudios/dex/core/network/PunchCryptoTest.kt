package com.dexstudios.dex.core.network

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PunchCryptoTest {

    @Test
    fun testKeyAgreementProducesIdenticalSharedSecretAndDerivedKeys() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()

        val aliceSecret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val bobSecret = PunchCrypto.computeSharedSecret(bob.privateKeyBytes, alice.publicKeyBytes)

        assertEquals(32, aliceSecret.size)
        assertContentEquals(aliceSecret, bobSecret)

        val salt = PunchCrypto.generateSalt()
        assertEquals(PunchCrypto.SALT_LENGTH_BYTES, salt.size)

        val aliceKeys = PunchCrypto.derivePunchKeys(aliceSecret, salt)
        val bobKeys = PunchCrypto.derivePunchKeys(bobSecret, salt)

        assertEquals(PunchCrypto.KEY_LENGTH_BYTES, aliceKeys.sessionKey.size)
        assertEquals(PunchCrypto.KEY_LENGTH_BYTES, aliceKeys.authKey.size)
        assertContentEquals(aliceKeys.sessionKey, bobKeys.sessionKey)
        assertContentEquals(aliceKeys.authKey, bobKeys.authKey)
    }

    @Test
    fun testMutualAuthProofSucceedsWithMatchingIdentityHash() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val identitySecret = "verified-google-sub-or-hash-999"

        val sharedSecret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val salt = PunchCrypto.generateSalt()
        val keys = PunchCrypto.derivePunchKeys(sharedSecret, salt)

        // Alice = sender, Bob = receiver
        val aliceSenderProof = PunchCrypto.computeSenderAuthProof(
            authKey = keys.authKey,
            identitySecret = identitySecret,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )

        val bobReceiverProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = identitySecret,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )

        // Bob verifies Alice's sender proof
        val expectedAliceProof = PunchCrypto.computeSenderAuthProof(
            authKey = keys.authKey,
            identitySecret = identitySecret,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )
        assertTrue(PunchCrypto.verifyAuthProof(expectedAliceProof, aliceSenderProof))

        // Alice verifies Bob's receiver proof
        val expectedBobProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = identitySecret,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )
        assertTrue(PunchCrypto.verifyAuthProof(expectedBobProof, bobReceiverProof))
    }

    @Test
    fun testMutualAuthProofFailsWithMismatchedIdentityHash() {
        val alice = PunchCrypto.generateKeyPair()
        val mallory = PunchCrypto.generateKeyPair()

        val aliceIdentity = "legit-account-hash"
        val malloryIdentity = "rogue-account-hash"

        val sharedSecret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, mallory.publicKeyBytes)
        val salt = PunchCrypto.generateSalt()
        val keys = PunchCrypto.derivePunchKeys(sharedSecret, salt)

        // Mallory attempts to compute receiver proof with her own identity
        val forgedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = malloryIdentity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = mallory.publicKeyBytes,
        )

        // Alice computes expected proof with her identity
        val expectedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = aliceIdentity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = mallory.publicKeyBytes,
        )

        assertFalse(PunchCrypto.verifyAuthProof(expectedProof, forgedProof))
    }

    @Test
    fun testGoogleSubHardeningDefeatsKnownEmailIdentityHashForgery() {
        val alice = PunchCrypto.generateKeyPair()
        val attacker = PunchCrypto.generateKeyPair()

        // Alice's legitimate secret is her private googleSub
        val aliceGoogleSub = "109823482348239482938"
        // Attacker only knows Alice's public email address and pre-calculates identityHash
        val attackerCalculatedIdentityHash = "sha256-of-alice-email"

        val sharedSecret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, attacker.publicKeyBytes)
        val salt = PunchCrypto.generateSalt()
        val keys = PunchCrypto.derivePunchKeys(sharedSecret, salt)

        // Attacker attempts to forge receiver proof using only the identityHash
        val attackerForgedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = attackerCalculatedIdentityHash,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = attacker.publicKeyBytes,
        )

        // Alice verifies expecting her hardened googleSub identitySecret
        val expectedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = aliceGoogleSub,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = attacker.publicKeyBytes,
        )

        assertFalse(PunchCrypto.verifyAuthProof(expectedProof, attackerForgedProof), "Attacker knowing only email hash must be rejected")
    }

    @Test
    fun testFramedSealingAndOpeningWithSequenceBinding() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val frame0Plain = "frame-0-manifest-json".toByteArray()
        val frame1Plain = "frame-1-resume-info-json".toByteArray()
        val frame2Plain = ByteArray(65536) { (it % 256).toByte() }

        val sealed0 = PunchCrypto.sealFrame(keys.sessionKey, 0L, frame0Plain)
        val sealed1 = PunchCrypto.sealFrame(keys.sessionKey, 1L, frame1Plain)
        val sealed2 = PunchCrypto.sealFrame(keys.sessionKey, 2L, frame2Plain)

        assertContentEquals(frame0Plain, PunchCrypto.openFrame(keys.sessionKey, 0L, sealed0))
        assertContentEquals(frame1Plain, PunchCrypto.openFrame(keys.sessionKey, 1L, sealed1))
        assertContentEquals(frame2Plain, PunchCrypto.openFrame(keys.sessionKey, 2L, sealed2))

        // Replay of frame 0 at sequence 1 must fail closed
        assertFailsWith<RelayCryptoException> {
            PunchCrypto.openFrame(keys.sessionKey, 1L, sealed0)
        }

        // Reordered frame 2 at sequence 0 must fail closed
        assertFailsWith<RelayCryptoException> {
            PunchCrypto.openFrame(keys.sessionKey, 0L, sealed2)
        }

        // Tampered byte must fail closed
        val tampered = sealed0.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()
        assertFailsWith<RelayCryptoException> {
            PunchCrypto.openFrame(keys.sessionKey, 0L, tampered)
        }
    }

    @Test
    fun testEphemeralSessionsYieldIndependentKeys() {
        val k1 = PunchCrypto.generateKeyPair()
        val k2 = PunchCrypto.generateKeyPair()
        val s1 = PunchCrypto.computeSharedSecret(k1.privateKeyBytes, k2.publicKeyBytes)

        val k3 = PunchCrypto.generateKeyPair()
        val k4 = PunchCrypto.generateKeyPair()
        val s2 = PunchCrypto.computeSharedSecret(k3.privateKeyBytes, k4.publicKeyBytes)

        val salt = PunchCrypto.generateSalt()
        val keys1 = PunchCrypto.derivePunchKeys(s1, salt)
        val keys2 = PunchCrypto.derivePunchKeys(s2, salt)

        assertFalse(keys1.sessionKey.contentEquals(keys2.sessionKey))
        assertFalse(keys1.authKey.contentEquals(keys2.authKey))
    }
}
