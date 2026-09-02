package com.dexstudios.dex.core.network

import com.dexstudios.dex.core.sync.SyncCollections
import com.dexstudios.dex.core.sync.SyncEngine
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Bridge from desktop domain state into the sync engine (plan 031 WP3). Every synced
 * surface funnels through this single object so the payloads stay consistent and the
 * privacy law has one choke point:
 *
 * - HISTORY: one record per transfer (metadata only — no uri on the wire? YES uri is
 *   local metadata and fine to sync; file CONTENT never rides here by construction).
 * - DEVICES: the roster card for THIS device (own fingerprint, alias, deviceType,
 *   platform) — written at startup and on alias change; tombstoned on identity reset.
 *
 * Koin-optional by design: sync must never block the desktop when the graph or the
 * engine is absent (tests, early startup). The engine itself is offline-first — a
 * queued mutation simply waits for the next flush.
 */
object SyncBridge {

    @Volatile
    private var engine: SyncEngine? = null
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** Wires the engine; called from Koin once the sync graph exists. Idempotent. */
    fun attach(engine: SyncEngine?) {
        if (engine != null) {
            synchronized(this) { this.engine = engine }
        }
    }

    /**
     * Cancels in-flight bridge writes (shutdown coordinator step): a straggler
     * engine.mutate launched after DataStore teardown would crash as an uncaught
     * exception in whatever runs next — the same pollution class the MessageHandler
     * scope fix addressed. Idempotent; late enqueues after shutdown are dropped
     * (offline-first: they are re-queued on the next session's first transfer anyway).
     */
    @Volatile
    private var shutdown = false

    fun stop() {
        shutdown = true
        scope.cancel()
    }

    fun historyRecord(record: TransferRecord) {
        enqueue { engine ->
            engine.mutate(
                SyncCollections.HISTORY,
                key = record.id,
                payload = buildJsonObject {
                    put("name", record.name)
                    put("size", record.size)
                    put("timestamp", record.timestamp)
                    put("direction", record.direction)
                    put("peerDevice", record.peerDevice)
                    put("status", record.status)
                },
            )
        }
    }

    /** The roster card for THIS device — what other peers render as "this PC". */
    fun ownDeviceCard(fingerprint: String, alias: String, deviceModel: String, platform: String) {
        enqueue { engine ->
            engine.mutate(
                SyncCollections.DEVICES,
                key = fingerprint,
                payload = buildJsonObject {
                    put("alias", alias)
                    put("deviceModel", deviceModel)
                    put("deviceType", "desktop")
                    put("platform", platform)
                },
            )
        }
    }

    /** Identity reset: the old fingerprint's roster entry must vanish everywhere. */
    fun tombstoneDevice(fingerprint: String) {
        enqueue { engine ->
            engine.mutate(SyncCollections.DEVICES, key = fingerprint, payload = null)
        }
    }

    private fun enqueue(block: suspend (com.dexstudios.dex.core.sync.SyncEngine) -> Unit) {
        val target = engine ?: return
        if (shutdown) return // late enqueue after shutdown: dropped, next session re-queues
        scope.launch { block(target) }
    }
}
