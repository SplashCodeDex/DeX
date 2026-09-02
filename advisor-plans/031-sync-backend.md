# Plan 031 — Sync Backend: Self-Hosted Metadata Sync (Phase 0-B)

> Status: DONE (client side complete: WP1 engine + WP2 storage/DI + WP3 transport/wiring + WP4 scheduler; the host surface itself lives in plan 032 — DONE there)
> Decision settled 2026-08-31: self-hosted on the Hetzner VPS
> Depends on: 025 (DONE)
> Effort: L (2–3 weeks solo)

## What shipped (Work Package 4, 2026-09-01 — closing the client loop)

- **`DesktopSyncScheduler`** (core/network desktopMain): the always-on flush loop —
  periodic exchange every 5 min (also pulls host records when the queue is empty:
  convergence for fresh boots/new roster members), idle when the host URL is unset
  (sync disabled) or signed out (queue retained), backoff on failure (cadence
  magnitude — an unreachable host never busy-loops), started/stopped with the server
  lifecycle and cancelled by the shutdown coordinator.
- **Settings-gated host URL** — `DeviceConfig.syncHostUrl` (persisted; empty =
  disabled; NO hardcoded endpoints). Transport resolves it per exchange, so a
  settings change takes effect without restart (covered by test).
- **Google ID token retention** — `GoogleOAuth` now exposes the raw ID token
  (process-lifetime, in-memory ONLY — never persisted; cleared on sign-out). The
  transport presents it as the exchange bearer.
- **Engine convergence fix (real defect)** — `SyncEngine.flush` previously returned
  early on an empty queue, meaning a device with no local changes NEVER pulled the
  host's records (breaking "new device inherits history instantly"). Empty-delta
  flushes now still perform the exchange.
- **DeviceConfig race guard (real defect)** — the async init-load could clobber a
  `syncHostUrl` write that fired before hydration completed (any early settings write
  would silently revert). Init now never overwrites a setter-touched flow.
- **Scheduler DI**: `factory` in NetworkModule (engine + transport + providers +
  scope), started in `DeXServer.start`, stopped in `DesktopShutdownCoordinator` step 3a
  (queued deltas survive in DataStore for the next session — offline-first).
- 5 scheduler tests: cadence, disabled idle + queue retention, signed-out idle +
  queue retention, failure backoff, live host-URL change pickup. Transport tests
  re-pinned against the blank-host guard (embedded server via absolute localhost URL).

## What shipped (Work Package 3, 2026-09-01)

- **Sync REST wire law (core/sync)**: `SyncExchangeRequest`/`SyncExchangeResponse` +
  `SyncEndpoints.EXCHANGE` (`/sync/v1/exchange`) defined ONCE in the pure sync module —
  the 032 server consumes the exact definitions, so client and host can never drift.
  Auth: `Authorization: Bearer <live Google ID token>`; tenant = verified googleSub
  (same identity namespace as the trust model — never a Firebase UID).
- **`HttpSyncTransport`** (core/network): Ktor POST exchange, live-token auth, failure
  propagation to the engine (which re-queues — offline-first means a failed exchange
  never loses data). 4 embedded-server tests: DTO round-trip, HTTP error propagation
  with status surfaced, blank-token refusal, empty-delta legality.
- **Feature wiring (`SyncBridge`, core/data)**: single choke point bridging desktop
  domain state into the engine — HISTORY records (metadata only, by construction of
  the model), the own-device DEVICES roster card (published at server start; alias /
  model / platform), and DEVICES tombstones on identity reset. Koin-optional: no
  engine attached means local-only operation, never a startup failure.
- **Call sites**: `TransferHistory.log` now returns the created record (the recorder
  queues the sync mutation without re-reading storage); `TransferHistoryRecorder`
  bridges every history write; `TrustRevocationService.revokeAll` tombstones the old
  fingerprint BEFORE the reset; `DeXServer.start` attaches the engine + publishes
  the roster card, failure-tolerant.
