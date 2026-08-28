package com.dexstudios.dex.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the discovery surface: the [SECURITY] disclosure rule (identityHash/googleSub are
 * NEVER advertised — beacons, /info and manual probes must all stay anonymous), the
 * fingerprint-keyed device cache with its 100-entry eviction bound, and the manual
 * HTTP probe's parsing defaults.
 */
class DiscoveryEngineTest {

    private val tempDir = Files.createTempDirectory("dex_discovery_test")
    private val storePath = tempDir.resolve("device_config.preferences_pb")
    private lateinit var scope: CoroutineScope
    private lateinit var deviceConfig: DeviceConfig
    private lateinit var httpClient: HttpClient
    private var engine: DiscoveryEngine? = null

    private class FakeDiscoveryService : IDiscoveryService {
        var startedInfo: RegisterDto? = null
        var startCount = 0
        var stopCount = 0
        var onDevice: ((DiscoveredDevice) -> Unit)? = null

        override fun start(localInfo: RegisterDto, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
            startedInfo = localInfo
            startCount++
            onDevice = onDeviceDiscovered
        }

        override fun stop() {
            stopCount++
        }
    }

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO)
        val store = PreferenceDataStoreFactory.createWithPath(
            produceFile = { storePath.toString().toPath() },
        )
        deviceConfig = DeviceConfig(store, scope)
        // A client that fails every request — tests that do not probe never touch it.
        httpClient = HttpClient(MockEngine { request -> error("unexpected request: ${request.url}") })
    }

    @After
    fun tearDown() {
        engine?.stopDiscovery()
        // Await in-flight DataStore edits BEFORE deleting the temp dir, or a straggling
        // write (e.g. setGoogleSub persistence) surfaces as an uncaught exception that
        // pollutes the next runTest.
        runBlocking { deviceConfig.flushPersistedWrites() }
        scope.coroutineContext.cancelChildren()
        httpClient.close()
        tempDir.toFile().deleteRecursively()
    }

    private fun newEngine(services: List<IDiscoveryService> = emptyList(), client: HttpClient = httpClient) = DiscoveryEngine(deviceConfig, services, client).also { engine = it }

    private fun device(fp: String, ip: String = "10.0.0.$fp", lastSeen: Long = 0L, viaWan: Boolean = false, viaRoster: Boolean = false) = DiscoveredDevice(
        ip = ip,
        info = RegisterDto(
            alias = "dev-$fp",
            version = "2.0",
            deviceModel = "Pixel",
            deviceType = "phone",
            fingerprint = fp,
            port = 48424,
            protocol = "https",
            download = false,
        ),
        lastSeenTimestamp = lastSeen,
        viaWan = viaWan,
        viaRoster = viaRoster,
    )

    private suspend fun awaitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val met = withTimeoutOrNull(timeoutMillis) {
            while (!condition()) delay(10)
            true
        }
        assertTrue(met == true, "condition not met within ${timeoutMillis}ms")
    }

    // === The disclosure rule (SECURITY) ===

    @Test
    fun `localInfo never advertises identity material even when signed in`() = runBlocking {
        deviceConfig.setGoogleSub("secret-sub-123")
        val info = newEngine().localInfo

        assertNull(info.identityHash, "identityHash is a bearer credential and must never be advertised")
        assertNull(info.googleSub, "googleSub is a bearer credential and must never be advertised")
        assertEquals(deviceConfig.fingerprint, info.fingerprint)
        assertEquals("2.0", info.version)
        assertEquals(DeXPorts.HTTPS, info.port)
        assertEquals("https", info.protocol)
        assertTrue(info.download, "the desktop hosts the LocalSend v2 receiver")
    }

    @Test
    fun `startDiscovery hands services the anonymous local info and routes discovered devices`() = runBlocking {
        deviceConfig.setGoogleSub("secret-sub-123")
        val service = FakeDiscoveryService()
        val discovery = newEngine(listOf(service))

        discovery.startDiscovery()

        val advertised = requireNotNull(service.startedInfo)
        assertNull(advertised.googleSub, "services broadcast this payload — it must stay anonymous")
        assertNull(advertised.identityHash)

        service.onDevice?.invoke(device("fp-lan"))
        awaitUntil { discovery.devices.value.containsKey("fp-lan") }
        assertEquals("10.0.0.fp-lan", discovery.devices.value.getValue("fp-lan").ip)

        discovery.stopDiscovery()
        assertEquals(1, service.stopCount)
    }

    // === Device cache ===

    @Test
    fun `addDevice keys by fingerprint and deduplicates identical announcements`() = runBlocking {
        val discovery = newEngine()
        val d = device("fp-a")
        discovery.addDevice(d)
        discovery.addDevice(d.copy()) // structurally identical re-announcement

        assertEquals(1, discovery.devices.value.size)
        assertEquals(d, discovery.devices.value.getValue("fp-a"))
    }

    @Test
    fun `a changed announcement replaces the stored device`() = runBlocking {
        val discovery = newEngine()
        discovery.addDevice(device("fp-a", ip = "10.0.0.1"))
        discovery.addDevice(device("fp-a", ip = "10.0.0.2"))

        assertEquals(1, discovery.devices.value.size)
        assertEquals("10.0.0.2", discovery.devices.value.getValue("fp-a").ip)
    }

    @Test
    fun `wan and roster flags are part of the change detection`() = runBlocking {
        val discovery = newEngine()
        discovery.addDevice(device("fp-r", viaRoster = true))
        assertEquals(true, discovery.devices.value.getValue("fp-r").viaRoster)
        discovery.addDevice(device("fp-w", viaWan = true))
        assertEquals(true, discovery.devices.value.getValue("fp-w").viaWan)
    }

    @Test
    fun `the device cache evicts the oldest entry past 100 devices`() = runBlocking {
        val discovery = newEngine()
        repeat(101) { i -> discovery.addDevice(device("fp-%03d".format(i), lastSeen = i.toLong())) }

        val devices = discovery.devices.value
        assertEquals(100, devices.size)
        assertFalse(devices.containsKey("fp-000"), "the oldest-seen device must be evicted first")
        assertTrue(devices.containsKey("fp-100"))
    }

    @Test
    fun `updating an existing device at capacity never evicts`() = runBlocking {
        val discovery = newEngine()
        repeat(101) { i -> discovery.addDevice(device("fp-%03d".format(i), lastSeen = i.toLong())) }

        // A refresh of an existing entry must not evict any peer.
        discovery.addDevice(device("fp-100", ip = "10.0.0.refreshed", lastSeen = 1000L))

        val devices = discovery.devices.value
        assertEquals(100, devices.size)
        assertTrue(devices.containsKey("fp-001"), "a refresh of an existing entry must not evict peers")
        assertEquals("10.0.0.refreshed", devices.getValue("fp-100").ip)
    }

    // === Manual probe (HTTP /info fallback) ===

    @Test
    fun `manual probe parses info responses with safe defaults and never ingests identity material`() = runBlocking {
        val body = """
            {
              "alias": "Gaming PC",
              "fingerprint": "probe-fp",
              "identityHash": "",
              "deviceType": "desktop",
              "download": true
            }
        """.trimIndent()
        val client = HttpClient(
            MockEngine {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val discovery = newEngine(client = client)

        discovery.sendManualDiscovery("192.168.1.50", port = 9999)
        awaitUntil { discovery.devices.value.containsKey("probe-fp") }

        val info = discovery.devices.value.getValue("probe-fp").info
        assertEquals("Gaming PC", info.alias)
        assertEquals("192.168.1.50", discovery.devices.value.getValue("probe-fp").ip)
        assertEquals("2.0", info.version, "missing version falls back to 2.0")
        assertEquals("Windows PC", info.deviceModel, "missing deviceModel falls back to the desktop default")
        assertEquals("desktop", info.deviceType)
        assertEquals(9999, info.port, "missing port falls back to the probed port")
        assertEquals(DeXPorts.QUIC, info.quicPort)
        assertEquals(DeXPorts.PULL, info.tcpFallbackPort)
        assertEquals("https", info.protocol)
        assertTrue(info.download)
        assertNull(info.identityHash, "blank identityHash must be normalized to null, not ingested")
        assertNull(info.googleSub)
    }

    @Test
    fun `manual probe ignores responses without a fingerprint`() = runBlocking {
        val client = HttpClient(
            MockEngine {
                respond("""{"alias":"No FP Here"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val discovery = newEngine(client = client)

        discovery.sendManualDiscovery("192.168.1.50")
        delay(300)

        assertTrue(discovery.devices.value.isEmpty(), "a fingerprintless /info reply must not register a device")
    }

    @Test
    fun `manual probe failure paths stay silent`() = runBlocking {
        val client = HttpClient(
            MockEngine {
                respond("""{"alias":"broken"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val discovery = newEngine(client = client)

        discovery.sendManualDiscovery("192.168.1.50")
        delay(300)

        assertTrue(discovery.devices.value.isEmpty())
    }
}
