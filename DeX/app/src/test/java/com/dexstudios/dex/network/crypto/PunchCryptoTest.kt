package com.dexstudios.dex.network.crypto

import com.dexstudios.dex.core.network.RelayCryptoException
import com.dexstudios.dex.network.PunchCryptoChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Android test suite verifying PunchCrypto behavior and PunchCryptoChannel framing
 * against the multiplatform contracts established in Plan 046.
 */
class PunchCryptoTest {

    @Test
    fun bothPeersDeriveIdenticalSessionKey() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()

        val aliceSecret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val bobSecret = PunchCrypto.computeSharedSecret(bob.privateKeyBytes, alice.publicKeyBytes)

        assertEquals(32, aliceSecret.size)
        assertArrayEquals(aliceSecret, bobSecret)

        val salt = PunchCrypto.generateSalt()
        val aliceKeys = PunchCrypto.derivePunchKeys(aliceSecret, salt)
        val bobKeys = PunchCrypto.derivePunchKeys(bobSecret, salt)

        assertEquals(PunchCrypto.KEY_LENGTH_BYTES, aliceKeys.sessionKey.size)
        assertEquals(PunchCrypto.KEY_LENGTH_BYTES, aliceKeys.authKey.size)
        assertArrayEquals(aliceKeys.sessionKey, bobKeys.sessionKey)
        assertArrayEquals(aliceKeys.authKey, bobKeys.authKey)
    }

    @Test
    fun mutualAuthProofSucceedsWithMatchingIdentity() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val identity = "user-identity-hash-42"

        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val senderProof = PunchCrypto.computeSenderAuthProof(
            authKey = keys.authKey,
            identitySecret = identity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )

        val receiverProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = identity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )

        val expectedSenderProof = PunchCrypto.computeSenderAuthProof(
            authKey = keys.authKey,
            identitySecret = identity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )
        val expectedReceiverProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = identity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = bob.publicKeyBytes,
        )

        assertTrue(PunchCrypto.verifyAuthProof(expectedSenderProof, senderProof))
        assertTrue(PunchCrypto.verifyAuthProof(expectedReceiverProof, receiverProof))
    }

    @Test
    fun mutualAuthProofRejectsStrangerIdentity() {
        val alice = PunchCrypto.generateKeyPair()
        val mallory = PunchCrypto.generateKeyPair()

        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, mallory.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val legitIdentity = "alice-identity-hash"
        val rogueIdentity = "mallory-identity-hash"

        val forgedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = rogueIdentity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = mallory.publicKeyBytes,
        )

        val expectedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = legitIdentity,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = mallory.publicKeyBytes,
        )

        assertFalse(PunchCrypto.verifyAuthProof(expectedProof, forgedProof))
    }

    @Test
    fun googleSubHardeningRejectsEmailHashAttacker() {
        val alice = PunchCrypto.generateKeyPair()
        val attacker = PunchCrypto.generateKeyPair()

        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, attacker.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val aliceGoogleSub = "10934892834928349234"
        val attackerPublicEmailHash = "sha256-alice-email"

        val forgedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = attackerPublicEmailHash,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = attacker.publicKeyBytes,
        )

        val expectedProof = PunchCrypto.computeReceiverAuthProof(
            authKey = keys.authKey,
            identitySecret = aliceGoogleSub,
            senderPubKey = alice.publicKeyBytes,
            receiverPubKey = attacker.publicKeyBytes,
        )

        assertFalse(PunchCrypto.verifyAuthProof(expectedProof, forgedProof))
    }

    @Test
    fun frameSequencedSealingAndOpening() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val plain0 = "frame-zero-payload".toByteArray(Charsets.UTF_8)
        val plain1 = "frame-one-payload".toByteArray(Charsets.UTF_8)

        val sealed0 = PunchCrypto.sealFrame(keys.sessionKey, 0L, plain0)
        val sealed1 = PunchCrypto.sealFrame(keys.sessionKey, 1L, plain1)

        assertArrayEquals(plain0, PunchCrypto.openFrame(keys.sessionKey, 0L, sealed0))
        assertArrayEquals(plain1, PunchCrypto.openFrame(keys.sessionKey, 1L, sealed1))
    }

    @Test
    fun replayedFrameFailsClosed() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val sealed0 = PunchCrypto.sealFrame(keys.sessionKey, 0L, "payload".toByteArray())

        assertThrows(RelayCryptoException::class.java) {
            PunchCrypto.openFrame(keys.sessionKey, 1L, sealed0)
        }
    }

    @Test
    fun reorderedFrameFailsClosed() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val sealed1 = PunchCrypto.sealFrame(keys.sessionKey, 1L, "payload".toByteArray())

        assertThrows(RelayCryptoException::class.java) {
            PunchCrypto.openFrame(keys.sessionKey, 0L, sealed1)
        }
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val alice = PunchCrypto.generateKeyPair()
        val bob = PunchCrypto.generateKeyPair()
        val secret = PunchCrypto.computeSharedSecret(alice.privateKeyBytes, bob.publicKeyBytes)
        val keys = PunchCrypto.derivePunchKeys(secret, PunchCrypto.generateSalt())

        val sealed = PunchCrypto.sealFrame(keys.sessionKey, 0L, "sensitive-data".toByteArray())
        val tampered = sealed.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x40).toByte()

        assertThrows(RelayCryptoException::class.java) {
            PunchCrypto.openFrame(keys.sessionKey, 0L, tampered)
        }
    }

    @Test
    fun punchCryptoChannelFramedStreamRoundTrip() = runBlocking {
        val key = ByteArray(PunchCrypto.KEY_LENGTH_BYTES) { 7.toByte() }
        val wirePipe = ByteArrayOutputStream()

        val writerChannel = PunchCryptoChannel(
            input = ByteArrayInputStream(ByteArray(0)),
            output = wirePipe,
            sessionKey = key,
        )

        val frame1 = "manifest-json-data".toByteArray(Charsets.UTF_8)
        val frame2 = ByteArray(64 * 1024) { (it % 127).toByte() }
        val frame3 = "done-marker".toByteArray(Charsets.UTF_8)

        writerChannel.writeFrame(frame1)
        writerChannel.writeFrame(frame2)
        writerChannel.writeFrame(frame3)

        val readerChannel = PunchCryptoChannel(
            input = ByteArrayInputStream(wirePipe.toByteArray()),
            output = ByteArrayOutputStream(),
            sessionKey = key,
        )

        val read1 = readerChannel.readFrame()
        val read2 = readerChannel.readFrame()
        val read3 = readerChannel.readFrame()

        assertArrayEquals(frame1, read1)
        assertArrayEquals(frame2, read2)
        assertArrayEquals(frame3, read3)
    }
}
