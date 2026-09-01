# Plan 028 — core/domain Slice 3: Discovery & Roster Registry (Phase 0-A3)

> Status: TODO
> Depends on: 026 (DONE); sequence after 027
> Effort: M (~1 week solo)

## Why

`DiscoveryEngine` (`core/network` commonMain) owns the device cache, freshness/expiry
policy, telemetry updates, and LAN/WAN merge — platform-neutral policy entangled with
the Ktor HTTP probe. The same-account roster merge lives in `MessageHandler
.handleDeviceRoster` + `PunchState.devices` (`core/data`). Future peers need roster +
registry semantics; the probe itself is per-platform (JmDns/UDP desktop, NSD Android,
NWBrowser iOS).

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
