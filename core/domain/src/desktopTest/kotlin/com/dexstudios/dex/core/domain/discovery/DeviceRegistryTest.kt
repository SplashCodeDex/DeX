package com.dexstudios.dex.core.domain.discovery

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 028 contract suite: every legacy DiscoveryEngine semantic — change-detection,
 * the 100-cap oldest-eviction, the 20s/10s freshness sweep, telemetry merge, roster
 * upsert, and the no-TTL-for-roster rule — pinned here so the domain registry can never
 * drift from the behavior the desktop shipped with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRegistryTest {

    private fun info(fp: String, alias: String = "dev-$fp", battery: Int? = null, ssid: String? = null) = DiscoveredDeviceInfo(
        alias = alias,
        version = "2.0",
        deviceModel = "Pixel",
        deviceType = "phone",
        fingerprint = fp,
        port = 48424,
        quicPort = 48423,
        tcpFallbackPort = 48426,
        protocol = "https",
        download = false,
        battery = battery,
        wifiSsid = ssid,
    )

    private fun device(fp: String, lastSeen: Long, viaWan: Boolean = false, viaRoster: Boolean = false, ip: String = "10.0.0.1") =
        ObservedDevice(ip = ip, info = info(fp), lastSeenMillis = lastSeen, viaWan = viaWan, viaRoster = viaRoster)

    @Test
    fun `add stores a new device keyed by fingerprint`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })

        registry.addDevice(device("fp-1", lastSeen = 1_000L))

        assertEquals("fp-1", registry.device("fp-1")?.info?.fingerprint)
        assertEquals(1, registry.devices.value.size)
    }

    @Test
    fun `unchanged reports are no-ops`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })
        registry.addDevice(device("fp-1", lastSeen = 1_000L))
        val firstSnapshot = registry.devices.value

        // Same everything: the entry must NOT be replaced (no flow churn, no lastSeen bump).
        registry.addDevice(device("fp-1", lastSeen = 1_000L))

        assertEquals(firstSnapshot, registry.devices.value)
    }

    @Test
    fun `changed reports replace the entry`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })
        registry.addDevice(device("fp-1", lastSeen = 1_000L, ip = "10.0.0.1"))

        registry.addDevice(device("fp-1", lastSeen = 2_000L, ip = "10.0.0.2"))

        assertEquals("10.0.0.2", registry.device("fp-1")?.ip)
        assertEquals(2_000L, registry.device("fp-1")?.lastSeenMillis)
    }

    @Test
    fun `cap of 100 evicts the oldest when exceeded`() {
        val registry = DeviceRegistry(nowMillis = { 0L })

        // Fill with staggered lastSeen so fp-0 is the oldest.
        for (i in 0 until DeviceRegistry.MAX_DEVICES) {
            registry.addDevice(device("fp-$i", lastSeen = i.toLong()))
        }
        registry.addDevice(device("fp-new", lastSeen = 999_999L))

        assertEquals(DeviceRegistry.MAX_DEVICES, registry.devices.value.size)
        assertNull(registry.device("fp-0"), "the oldest entry must be evicted")
        assertTrue(registry.device("fp-new") != null)
        assertTrue(registry.device("fp-99") != null, "recent entries survive")
    }

    @Test
    fun `existing fingerprint refresh never triggers eviction`() {
        val registry = DeviceRegistry(nowMillis = { 0L })
        for (i in 0 until DeviceRegistry.MAX_DEVICES) {
            registry.addDevice(device("fp-$i", lastSeen = i.toLong()))
        }

        // Refresh the OLDEST entry: it is an update, not a 101st device.
        registry.addDevice(device("fp-0", lastSeen = 999_999L))

        assertEquals(DeviceRegistry.MAX_DEVICES, registry.devices.value.size)
        assertTrue(registry.device("fp-0") != null, "refreshed entries are never evicted by their own update")
    }

    @Test
    fun `stale entries are swept after the freshness window`() {
        var now = 100_000L
        val registry = DeviceRegistry(nowMillis = { now })

        registry.addDevice(device("fp-old", lastSeen = now - 21_000))
        registry.addDevice(device("fp-fresh", lastSeen = now - 5_000))

        registry.sweepExpired()

        assertNull(registry.device("fp-old"), "21s-old entry must be swept")
        assertTrue(registry.device("fp-fresh") != null, "5s-old entry must survive")
    }

    @Test
    fun `the periodic sweep honors cadence and freshness`() = runTest {
        var now = 0L
        val registry = DeviceRegistry(scope = backgroundScope, nowMillis = { now })
        registry.start()

        now = 1_000L
        registry.addDevice(device("fp-1", lastSeen = now))
        assertTrue(registry.device("fp-1") != null)

        now += 25_000L
        advanceTimeBy(DeviceRegistry.STALE_SWEEP_MS)
        runCurrent()

        assertTrue(registry.device("fp-1") == null, "the sweep must remove the expired entry")
        registry.stop()
    }

    @Test
    fun `telemetry merges into an observed device and refreshes lastSeen`() {
        var now = 5_000L
        val registry = DeviceRegistry(nowMillis = { now })
        registry.addDevice(device("fp-1", lastSeen = 1_000L))

        now = 9_000L
        registry.updateTelemetry("fp-1", battery = 77, isCharging = true, wifiSsid = "HomeNet")

        val entry = registry.device("fp-1")!!
        assertEquals(77, entry.info.battery)
        assertEquals(true, entry.info.isCharging)
        assertEquals("HomeNet", entry.info.wifiSsid)
        assertEquals(9_000L, entry.lastSeenMillis)
    }

    @Test
    fun `telemetry for unknown peers is ignored`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })

        registry.updateTelemetry("ghost", battery = 50)

        assertNull(registry.device("ghost"))
        assertTrue(registry.devices.value.isEmpty())
    }

    @Test
    fun `roster entries land with viaRoster and survive the freshness sweep`() {
        var now = 10_000L
        val registry = DeviceRegistry(nowMillis = { now })

        registry.applyRoster(listOf(device("fp-roster", lastSeen = now)))
        assertEquals(true, registry.device("fp-roster")?.viaRoster)

        now += 60_000L
        registry.sweepExpired()

        assertTrue(registry.device("fp-roster") != null, "roster entries are not beacon-driven; no TTL sweep")
    }

    @Test
    fun `blank fingerprints are never admitted`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })

        registry.addDevice(device("", lastSeen = 1_000L))

        assertTrue(registry.devices.value.isEmpty())
    }

    @Test
    fun `viaWan flag round-trips`() {
        val registry = DeviceRegistry(nowMillis = { 1_000L })
        registry.addDevice(device("fp-wan", lastSeen = 1_000L, viaWan = true))
        assertEquals(true, registry.device("fp-wan")?.viaWan)
    }
}
