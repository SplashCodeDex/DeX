package com.dexstudios.dex.network

import com.dexstudios.dex.network.crypto.PunchCrypto
import com.dexstudios.dex.network.crypto.RelayCryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

/**
 * End-to-end encrypted framed channel for direct NAT punch data transfers (Plan 046).
 *
 * Wire Framing:
 *  [4-byte big-endian frameLength][12-byte nonce][ciphertext + 16-byte GCM tag]
 *
 * Each frame binds a monotonically increasing 64-bit sequence number in the GCM AAD,
 * preventing frame replay, frame reordering, dropping, or tampering.
 */
class PunchCryptoChannel(
    private val input: InputStream,
    private val output: OutputStream,
    val sessionKey: ByteArray,
) {
    companion object {
        const val CHUNK_SIZE = 64 * 1024
        const val MAX_FRAME_BYTES = CHUNK_SIZE + PunchCrypto.KEY_LENGTH_BYTES + 64

        sealed class ReceiverHandshakeResult {
            data class Success(val channel: PunchCryptoChannel, val sessionId: String) : ReceiverHandshakeResult()
            data class Rejected(val reason: String) : ReceiverHandshakeResult()
            object LegacyV1Detected : ReceiverHandshakeResult()
        }

        /**
         * Executes the initiator (sender) handshake over the connected TCP socket.
         * Generates an ephemeral P-256 keypair, computes shared secret, verifies receiver's
         * identity proof, and transitions the socket to encrypted framing.
         *
         * Sealed per Plan 046 hardening: cleartext sessionId is NEVER sent in PunchHelloDto
         * (preventing correlation on hostile networks), and identitySecret prioritizes
         * private googleSub over public identityHash (defeating MITM by known contacts).
         */
        @OptIn(ExperimentalEncodingApi::class)
        suspend fun performSenderHandshake(
            socket: Socket,
            sessionId: String = "",
            identitySecret: String,
        ): PunchCryptoChannel = withContext(Dispatchers.IO) {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Generate ephemeral keypair and random salt (sessionId omitted to prevent cleartext leakage)
            val keyPair = PunchCrypto.generateKeyPair()
            val salt = PunchCrypto.generateSalt()
            val hello = PunchHelloDto(
                type = "punch-hello",
                version = 2,
                publicKey = Base64.Default.encode(keyPair.publicKeyBytes),
                salt = Base64.Default.encode(salt),
            )
            PunchLineProtocol.writeLine(output, DexJson.encodeToString(hello))

            // 2. Await receiver's PunchReadyDto
            val readyLine = withTimeoutOrNull(15_000.milliseconds) { PunchLineProtocol.readLine(input) }
                ?: throw RelayCryptoException("Timed out waiting for punch-ready handshake")
            if (readyLine.contains("\"reject\"")) {
                throw RelayCryptoException("Recipient rejected punch connection: $readyLine")
            }

            val ready = try {
                DexJson.decodeFromString<PunchReadyDto>(readyLine)
            } catch (e: Exception) {
                throw RelayCryptoException("Invalid punch-ready response from peer: ${e.message}")
            }
            if (ready.type != "punch-ready" || ready.version != 2) {
                throw RelayCryptoException("Unsupported punch handshake version: ${ready.version}")
            }

            val peerPubKeyBytes = Base64.Default.decode(ready.publicKey)
            val sharedSecret = PunchCrypto.computeSharedSecret(keyPair.privateKeyBytes, peerPubKeyBytes)
            val keys = PunchCrypto.derivePunchKeys(sharedSecret, salt)

            // Verify receiver's proof-of-identity using hardened identity secret
            val expectedReceiverProof = PunchCrypto.computeReceiverAuthProof(
                authKey = keys.authKey,
                identitySecret = identitySecret,
                senderPubKey = keyPair.publicKeyBytes,
                receiverPubKey = peerPubKeyBytes,
            )
            val actualReceiverProof = Base64.Default.decode(ready.authProof)
            if (!PunchCrypto.verifyAuthProof(expectedReceiverProof, actualReceiverProof)) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"auth_failed"}""")
                throw RelayCryptoException("Receiver failed identity authentication proof")
            }

            // 3. Send sender's authentication proof
            val senderProof = PunchCrypto.computeSenderAuthProof(
                authKey = keys.authKey,
                identitySecret = identitySecret,
                senderPubKey = keyPair.publicKeyBytes,
                receiverPubKey = peerPubKeyBytes,
            )
            val auth = PunchAuthDto(authProof = Base64.Default.encode(senderProof))
            PunchLineProtocol.writeLine(output, DexJson.encodeToString(auth))

            PunchCryptoChannel(input, output, keys.sessionKey)
        }

        /**
         * Executes the responder (receiver) handshake upon accepting an incoming TCP socket.
         * Detects legacy v1 manifests and rejects gracefully. For v2 handshakes, performs ECDH
         * key agreement, asserts mutual identity proof, and returns the encrypted channel.
         */
        @OptIn(ExperimentalEncodingApi::class)
        suspend fun performReceiverHandshake(
            socket: Socket,
            deviceIdentitySecret: String,
        ): ReceiverHandshakeResult = withContext(Dispatchers.IO) {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Read first line
            val firstLine = withTimeoutOrNull(15_000.milliseconds) { PunchLineProtocol.readLine(input) }
                ?: return@withContext ReceiverHandshakeResult.Rejected("timeout")

            // Legacy v1 detection: cleanly reject per STOP conditions without crashing or hanging
            if (firstLine.contains("\"manifest\"")) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"upgrade_required"}""")
                return@withContext ReceiverHandshakeResult.LegacyV1Detected
            }

            val hello = try {
                DexJson.decodeFromString<PunchHelloDto>(firstLine)
            } catch (e: Exception) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"invalid_hello"}""")
                return@withContext ReceiverHandshakeResult.Rejected("invalid_hello")
            }

            if (hello.type != "punch-hello" || hello.version != 2) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"unsupported_version"}""")
                return@withContext ReceiverHandshakeResult.Rejected("unsupported_version")
            }

            // 2. Generate receiver keypair and compute shared secret
            val keyPair = PunchCrypto.generateKeyPair()
            val peerPubKeyBytes = Base64.Default.decode(hello.publicKey)
            val salt = Base64.Default.decode(hello.salt)

            val sharedSecret = PunchCrypto.computeSharedSecret(keyPair.privateKeyBytes, peerPubKeyBytes)
            val keys = PunchCrypto.derivePunchKeys(sharedSecret, salt)

            // Compute receiver's proof-of-identity using hardened identity secret
            val receiverProof = PunchCrypto.computeReceiverAuthProof(
                authKey = keys.authKey,
                identitySecret = deviceIdentitySecret,
                senderPubKey = peerPubKeyBytes,
                receiverPubKey = keyPair.publicKeyBytes,
            )
            val ready = PunchReadyDto(
                publicKey = Base64.Default.encode(keyPair.publicKeyBytes),
                authProof = Base64.Default.encode(receiverProof),
            )
            PunchLineProtocol.writeLine(output, DexJson.encodeToString(ready))

            // 3. Await sender's authentication proof
            val authLine = withTimeoutOrNull(15_000.milliseconds) { PunchLineProtocol.readLine(input) }
                ?: return@withContext ReceiverHandshakeResult.Rejected("auth_timeout")
            if (authLine.contains("\"reject\"")) {
                return@withContext ReceiverHandshakeResult.Rejected("sender_rejected")
            }

            val auth = try {
                DexJson.decodeFromString<PunchAuthDto>(authLine)
            } catch (e: Exception) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"invalid_auth"}""")
                return@withContext ReceiverHandshakeResult.Rejected("invalid_auth")
            }

            val expectedSenderProof = PunchCrypto.computeSenderAuthProof(
                authKey = keys.authKey,
                identitySecret = deviceIdentitySecret,
                senderPubKey = peerPubKeyBytes,
                receiverPubKey = keyPair.publicKeyBytes,
            )
            val actualSenderProof = Base64.Default.decode(auth.authProof)
            if (!PunchCrypto.verifyAuthProof(expectedSenderProof, actualSenderProof)) {
                PunchLineProtocol.writeLine(output, """{"type":"reject","reason":"identity"}""")
                return@withContext ReceiverHandshakeResult.Rejected("identity_mismatch")
            }

            ReceiverHandshakeResult.Success(
                channel = PunchCryptoChannel(input, output, keys.sessionKey),
                sessionId = hello.sessionId,
            )
        }
    }

    private var outboundSeq: Long = 0L
    private var inboundSeq: Long = 0L

    /** Writes a length-prefixed, AES-GCM sealed frame with sequence number bound in AAD. */
    suspend fun writeFrame(plaintext: ByteArray) = withContext(Dispatchers.IO) {
        val sealed = PunchCrypto.sealFrame(sessionKey, outboundSeq++, plaintext)
        val len = sealed.size
        val prefix = byteArrayOf(
            (len ushr 24).toByte(),
            (len ushr 16).toByte(),
            (len ushr 8).toByte(),
            len.toByte(),
        )
        output.write(prefix)
        output.write(sealed)
        output.flush()
    }

    /** Reads and decrypts the next length-prefixed AES-GCM frame, verifying sequence number in AAD. */
    suspend fun readFrame(): ByteArray = withContext(Dispatchers.IO) {
        val prefix = ByteArray(4)
        if (!readExact(input, prefix)) {
            throw RelayCryptoException("EOF while reading frame length prefix")
        }
        val len = decodeFrameLength(prefix)
        if (len <= 0 || len > MAX_FRAME_BYTES) {
            throw RelayCryptoException("Hostile or invalid frame length: $len")
        }
        val sealed = ByteArray(len)
        if (!readExact(input, sealed)) {
            throw RelayCryptoException("EOF while reading sealed frame payload ($len bytes)")
        }
        PunchCrypto.openFrame(sessionKey, inboundSeq++, sealed)
    }

    /** Serializes [value] to JSON and transmits it as an encrypted frame. */
    suspend fun <T> writeMessage(serializer: KSerializer<T>, value: T) {
        val str = DexJson.encodeToString(serializer, value)
        writeFrame(str.toByteArray(Charsets.UTF_8))
    }

    /** Reads the next encrypted frame and deserializes it from JSON. */
    suspend fun <T> readMessage(serializer: KSerializer<T>): T {
        val bytes = readFrame()
        val str = bytes.toString(Charsets.UTF_8)
        return DexJson.decodeFromString(serializer, str)
    }

    suspend inline fun <reified T> writeJson(value: T) = writeMessage(serializer<T>(), value)

    suspend inline fun <reified T> readJson(): T = readMessage(serializer<T>())

    /** Streams up to [length] plaintext bytes from [fileStream] as encrypted frames. */
    suspend fun streamFile(
        fileStream: InputStream,
        length: Long,
        isCancelled: () -> Boolean = { false },
        onDelta: suspend (Long) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val buffer = ByteArray(CHUNK_SIZE)
        var sent = 0L
        while (sent < length) {
            if (isCancelled()) return@withContext false
            val toRead = minOf(buffer.size.toLong(), length - sent).toInt()
            val n = fileStream.read(buffer, 0, toRead)
            if (n <= 0) return@withContext false
            val chunk = if (n == buffer.size) buffer else buffer.copyOf(n)
            writeFrame(chunk)
            sent += n
            onDelta(n.toLong())
        }
        true
    }

    /** Receives and decrypts encrypted frames, writing up to [expectedLength] plaintext bytes to [outStream]. */
    suspend fun receiveFile(
        outStream: OutputStream,
        expectedLength: Long,
        onDelta: suspend (Long) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        var received = 0L
        while (received < expectedLength) {
            val plaintext = readFrame()
            outStream.write(plaintext)
            received += plaintext.size
            onDelta(plaintext.size.toLong())
        }
        outStream.flush()
        received
    }

    private fun readExact(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = stream.read(buffer, offset, buffer.size - offset)
            if (count < 0) {
                return offset != 0 && throw RelayCryptoException("Unexpected EOF ($offset/${buffer.size} bytes read)")
            }
            offset += count
        }
        return true
    }

    private fun decodeFrameLength(prefix: ByteArray): Int =
        ((prefix[0].toInt() and 0xFF) shl 24) or
        ((prefix[1].toInt() and 0xFF) shl 16) or
        ((prefix[2].toInt() and 0xFF) shl 8) or
        (prefix[3].toInt() and 0xFF)
}
