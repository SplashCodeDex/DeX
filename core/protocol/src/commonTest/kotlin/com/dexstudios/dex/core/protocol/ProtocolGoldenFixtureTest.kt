package com.dexstudios.dex.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Golden fixtures for the wire contract. These exact JSON frames are protocol law:
 * desktop, phone, watch, tablet and the relay server must all keep producing and
 * accepting them byte-identically. When a fixture must legitimately change, the
 * change lands here first, in the same commit that updates every peer and
 * docs/PROTOCOL.md — never by relaxing an assertion.
 */
class ProtocolGoldenFixtureTest {

    @Test
    fun pair_prompt_envelope_is_canonical() {
        val frame = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_PROMPT) {
            put(FieldNames.PIN, "482910")
            put(FieldNames.FINGERPRINT, "fp-desktop-1")
        }
        assertEquals("""{"type":"pair-prompt","data":{"pin":"482910","fingerprint":"fp-desktop-1"}}""", frame)
    }

    @Test
    fun pair_response_echoes_pin_only_when_present() {
        val withPin = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_RESPONSE) {
            put(FieldNames.ACCEPTED, true)
            put(FieldNames.PIN, "482910")
        }
        assertEquals(
            """{"type":"pair-response","data":{"accepted":true,"pin":"482910"}}""",
            withPin,
        )

        val withoutPin = ProtocolEnvelope.envelopeOf(MessageTypes.PAIR_RESPONSE) {
            put(FieldNames.ACCEPTED, false)
        }
        assertEquals("""{"type":"pair-response","data":{"accepted":false}}""", withoutPin)
    }

    @Test
    fun pin_digit_telemetry_uses_digit_count_not_count() {
        // The original `count` vs `digitCount` mismatch bug happened because this
        // field was restated per call site. It is pinned here forever.
        val frame = ProtocolEnvelope.envelopeOf(MessageTypes.PIN_DIGIT_ENTERED) {
            put(FieldNames.DIGIT_COUNT, 3)
        }
        assertEquals("""{"type":"pin-digit-entered","data":{"digitCount":3}}""", frame)
    }

    @Test
    fun identity_proof_carries_only_mac() {
        val frame = ProtocolEnvelope.envelopeOf(MessageTypes.IDENTITY_PROOF) {
            put(FieldNames.MAC, "c2VjcmV0LW1hYw==")
        }
        assertEquals("""{"type":"identity-proof","data":{"mac":"c2VjcmV0LW1hYw=="}}""", frame)
    }

    @Test
    fun device_roster_request_has_empty_data() {
        val frame = ProtocolEnvelope.envelopeOf(MessageTypes.DEVICE_ROSTER)
        assertEquals("""{"type":"device-roster","data":{}}""", frame)
    }

    @Test
    fun relay_reply_types_are_distinct() {
        val started = ProtocolEnvelope.envelopeOf(MessageTypes.RELAY_STARTED) {
            put(FieldNames.SESSION_ID, "s-1")
            put(FieldNames.TARGET_FINGERPRINT, "fp-b")
        }
        val errored = ProtocolEnvelope.envelopeOf(MessageTypes.RELAY_ERROR) {
            put(FieldNames.SESSION_ID, "s-1")
            put(FieldNames.TARGET_FINGERPRINT, "fp-b")
        }
        assertEquals("""{"type":"relay-started","data":{"sessionId":"s-1","targetFingerprint":"fp-b"}}""", started)
        assertEquals("""{"type":"relay-error","data":{"sessionId":"s-1","targetFingerprint":"fp-b"}}""", errored)
    }

    @Test
    fun pull_progress_uses_canonical_field_names() {
        val frame = ProtocolEnvelope.envelopeOf(MessageTypes.PULL_PROGRESS) {
            put(FieldNames.REQUEST_ID, "req-9")
            put(FieldNames.STATE, FieldNames.STATE_DONE)
            put(FieldNames.DONE_FILES, 2)
            put(FieldNames.TOTAL_FILES, 2)
            put(FieldNames.SENT_BYTES, 2048L)
            put(FieldNames.TOTAL_BYTES, 2048L)
        }
        assertEquals(
            """{"type":"pull-progress","data":{"requestId":"req-9","state":"done","doneFiles":2,"totalFiles":2,"sentBytes":2048,"totalBytes":2048}}""",
            frame,
        )
    }

    @Test
    fun decode_type_and_data_round_trip() {
        val original = """{"type":"unpair","data":{"fingerprint":"fp-x"}}"""
        assertEquals(MessageTypes.UNPAIR, ProtocolEnvelope.decodeType(original))
        val data = assertIs<JsonObject>(ProtocolEnvelope.decodeData(original))
        assertEquals("fp-x", data[FieldNames.FINGERPRINT]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun decode_tolerates_malformed_frames() {
        assertNull(ProtocolEnvelope.decodeType("not json"))
        assertNull(ProtocolEnvelope.decodeType("""{"data":{}}"""))
        assertNull(ProtocolEnvelope.decodeData("""{"type":"x"}"""))
    }

    @Test
    fun message_type_constants_are_stable() {
        // Wire values are frozen protocol law; these assertions make accidental
        // constant edits fail CI before they reach any peer.
        assertEquals("pair-request", MessageTypes.PAIR_REQUEST)
        assertEquals("pair-prompt", MessageTypes.PAIR_PROMPT)
        assertEquals("pair-response", MessageTypes.PAIR_RESPONSE)
        assertEquals("pair-accepted", MessageTypes.PAIR_ACCEPTED)
        assertEquals("pair-cancelled", MessageTypes.PAIR_CANCELLED)
        assertEquals("pin-digit-entered", MessageTypes.PIN_DIGIT_ENTERED)
        assertEquals("identity-challenge", MessageTypes.IDENTITY_CHALLENGE)
        assertEquals("identity-proof", MessageTypes.IDENTITY_PROOF)
        assertEquals("trust-check", MessageTypes.TRUST_CHECK)
        assertEquals("unpair", MessageTypes.UNPAIR)
        assertEquals("prepare-upload", MessageTypes.PREPARE_UPLOAD)
        assertEquals("relay-transfer", MessageTypes.RELAY_TRANSFER)
        assertEquals("relay-started", MessageTypes.RELAY_STARTED)
        assertEquals("relay-error", MessageTypes.RELAY_ERROR)
        assertEquals("resolve-endpoint", MessageTypes.RESOLVE_ENDPOINT)
        assertEquals("endpoint-info", MessageTypes.ENDPOINT_INFO)
        assertEquals("peer-endpoint", MessageTypes.PEER_ENDPOINT)
        assertEquals("device-roster", MessageTypes.DEVICE_ROSTER)
        assertEquals("public-address", MessageTypes.PUBLIC_ADDRESS)
        assertEquals("telemetry", MessageTypes.TELEMETRY)
        assertEquals("set-clipboard", MessageTypes.SET_CLIPBOARD)
        assertEquals("wallpaper-updated", MessageTypes.WALLPAPER_UPDATED)
        assertEquals("mirror-start", MessageTypes.MIRROR_START)
        assertEquals("mirror-stop", MessageTypes.MIRROR_STOP)
        assertEquals("mirror-config", MessageTypes.MIRROR_CONFIG)
        assertEquals("list-shared-folders", MessageTypes.LIST_SHARED_FOLDERS)
        assertEquals("list-shared-folders-reply", MessageTypes.LIST_SHARED_FOLDERS_REPLY)
        assertEquals("browse-folder", MessageTypes.BROWSE_FOLDER)
        assertEquals("browse-reply", MessageTypes.BROWSE_REPLY)
        assertEquals("pull-files", MessageTypes.PULL_FILES)
        assertEquals("pull-cancel", MessageTypes.PULL_CANCEL)
        assertEquals("pull-progress", MessageTypes.PULL_PROGRESS)
        assertEquals("pull-reply", MessageTypes.PULL_REPLY)
        assertEquals("grant-shared-folder", MessageTypes.GRANT_SHARED_FOLDER)
        assertEquals("grant-shared-folder-reply", MessageTypes.GRANT_SHARED_FOLDER_REPLY)
        assertEquals("grant-reply", MessageTypes.GRANT_REPLY)
        assertEquals("reply", MessageTypes.REPLY)
    }
}
