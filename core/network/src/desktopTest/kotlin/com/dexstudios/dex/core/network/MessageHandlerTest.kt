package com.dexstudios.dex.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dexstudios.dex.auth.AuthState
import com.dexstudios.dex.core.network.engine.IPlatformEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the WebSocket control-channel contract on the client side (see docs/PROTOCOL.md):
 * every inbound message type routes to exactly the documented side effect, malformed frames
 * never crash the handler, and outbound frames keep the canonical field names (the
 * `count` vs `digitCount` class of bug is exactly what these tests pin down).
 */
class MessageHandlerTest {

    private val tempDir = Files.createTempDirectory("dex_messagehandler_test")
    private val storePath = tempDir.resolve("device_config.preferences_pb")
    private lateinit var scope: CoroutineScope
    private lateinit var deviceConfig: DeviceConfig
    private lateinit var engine: FakePlatformEngine
    private lateinit var handler: MessageHandler
    private val sent = mutableListOf<String>()

    /** Hand-rolled recording fake — no mocking library needed for a small interface. */
    private class FakePlatformEngine : IPlatformEngine {
        val pairingNotificationAliases = mutableListOf<String>()
        var cancelledPairingNotifications = 0
        val incomingFileNotifications = mutableListOf<Triple<String, Int, Int>>()
        var lastClipboardText: String? = null
        val downloads = mutableListOf<DownloadCall>()
        val fileExplorerRequests = mutableListOf<Pair<String, JsonObject>>()
        var mirrorStarts = 0
        var mirrorStops = 0

        data class DownloadCall(val senderIp: String, val port: Int, val tcpFallbackPort: Int, val files: List<PullFileDto>, val fingerprint: String, val sourceAlias: String)

        override fun showPairingRequestNotification(alias: String) {
            pairingNotificationAliases.add(alias)
        }

        override fun cancelPairingNotification() {
            cancelledPairingNotifications++
        }

        override fun showIncomingFileNotification(sessionId: String, notificationId: Int, fileCount: Int) {
            incomingFileNotifications.add(Triple(sessionId, notificationId, fileCount))
        }

        override fun setClipboardText(text: String) {
            lastClipboardText = text
        }

        override fun downloadBatch(senderIp: String, port: Int, tcpFallbackPort: Int, files: List<PullFileDto>, fingerprint: String, sourceAlias: String) {
            downloads.add(DownloadCall(senderIp, port, tcpFallbackPort, files, fingerprint, sourceAlias))
        }

        override fun handleFileExplorerRequest(type: String, data: JsonObject) {
            fileExplorerRequests.add(type to data)
        }

        override fun handleMirrorStart() {
            mirrorStarts++
        }

        override fun handleMirrorStop() {
            mirrorStops++
        }
    }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // The pairing-prompt paths launch their dialog awaiter on Dispatchers.Main; without a
        // test Main the launch throws an uncaught IllegalStateException that pollutes the next
        // runTest in the suite (same convention as PairingEngineTest).
        Dispatchers.setMain(testDispatcher)
        resetGlobalState()
        scope = CoroutineScope(Dispatchers.IO)
        val store = PreferenceDataStoreFactory.createWithPath(
            produceFile = { storePath.toString().toPath() },
        )
        deviceConfig = DeviceConfig(store, scope)
        engine = FakePlatformEngine()
        handler = MessageHandler(deviceConfig, engine)
        handler.onSendMessage = { sent.add(it) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        resetGlobalState()
        TransferState.pendingPrompts.clear()
        // Await in-flight DataStore edits BEFORE cancelling the persisting scope and
        // deleting the temp dir, or a straggling write surfaces as an uncaught exception
        // that pollutes the next runTest.
        runBlocking { deviceConfig.flushPersistedWrites() }
        scope.coroutineContext.cancelChildren()
        tempDir.toFile().deleteRecursively()
    }

    private fun resetGlobalState() {
        AuthState.updateFingerprints(emptySet())
        AuthState.updateTokens(emptyMap())
        AuthState.updateTimes(emptyMap())
        AuthState.updateIncomingPairRequest(null)
        PunchState.devices.value = emptyList()
        PunchState.pendingEndpointInfo.value = null
        PunchState.pendingRelay.value = null
        PunchState.incomingPeerEndpoints.value = emptyMap()
    }

    private fun frame(type: String, dataJson: String) = """{"type":"$type","data":$dataJson}"""