- **Plan 032 amended**: server consumes `core/sync` for the sync wire law.
- Remaining for this plan: flush scheduling (SyncScheduler desktop impl: periodic +
  connectivity-triggered flushes; currently the engine queues but nothing drains it
  until 032's host exists), on-device airplane-mode→reconnect convergence test.

## What shipped (Work Package 2, 2026-09-01)

- **Dependency cycle prevented by design**: `core/sync` is now a pure leaf (protocol +
  coroutines + serialization ONLY — `HashUtils` clock defaults removed; the HLC and
  engine take MANDATORY wall-clock injection). `core/data` depends on `core/sync` and
  hosts the adapter; `core/network` wires DI. Layering: sync (pure) ← data (adapter) ←
  network (composition).
- **`DataStoreSyncStorage`** (core/data): Preferences-DataStore-backed `SyncStorage` —
  one serialized record per `(collection, key)` under `sync.<collection>.<key>` keys,
  HLC state under `sync.hlc_clock`; corrupt entries/clock degrade to absent (the
  repo-wide persistence tolerance rule). 8-test contract suite: round-trips (payload +
  tombstone), per-collection snapshots, purge, clock persistence across adapter
  restarts, corrupt-entry/clock tolerance.
- **DI (NetworkModule)**: `SyncStorage` (adapter over the app's DataStore),
  `HybridLogicalClock` (platform wall clock; persisted HLC state restored
  monotonic-only at graph construction), `SyncEngine` (deviceId = DeviceConfig
  fingerprint — stable across restarts, unique per device).
- Remaining for this plan: Ktor `SyncTransport` adapter (HTTPS exchange; auth + the
  server surface itself land with plan 032), feature wiring (roster/history/settings
  mutate through the engine), airplane-mode→reconnect convergence test on-device.

## What shipped (Work Package 1, 2026-09-01)

- `core/sync` module (jvm desktop target; deps: core/protocol, core/data primitives,
  coroutines, serialization ONLY — transport + storage stay behind ports).
- `HybridLogicalClock` — Kulkarni-style pt.lc clock; monotonic under frozen/backwards
  wall clocks; causally absorbs remote stamps; monotonic restore. 13-test property
  suite incl. the poisoned-skewed-phone scenario (post-receive local writes supersede).
- `HlcTimestamp` — comparable, serializable, canonical "pt.lc" string form; parse
  fails closed on malformed/negative inputs.
- `SyncRecord` — collection/key/hlc/deviceId/payload envelope with deterministic
  `supersedes()` (HLC order, deviceId tiebreak); tombstone = null payload.
  `SyncCollections` locks the three legal surfaces (devices/history/settings).
- `SyncEngine` — offline-first: local writes persist immediately + queue coalesced
  deltas; `flush` exchanges with the host and merges incoming by the same rule;
  failures re-queue; tombstones persist (no resurrection by stale peers) and compact
  after 30d for OWN records only; **PRIVACY LAW mechanically enforced** —
  `FORBIDDEN_PAYLOAD_FIELDS` reject content/credential fields at any nesting depth.
- Ports: `SyncStorage`, `SyncTransport`, `SyncChangeSource`, `SyncScheduler`.
- Remaining for this plan: desktop DataStore adapter (SyncStorage impl + DI),
  Ktor SyncTransport adapter (HTTPS exchange; auth lands with 032), feature wiring
  (roster/history/settings read+mutate through the engine), and the server surface
  itself in 032.


## Why

The ecosystem promise — "new device signs in with the same Google account and instantly
inherits trust, history, settings" — needs an async metadata sync layer. Decision
(2026-08-31 review, user-approved direction): **self-hosted on the existing Hetzner VPS**
rather than Firestore, because (a) there is no official Firebase SDK for JVM/Compose
Desktop (the flagship platform), (b) Firebase Auth UID is a different identity
namespace than the `googleSub` the entire trust model is keyed on, and (c) the VPS is
already paid for and under our control. Push notifications remain on FCM/APNs (free,
best-in-class) — plan 032.

## Scope

1. **Sync server surface** (lives in `server/` with plan 032, this plan defines the
   client contract):
   - Collections: `devices` (roster: fingerprint, alias, deviceType, platform, push
     token), `history` (transfer metadata), `settings` (user preferences).
   - Auth: Google ID Token verification (same issuer as the relay); the account
     subtree is the tenant boundary.
2. **`core/sync` module** (new; commonMain):
   - `SyncEngine`: offline-first — local DataStore is the source of truth; sync is
     asynchronous delta exchange over authenticated HTTPS (SSE or long-poll listeners;
     REST snapshot on first connect). Queue-and-flush on connectivity.
   - **Hybrid Logical Clock (HLC) conflict resolution** — NOT wall-clock LWW. A wrong
     phone clock must never win a conflict. Every synced record carries
     `{deviceId, hlc}`; HLC compare is total and monotonic.
   - Deletion semantics: tombstones with a compaction window (default 30 days) so
     deletes propagate before being garbage-collected.
3. **Privacy law (inviolable)**:
   - File CONTENT and clipboard CONTENT are NEVER synced. Only metadata.
   - Paired-token VALUES are never synced — fingerprint identifiers only (a signed-in
     new device inherits same-account trust via the existing identity-proof flow, not
     by downloading credentials).
4. **Ports**: `SyncTransport` (HTTPS client adapter), `SyncStorage` (per-record
   persistence adapter over DataStore in `core/data`).

## STOP conditions

- A record type not listed above (devices/history/settings) requires a user decision —
  do not add new synced surfaces silently.
- HLC implementation must be property-tested (monotonicity across clock-skew scenarios)
  before any real data flows.
- If server deployment costs exceed the CX22 envelope (memory/disk), STOP and re-plan
  with the user — never "optimize" by dropping tombstones or HLC.
- Desktop must stay fully functional with sync unreachable (offline-first is a
  release gate, not a nice-to-have).

## Verification

```
.\gradlew :core:sync:desktopTest        # HLC property tests + engine suite
.\gradlew :composeApp:desktopTest       # no-regression gate
```
On-device: airplane-mode queue → reconnect → convergence test.
