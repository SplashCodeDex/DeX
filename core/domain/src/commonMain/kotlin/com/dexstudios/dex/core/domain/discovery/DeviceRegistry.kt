package com.dexstudios.dex.core.domain.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Domain model of one observed peer. Mapped FROM the wire DTO (RegisterDto) at the
 * network boundary — the domain never sees transport types.
 *
 * identityHash/googleSub are DATA fields here (the registry stores what peers disclose);
 * the DISCLOSURE rule — our OWN advertisements never carry them — lives in the adapter
 * that builds localInfo (docs/PROTOCOL.md, inviolable).
 */
data class DiscoveredDeviceInfo(
    val alias: String,
    val version: String,
    val deviceModel: String,
    val deviceType: String,
    val fingerprint: String,
    val port: Int,
    val quicPort: Int,
    val tcpFallbackPort: Int,
    val protocol: String,
    val download: Boolean,
    val identityHash: String? = null,
    val googleSub: String? = null,
    val battery: Int? = null,
    val isCharging: Boolean? = null,
    val wifiBand: String? = null,
    val wifiSsid: String? = null,
)

/** One registry entry: the peer's info plus observation metadata. */
data class ObservedDevice(val ip: String, val info: DiscoveredDeviceInfo, val lastSeenMillis: Long, val viaWan: Boolean = false, val viaRoster: Boolean = false)

/**
 * The device registry use case (plan 028): observed-device state with the freshness
 * policy, DoS caps, telemetry updates, and roster merge — platform-neutral so every
 * peer (desktop, Android, Wear, iOS) reuses it verbatim.
 *
 * Semantics migrated EXACTLY from the legacy DiscoveryEngine (core/network):
 *  - entries keyed by fingerprint; a report REPLACES the entry only when something
 *    actually changed (ip / info / viaWan / viaRoster) — unchanged reports are no-ops;
 *  - hard cap of [MAX_DEVICES] entries with oldest-lastSeen eviction (DoS bound);
 *  - freshness: entries older than [FRESHNESS_MS] vanish on the [STALE_SWEEP_MS] sweep;
 *  - telemetry updates merge battery/isCharging/wifiSsid and refresh lastSeen;
 *  - roster entries (same-account peers advertised by a connected PC) upsert into the
 *    SAME map with viaRoster=true (the desktop UI merges both lanes).
 *
 * THREAD-SAFETY: beacon feeds, telemetry, roster merges, and the TTL sweep arrive on
 * DIFFERENT threads/coroutines. Read-modify-write sequences (change detection, cap
 * eviction, sweeps) are serialized on [lock] — a lost update here would silently drop
 * devices or double-evict under concurrent beacons.
 */
