# Plan 029 — core/domain Slice 4: Clipboard Sync + AuthState Disposition (Phase 0-A4)

> Status: DONE (executed 2026-09-01)
> Depends on: 026 (DONE); sequenced after 028 (DONE)
> Effort: S (~1 week solo)

## What actually shipped (2026-09-01)

- **`core/domain/clipboard`**: `ClipboardSyncUseCase` — the echo guard (content-hash
  based: received content is written locally AND marked, so the platform's subsequent
  change event is suppressed and the classic two-device infinite copy loop is killed),
  the enable policy (injected value source), payload shaping (Text XOR Image lanes),
  and send-failure propagation (callers own fallback channels). `ClipboardAccess` +
  `ClipboardSender` ports. **No sync dependency by design** — clipboard CONTENT is
  real-time P2P only, never enters the sync backend (privacy law restated in the code).
- **8-test contract suite**: push-when-changed, disabled no-op, empty no-op,
  receive-then-write-then-NOT-rebroadcast, new-local-copy-after-remote still sends,
  guard is per-content (idempotent), image lanes, unreachable-peer failure surfaces.
- **Desktop wiring**: ONE process-wide instance (NetworkModule.jvm) — AWT `ClipboardAccess`
  + WS-broadcast `ClipboardSender` (`DesktopClipboardPorts`, core/network desktopMain) +
  DeviceConfig enable policy — eagerly resolved in DeXServer.start so the guard exists
  before any frame arrives. The server receive path (`WebSocketRoutes` set-clipboard)
  routes through `ClipboardSyncState.applyRemoteText` (same shared instance).
  `ClipboardSyncService` (composeApp) is reduced to AWT flavor-listener plumbing; the
  ADB fallback lives in `DesktopClipboardSender` (WS-first, ADB when no session).
- **AuthState disposition — DECIDED (recorded for plan 030)**: `AuthState` STAYS in
  `core/network`. It is the desktop/phone mirror of DeviceManager persistence
  (StateFlow projections consumed by engines and UI), not domain logic; moving it would
  churn every consumer for zero platform gain. Plan 030 (Android shared-core) is the
  moment its fate is revisited: the Android app has its own DeviceManager-backed copy,
  and THAT integration decides whether a shared mirror belongs in core/data. Moving it
  twice would be worse than moving once, late, with full information.
- Verification: `:core:domain:desktopTest` (50), `:core:network:desktopTest` (134),
  `:composeApp:desktopTest`, `spotlessCheck` — all green.

## Why

Two shared-state pieces remain outside domain control: `ClipboardSyncState`
(echo-dedup for clipboard sync, `core/network` commonMain) and `AuthState`
(the DeviceManager → StateFlow mirror consumed by engines and UI). The clipboard
use case is a prerequisite for any peer implementing sync; AuthState's final home
must be decided before the Android integration (030) locks consumers in.

## Scope

1. **`core/domain/clipboard`**:
   - `ClipboardSyncUseCase`: local-origin echo guard (remember what WE pushed so an
     echoed copy does not re-broadcast), text + image payload handling via
     `FieldNames.TEXT` / `FieldNames.IMAGE_BASE64`, enable/disable policy.
   - Port: `ClipboardAccess` (platform read/write — AWT on desktop, system service on
     Android, UIPasteboard on iOS). The ADB fallback path in composeApp
     (`ClipboardSyncService`) stays desktop wiring, now calling the use case.
   - Wire both directions through the use case: `WebSocketRoutes` `set-clipboard`
     handler (server side) and `MessageHandler.handleSetClipboard` (client side).
2. **AuthState disposition — DECISION POINT (present to user at gate)**:
   - (a) Move `AuthState` to `core/data` as the canonical shared state holder.
   - (b) Keep in `core/network` as the desktop mirror, document it as such.
   - Default recommendation: (b) until 030 proves which peer side consumes it; moving
     twice is worse than moving once late.

## STOP conditions

- **Clipboard CONTENT never enters the sync backend (031) — hard rule, restated here
  because this slice is where it could first leak.** Sync carries metadata only.
- Echo-guard semantics preserved: a remotely-received text must not re-broadcast
  (existing `emitReceived` → `updateHashFromRemote` contract).
- Empty/blank clipboard payloads are still ignored (current behavior).
- No Ktor/DataStore/Koin in `core/domain` (the `ClipboardAccess` port pattern keeps
  AWT out of it).

## Verification

```
.\gradlew :core:domain:desktopTest :core:network:desktopTest :composeApp:desktopTest
.\gradlew spotlessCheck
```
