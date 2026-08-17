package com.dexstudios.dex.core.network.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TransportMessageTest {

    @Test
    fun serialize_and_deserialize_AuthRequest() {
        val msg = AuthRequest(
            fingerprint = "test_fp",
            deviceName = "test_device",
            challengeResponse = "resp_123"
        )

        val jsonStr = Json.encodeToString(msg)
        val decoded = Json.decodeFromString<AuthRequest>(jsonStr)

        assertEquals(msg.fingerprint, decoded.fingerprint)
        assertEquals(msg.deviceName, decoded.deviceName)
        assertEquals(msg.challengeResponse, decoded.challengeResponse)
    }

    @Test
    fun serialize_and_deserialize_FileTransferChunk() {
        val chunk = FileTransferChunk(
            transferId = "transfer_123",
            chunkIndex = 5,
            data = "base64encodeddata=="
        )

        val jsonStr = Json.encodeToString(chunk)
        val decoded = Json.decodeFromString<FileTransferChunk>(jsonStr)

        assertEquals(chunk.transferId, decoded.transferId)
        assertEquals(chunk.chunkIndex, decoded.chunkIndex)
        assertEquals(chunk.data, decoded.data)
    }
}
