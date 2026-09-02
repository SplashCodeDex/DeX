# Plan 028 — core/domain Slice 3: Discovery & Roster Registry (Phase 0-A3)

> Status: DONE (executed 2026-09-01)
> Depends on: 026 (DONE); sequenced after 027 (DONE)
> Effort: M (~1 week solo)

## What actually shipped (2026-09-01)

- **`core/domain/discovery`**: `DeviceRegistry` (observed-device state machine), domain
  `DiscoveredDeviceInfo` + `ObservedDevice` models, `KnownPeerPersistence` +
  `DiscoveryProbe` ports. Semantics migrated EXACTLY from the live DiscoveryEngine
  (verified against source, not memory): fingerprint-keyed replace-on-change
  (ip/info/viaWan/viaRoster), 100-entry cap with oldest-lastSeen eviction,
  20s-freshness/10s-sweep TTLs, telemetry merge (battery/isCharging/wifiSsid +
  lastSeen refresh), roster upsert lane (viaRoster=true, NO TTL — roster entries are
  not beacon-driven), blank-fingerprint rejection. 12-test contract suite.
- **`DiscoveryEngine` is now the desktop adapter**: DTO↔domain mapping at the boundary,
  registry owns state, adapter owns platform feeds (JmDNS/UDP beacons, identity
  re-advertisement watch, dual-port UDP + HTTPS/HTTP manual probe — all unchanged).
  The legacy wire-typed `devices` flow is bridged with SYNCHRONOUS write-through so
  every existing consumer (UI, engines, routes) compiles and behaves identically —
  the full legacy DiscoveryEngineTest (disclosure rule, cache, probe parsing) passes
  unmodified against the registry-backed adapter.
- **Deliberately deferred (recorded, not forgotten)**:
  - `PcMemory` ↔ `KnownPeerPersistence` formal wiring — PcMemory already IS the desktop
    implementation; the port earns its keep at the first non-desktop consumer (030/033).
  - Client-side `PunchState.devices` roster merge through the registry — that state is
    consumed by the ANDROID app (DeX/app), so it lands with plan 030's integration,
    not as a desktop-side change.
- Verification: `:core:domain:desktopTest` (42), `:core:network:desktopTest` (134),
  `:composeApp:desktopTest`, `spotlessCheck` — all green.

## Why

`DiscoveryEngine` (`core/network` commonMain) owns the device cache, freshness/expiry
policy, telemetry updates, and LAN/WAN merge — platform-neutral policy entangled with
the Ktor HTTP probe. The same-account roster merge lives in `MessageHandler
.handleDeviceRoster` + `PunchState.devices` (`core/data`). Future peers need roster +
registry semantics; the probe itself is per-platform (JmDns/UDP desktop, NSD Android,
NWBrowser iOS).

## STOP conditions

## Scope

1. **`core/domain/discovery`**:
   - `DeviceRegistry` use case: observed-device state (add / refresh / expire), the
     freshness policy, the DoS caps, telemetry updates (battery / isCharging / ssid),
     and the roster merge (`viaRoster` / `viaWan` flags preserved verbatim).
   - Domain-owned `DiscoveredDeviceInfo` (alias, fingerprint, deviceType, ports, flags)
     — mapped from `RegisterDto` at the network boundary.
2. **Ports**:
   - `DiscoveryProbe` — the HTTP `info` fetch stays in `core/network` (Ktor).
   - `KnownPeerPersistence` — the `PcMemory` (last PC) read/write stays behind the port.
3. **Rewire**: `DiscoveryEngine` becomes the desktop adapter (probe + mDNS/UDP feeds →
   registry); `MessageHandler.handleDeviceRoster` and `WebSocketRoutes
   .handleDeviceRosterRequest` push/pull through the registry; `PunchState.devices`
   becomes a view over registry state.
4. **Tests**: registry expiry, roster merge, telemetry update, and cap semantics in
   `core/domain` desktopTest.

## STOP conditions

- The identity-disclosure rule is inviolable: our advertisements always carry
  `identityHash=null, googleSub=null` (docs/PROTOCOL.md). Any change touching advertised
  payloads must re-read the disclosure rule first.
- Freshness/TTL/cap numbers are migrated EXACTLY as found in code at execution time —
  read them from the live `DiscoveryEngine`; do not invent or "round" values.
- Roster membership is derived ONLY from proven session identity (existing rule);
  the domain must not introduce a client-claim path.
- No Ktor/DataStore/Koin in `core/domain`.

## Verification

```
.\gradlew :core:domain:desktopTest :core:network:desktopTest :composeApp:desktopTest
.\gradlew spotlessCheck
```