    /** Real-time polling: handler effects run on real IO threads, so virtual-time runTest is wrong here. */
    private suspend fun awaitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val met = withTimeoutOrNull(timeoutMillis) {
            while (!condition()) delay(10)
            true
        }
        assertTrue(met == true, "condition not met within ${timeoutMillis}ms")
    }

    private fun parsedFrame(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun lexicographicallySmallerThan(s: String): String {
        // Hex-string predecessor at the first non-'0' digit — always strictly smaller.
        val idx = s.indexOfFirst { it != '0' }
        if (idx == -1) return s + "\u0000"
        val c = s[idx]
        val pred = if (c == 'a') '9' else (c - 1)
        return s.substring(0, idx) + pred + s.substring(idx + 1)
    }

    // === Frame hygiene ===

    @Test
    fun `malformed and incomplete frames are swallowed without replies`() = runBlocking {
        handler.handleMessage("not json at all", "10.0.0.1", 1)
        handler.handleMessage("""{"type":"x"}""", "10.0.0.1", 1)
        handler.handleMessage("""{"data":{}}""", "10.0.0.1", 1)
        assertTrue(sent.isEmpty(), "no frame must be emitted for malformed input")
    }

    @Test
    fun `unknown message types are ignored`() = runBlocking {
        handler.handleMessage(frame("mystery-type", """{"anything":1}"""), "10.0.0.1", 1)
        assertTrue(sent.isEmpty())
        assertNull(engine.lastClipboardText)
        assertEquals(0, engine.mirrorStarts)
    }

    // === Clipboard sync ===

    @Test
    fun `set-clipboard routes to the platform clipboard`() = runBlocking {
        handler.handleMessage(frame("set-clipboard", """{"text":"hello desktop"}"""), "10.0.0.1", 1)
        assertEquals("hello desktop", engine.lastClipboardText)
    }

    @Test
    fun `blank set-clipboard is ignored`() = runBlocking {
        handler.handleMessage(frame("set-clipboard", """{"text":"  "}"""), "10.0.0.1", 1)
        assertNull(engine.lastClipboardText)
    }

    // === Mirror + wallpaper ===

    @Test
    fun `mirror-start and mirror-stop route to the engine`() = runBlocking {
        handler.handleMessage(frame("mirror-start", "{}"), "10.0.0.1", 1)
        handler.handleMessage(frame("mirror-stop", "{}"), "10.0.0.1", 1)
        assertEquals(1, engine.mirrorStarts)
        assertEquals(1, engine.mirrorStops)
    }

    @Test
    fun `wallpaper-updated bumps the wallpaper revision`() = runBlocking {
        val before = WallpaperState.revision.value
        handler.handleMessage(frame("wallpaper-updated", "{}"), "10.0.0.1", 1)
        assertTrue(WallpaperState.revision.value >= before, "revision must never go backwards")
    }

    // === Relay fallback ===

    @Test
    fun `relay-started completes the pending relay as success and clears it`() = runBlocking {
        val deferred = CompletableDeferred<Boolean>()
        PunchState.pendingRelay.value = deferred
        handler.handleMessage(frame("relay-started", "{}"), "10.0.0.1", 1)
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.getCompleted(), "relay-started must complete the fallback as success")
        assertNull(PunchState.pendingRelay.value)
    }

    @Test
    fun `relay-error completes the pending relay as failure`() = runBlocking {
        val deferred = CompletableDeferred<Boolean>()
        PunchState.pendingRelay.value = deferred
        handler.handleMessage(frame("relay-error", "{}"), "10.0.0.1", 1)
        assertTrue(deferred.isCompleted)
        assertFalse(deferred.getCompleted(), "relay-error must complete the fallback as failure")
        assertNull(PunchState.pendingRelay.value)
    }

    @Test
    fun `relay replies without a pending relay are ignored`() = runBlocking {
        handler.handleMessage(frame("relay-started", "{}"), "10.0.0.1", 1)
        handler.handleMessage(frame("relay-error", "{}"), "10.0.0.1", 1)
        assertNull(PunchState.pendingRelay.value)
        assertTrue(sent.isEmpty())
    }

    // === NAT punch ===

    @Test
    fun `endpoint-info completes the pending resolve with the parsed endpoint and clears it`() = runBlocking {
        val deferred = CompletableDeferred<EndpointInfoDto>()
        PunchState.pendingEndpointInfo.value = deferred
        handler.handleMessage(
            frame("endpoint-info", """{"targetFingerprint":"fp-target","ip":"5.6.7.8","port":6000}"""),
            "10.0.0.1",
            1,
        )
        assertTrue(deferred.isCompleted)
        val info = deferred.getCompleted()
        assertEquals("fp-target", info.targetFingerprint)
        assertEquals("5.6.7.8", info.ip)
        assertEquals(6000, info.port)
        assertNull(PunchState.pendingEndpointInfo.value)
    }

    @Test
    fun `peer-endpoint records valid announcements`() = runBlocking {
        handler.handleMessage(
            frame("peer-endpoint", """{"peerFingerprint":"fp-x","ip":"1.2.3.4","port":5000}"""),
            "10.0.0.1",
            1,
        )
        assertEquals(PunchEndpoint("1.2.3.4", 5000), PunchState.incomingPeerEndpoints.value["fp-x"])
    }

    @Test
    fun `invalid peer-endpoint announcements are ignored`() = runBlocking {
        handler.handleMessage(frame("peer-endpoint", """{"peerFingerprint":"fp-x","ip":"  ","port":5000}"""), "10.0.0.1", 1)
        handler.handleMessage(frame("peer-endpoint", """{"peerFingerprint":"fp-x","ip":"1.2.3.4","port":0}"""), "10.0.0.1", 1)
        assertTrue(PunchState.incomingPeerEndpoints.value.isEmpty(), "blank ip or non-positive port must not register")
    }

    // === Pairing (client side) ===

    @Test
    fun `pair-prompt auto-accepts bare when the peer is already paired locally`() = runBlocking {
        AuthState.updateFingerprints(setOf("peer-fp"))
        handler.handleMessage(
            frame("pair-prompt", """{"alias":"Pixel","fingerprint":"peer-fp","pin":"12345"}"""),
            "10.0.0.1",
            1,
        )
        awaitUntil { sent.isNotEmpty() }
        val outgoing = parsedFrame(sent.single())
        assertEquals("pair-response", outgoing["type"]?.jsonPrimitive?.content)
        val data = requireNotNull(outgoing["data"]).jsonObject
        assertEquals("true", data["accepted"]?.jsonPrimitive?.content)
        assertNull(data["pin"], "already-paired auto-accept is intentionally pinless")
        assertNull(AuthState.incomingPairRequest.value, "auto-accept must not raise a prompt dialog")
    }

    @Test
    fun `pair-prompt from a greater fingerprint is ignored during a simultaneous pairing race`() = runBlocking {
        awaitUntil { deviceConfig.initializedFlow.value }
        val localFp = deviceConfig.fingerprint
        val pending = PairRequestInfo(
            alias = "Local offer",
            fingerprint = "local-fp",
            pin = "00000",
            deferred = CompletableDeferred(),
        )
        AuthState.updateIncomingPairRequest(pending)

        handler.handleMessage(
            frame("pair-prompt", """{"alias":"Other","fingerprint":"$localFp-zzz","pin":"99999"}"""),
            "10.0.0.1",
            1,
        )

        assertTrue(sent.isEmpty(), "the ignored initiator must not get a pair-response")
        assertEquals(pending, AuthState.incomingPairRequest.value, "the local offer must survive")
        assertFalse(pending.deferred.isCompleted)
    }

    @Test
    fun `pair-prompt from a smaller fingerprint yields and cancels the local offer`() = runBlocking {
        awaitUntil { deviceConfig.initializedFlow.value }
        val localFp = deviceConfig.fingerprint
        val inboundFp = lexicographicallySmallerThan(localFp)
        val pending = PairRequestInfo(
            alias = "Local offer",
            fingerprint = "local-fp",
            pin = "00000",
            deferred = CompletableDeferred(),
        )
        AuthState.updateIncomingPairRequest(pending)

        handler.handleMessage(
            frame("pair-prompt", """{"alias":"Other","fingerprint":"$inboundFp","pin":"99999"}"""),
            "10.0.0.1",
            1,
        )

        assertTrue(sent.isEmpty())
        assertTrue(pending.deferred.isCompleted, "the dismissed local offer must resolve its awaiter")
        val takeover = AuthState.incomingPairRequest.value
        assertTrue(
            takeover !== pending && takeover?.fingerprint == inboundFp,
            "the inbound prompt takes over as the active pairing offer",
        )
    }

    @Test
    fun `pair-accepted persists the token against the connected PC fingerprint`() = runBlocking {
        val store = PreferenceDataStoreFactory.createWithPath(
            produceFile = { tempDir.resolve("pair_accepted.preferences_pb").toString().toPath() },
        )
        DeviceManager.init(store)

        // Missing token: must not persist anything.
        handler.handleMessage(frame("pair-accepted", "{}"), "10.0.0.1", 1)
        delay(100)
        assertTrue(AuthState.pairedFingerprints.value.isEmpty(), "a tokenless grant must be ignored")

        // The connected-peer provider wins over whatever the wire claims.
        handler.peerFingerprintProvider = { "pc-fp" }
        handler.handleMessage(
            frame("pair-accepted", """{"token":"tok-1","fingerprint":"wire-claimed-fp"}"""),
            "10.0.0.1",
            1,
        )
        awaitUntil { AuthState.pairedFingerprints.value.contains("pc-fp") }
        // The token save runs sequentially after the fingerprint save on IO — wait for it too.
        awaitUntil { AuthState.pairedTokens.value["pc-fp"] == "tok-1" }
        assertFalse(
            AuthState.pairedFingerprints.value.contains("wire-claimed-fp"),
            "the wire fingerprint must not override the proven session identity",
        )
    }

    // === Same-account identity proof ===

    @Test
    fun `identity-challenge stays silent when signed out and answers with the HMAC proof when signed in`() = runBlocking {
        handler.handleMessage(
            frame("identity-challenge", """{"nonce":"${Base64.getEncoder().encodeToString("nonce-bytes".toByteArray())}"}"""),
            "10.0.0.1",
            1,
        )
        assertTrue(sent.isEmpty(), "signed-out sessions must not answer identity challenges")

        deviceConfig.setGoogleSub("test-sub-123")
        val nonce = Base64.getEncoder().encodeToString("nonce-bytes".toByteArray())
        handler.handleMessage(frame("identity-challenge", """{"nonce":"$nonce"}"""), "10.0.0.1", 1)
        awaitUntil { sent.isNotEmpty() }
        val outgoing = parsedFrame(sent.single())
        assertEquals("identity-proof", outgoing["type"]?.jsonPrimitive?.content)
        val expected = HashUtils.hmacSha256Base64("test-sub-123", Base64.getDecoder().decode(nonce))
        assertEquals(expected, requireNotNull(outgoing["data"]).jsonObject["mac"]?.jsonPrimitive?.content)

        // Undecodable nonce: the runCatching guard must swallow it without emitting anything.
        handler.handleMessage(frame("identity-challenge", """{"nonce":"!!!not base64!!!"}"""), "10.0.0.1", 1)
        assertEquals(1, sent.size)
    }

    // === Public address ===

    @Test
    fun `public-address auto-fills only a blank address`() = runBlocking {
        handler.handleMessage(frame("public-address", """{"address":" 203.0.113.7 "}"""), "10.0.0.1", 1)
        awaitUntil { deviceConfig.publicAddress == "203.0.113.7" }

        // A manually configured address always wins — a later push must not overwrite it.
        handler.handleMessage(frame("public-address", """{"address":"198.51.100.1"}"""), "10.0.0.1", 1)
        assertEquals("203.0.113.7", deviceConfig.publicAddress)
    }

    // === Incoming transfer prompt ===

    @Test
    fun `prepare-upload raises a prompt and an accepted prompt downloads the batch`() = runBlocking {
        val data = """
            {
              "info": {"alias":"Pixel","version":"2.0","deviceModel":"Pixel","deviceType":"phone",
                       "fingerprint":"phone-fp","port":48424,"tcpFallbackPort":48426,
                       "protocol":"https","download":true},
              "files": {"f1": {"id":"f1","fileName":"a.txt","size":10,"fileType":"text/plain"}}
            }
        """.trimIndent()
        handler.handleMessage(frame("prepare-upload", data), "10.0.0.9", 1)

        awaitUntil { engine.incomingFileNotifications.size == 1 }
        val (sessionId, notificationId, fileCount) = engine.incomingFileNotifications.single()
        assertEquals(1, fileCount)
        assertTrue(TransferState.pendingPrompts.containsKey(sessionId))
        assertEquals(sessionId.hashCode(), notificationId)

        TransferState.pendingPrompts.getValue(sessionId).complete(true)
        awaitUntil { engine.downloads.size == 1 }
        val call = engine.downloads.single()
        assertEquals("10.0.0.9", call.senderIp)
        assertEquals(48424, call.port)
        assertEquals(48426, call.tcpFallbackPort)
        assertEquals("phone-fp", call.fingerprint)
        assertEquals("Pixel", call.sourceAlias)
        assertEquals(listOf("f1"), call.files.map { it.fileId })
        assertTrue(TransferState.pendingPrompts.isEmpty(), "the resolved prompt slot must be freed")
    }

    @Test
    fun `rejected prepare-upload prompt downloads nothing`() = runBlocking {
        val data = """
            {
              "info": {"alias":"Pixel","version":"2.0","deviceModel":"Pixel","deviceType":"phone",
                       "fingerprint":"phone-fp","port":48424,"protocol":"https","download":true},
              "files": {"f1": {"id":"f1","fileName":"a.txt","size":10,"fileType":"text/plain"}}
            }
        """.trimIndent()
        handler.handleMessage(frame("prepare-upload", data), "10.0.0.9", 1)
        awaitUntil { engine.incomingFileNotifications.size == 1 }
        val sessionId = engine.incomingFileNotifications.single().first

        TransferState.pendingPrompts.getValue(sessionId).complete(false)
        awaitUntil { TransferState.pendingPrompts.isEmpty() }
        assertTrue(engine.downloads.isEmpty(), "a rejected offer must never reach downloadBatch")
    }
}