class DeviceRegistry(
    // Owns the TTL sweep; adapters inject their service scope, tests a virtual scope.
    private val scope: CoroutineScope? = null,
    // Injectable clock: deterministic TTL tests + future platforms.
    private val nowMillis: () -> Long,
) {
    private val _devices = MutableStateFlow<Map<String, ObservedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ObservedDevice>> = _devices.asStateFlow()

    private val lock = Any()

    private var sweepJob: Job? = null

    companion object {
        /** DoS bound: at most 100 observed devices (legacy parity). */
        const val MAX_DEVICES = 100

        /** Freshness window: an entry unseen for 20s is gone (legacy parity). */
        const val FRESHNESS_MS = 20_000L

        /** Sweep cadence: every 10s (legacy parity). */
        const val STALE_SWEEP_MS = 10_000L
    }

    /** Starts the stale sweep (idempotent). */
    fun start() {
        if (sweepJob?.isActive == true) return
        sweepJob = scope?.launch {
            while (isActive) {
                delay(STALE_SWEEP_MS)
                sweepExpired()
            }
        }
    }

    /** Stops the sweep (shutdown; the state itself survives for the next start). */
    fun stop() {
        sweepJob?.cancel()
        sweepJob = null
    }

    /**
     * Adds or refreshes a device report. Unchanged reports are no-ops (no flow churn);
     * changed reports replace the entry; the cap evicts the oldest when exceeded.
     * Serialized on [lock] — concurrent beacon threads cannot lose updates.
     */
    fun addDevice(device: ObservedDevice) {
        if (device.info.fingerprint.isBlank()) return

        synchronized(lock) {
            val current = _devices.value
            val existing = current[device.info.fingerprint]
            val changed = existing == null ||
                existing.ip != device.ip ||
                existing.info != device.info ||
                existing.viaWan != device.viaWan ||
                existing.viaRoster != device.viaRoster
            if (!changed) return

            _devices.value = if (current.size >= MAX_DEVICES && !current.containsKey(device.info.fingerprint)) {
                val oldestFp = current.minByOrNull { it.value.lastSeenMillis }?.key
                if (oldestFp != null) {
                    (current - oldestFp) + (device.info.fingerprint to device)
                } else {
                    current + (device.info.fingerprint to device)
                }
            } else {
                current + (device.info.fingerprint to device)
            }
        }
    }

    /**
     * Merges live telemetry into an observed device and refreshes its lastSeen stamp.
     * A no-op for unknown fingerprints (telemetry for an unseen peer is meaningless).
     */
    fun updateTelemetry(fingerprint: String, battery: Int? = null, isCharging: Boolean? = null, wifiSsid: String? = null) {
        synchronized(lock) {
            val existing = _devices.value[fingerprint] ?: return
            addDevice(
                existing.copy(
                    info = existing.info.copy(
                        battery = battery ?: existing.info.battery,
                        isCharging = isCharging ?: existing.info.isCharging,
                        wifiSsid = wifiSsid ?: existing.info.wifiSsid,
                    ),
                    lastSeenMillis = nowMillis(),
                ),
            )
        }
    }

    /**
     * Upserts a roster advertisement (same-account peers listed by a connected PC).
     * Roster entries land in the SAME map with viaRoster=true — matching the legacy
     * addDevice(viaRoster=true) lane the desktop UI already renders.
     */
    fun applyRoster(devices: List<ObservedDevice>) {
        // Snapshot the roster inside the lock so a concurrent add cannot interleave
        // between roster entries (partial-roster flicker).
        synchronized(lock) {
            devices.forEach { addDevice(it.copy(viaRoster = true, viaWan = false)) }
        }
    }

    /** Drops entries past the freshness window. Also self-heals blank fingerprints. */
    fun sweepExpired() {
        val now = nowMillis()
        synchronized(lock) {
            val snapshot = _devices.value
            _devices.value = snapshot.filterValues { device ->
                // Roster entries are not beacon-driven: they live until replaced/revoked,
                // not swept by LAN freshness (legacy behavior — PunchState.roster had no TTL).
                device.viaRoster || (now - device.lastSeenMillis) < FRESHNESS_MS
            }
        }
    }

    fun device(fingerprint: String): ObservedDevice? = _devices.value[fingerprint]

    /** Test seam: clears all state. */
    fun clear() {
        _devices.value = emptyMap()
    }
}

/**
 * Persistence port for the "known peer" (last PC) memory — the desktop adapter wraps
 * PcMemory; future platforms persist it however they choose.
 */
interface KnownPeerPersistence {
    fun load(): Triple<String, String, Int>? // (fingerprint, ip, port)
    fun save(fingerprint: String, ip: String, port: Int)
}

/**
 * Probe port: fetches a peer's info over the network. The desktop keeps its dual-port
 * UDP + HTTPS/HTTP probe in core/network; this port exists so future platforms plug
 * their own transport without touching the registry.
 */
interface DiscoveryProbe {
    /** Returns the peer's info, or null when unreachable. */
    suspend fun probe(ip: String, port: Int): DiscoveredDeviceInfo?
}
