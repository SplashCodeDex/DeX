# Plan 029 — core/domain Slice 4: Clipboard Sync + AuthState Disposition (Phase 0-A4)

> Status: TODO
> Depends on: 026 (DONE); sequence after 028
> Effort: S (~1 week solo)

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
