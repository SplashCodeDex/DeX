# DeX — Feature Reference

> Source of truth for the product's feature surface. Every fact here was verified against the
> live codebase (master @ `60c306e`, 2026-08-28). Companions to this file:
> [ARCHITECTURE.md](docs/ARCHITECTURE.md) (module graph, ports, trust model, pairing state
> machine) and [PROTOCOL.md](docs/PROTOCOL.md) (WebSocket wire contract). If code and this
> document disagree, **the code wins** — update this file.

## Product scope

| Component | Location | Status |
|-----------|----------|--------|
| DeX Desktop (Compose Multiplatform, Windows + macOS) | `composeApp/`, `core/*` | Active — the migration target |
| DeX Android app | `DeX/` | Active — NOT part of the desktop migration |
| DeXShareTarget (C#/.NET Windows companion) | `DeXShareTarget/` | Active |
| Legacy WPF/C#/PowerShell implementation | `Archived_Legacy_WPF/` | Retired, read-only archive |

## Network surface

| Port | Protocol | Binding | Purpose |
|------|----------|---------|---------|
| 48424 | HTTPS + WSS (`/ws`) | 0.0.0.0 | Primary: discovery info, LocalSend v2 transfers, WebSocket control channel |
| 48423 | QUIC | — | High-speed transfer streams (`DeXPorts.QUIC`) |
| 48426 | HTTP | 0.0.0.0 | TCP fallback pull — downloads only, by deliberate security design (`DeXPorts.PULL`) |
| 48425 | HTTP | 127.0.0.1 | Loopback-only: Google OAuth browser-redirect listener + control plane (`DeXPorts.OAUTH_CALLBACK`) |
| 28425 | HTTP | 127.0.0.1 | Loopback-only control endpoints (settings/sign-in triggers from the UI process) |

Server wiring: `core/network/src/desktopMain/.../server/DeXServer.kt`; route groups under
`server/routes/` (device, share, control, webSocket, fileExplorer, clipboard, settings).

---

## 1. Device discovery

- **UDP multicast beacon** — periodic discovery announcements on the LAN.
- **mDNS / Bonjour** — `DesktopJmDnsService` (jmDNS) resolves peers and advertises `quicPort`.
- **LocalSend v2 compatibility** — `GET /api/localsend/v2/info` and `POST /api/localsend/v2/register`
  (`DeviceRoutes.kt`); beacons and info responses always send `identityHash=null, googleSub=null`
  (identity material is never advertised).
- **WAN roster devices** — same-account devices discovered via the account roster rather than the
  LAN (`DiscoveredDevice.viaWan` / `viaRoster` flags), enabling cross-network awareness.
- **Last-PC memory** — `PcMemory` (DataStore) persists the last PC's fingerprint, IP, HTTP port and
  QUIC port for fast reconnect.

## 2. Trust model

Strict priority, highest wins (owner: `ClientEngine.authToken`, `WebSocketEngine.connectToPC`):

1. **googleSub** — same signed-in Google account on both sides = instant mutual trust.
2. **identityHash** — SHA-256 of the verified email. DEPRECATED tier, retained for legacy peers only.
3. **Paired token** — per-fingerprint token minted by a completed PIN pairing.

- Same-account trust for peers without a bearer is established live via the
  `identity-challenge` / `identity-proof` HMAC-SHA256 nonce exchange on `/ws`; the match upgrades
  only that session and is constant-time.
- Trust can be downgraded remotely: `trust-check`, `unpair`, and the UI's "Forget device" /
  "Reset Identity & Trust" actions (all route through `DeviceManager.removePairedFingerprint`).
  Reset additionally rotates the identity hash (`DeviceConfig.resetIdentity`).
- Persistence: `DeviceManager` (DataStore) → `paired_fingerprints`, `paired_tokens` (`TokenCodec`),
  `paired_times` (first-pair epoch, never overwritten). Desktop startup hydrates via
  `DeviceManager.init(dataStore)` in `main.kt` BEFORE `DeXServer.start()`.

## 3. Pairing

State machine owner: `com.dexstudios.dex.auth.PairingEngine` (single offer at a time, last-wins).

- **QR pairing** — desktop shows a QR payload (`QrPayloadGenerator`); phone scans and pairs.
- **Inbound PIN pairing** — `InboundPairingDialog` / `PinPairingPanel` with a 60-second TTL in both
  phases (`PIN_TTL_MS`); expired PINs never verify.
- **PIN proof invariant** — a `pair-response accepted=true` is honored ONLY when the responder
  echoes the displayed PIN (`verifyInboundPairing`); bare assertions are rejected, but the desktop
  user may still accept manually.
- **Token minting** — a proven pairing mints a fresh per-device token, stores it server-side, and
  delivers it via `pair-accepted{token, fingerprint}` so BOTH sides can re-authenticate later.
- Already-paired auto-accept (re-pair after partial forget) stays pinless and therefore requires
  explicit desktop-side Accept.
- **Punch rendezvous security** — `GET /punch/endpoint` registration is accepted only from the
  registered fingerprint's own TRUSTED session; `resolve-endpoint` is answered only to trusted
  callers (prevents rendezvous-table poisoning, 5-minute entry TTL).

## 4. File transfer (receiving and sending paths)

Sender-perspective paths, in priority order:

1. **LAN primary** — LocalSend v2 `prepare-upload` (HTTPS 48424) → per-file upload, QUIC 48423
   preferred with HTTP fallback. Auth via bearer token from the trust model.
2. **NAT punch** — same-account roster devices without LAN reachability: UDP endpoint exchange
   (`/punch/endpoint`, `endpoint-info`, `peer-endpoint`) then a direct `PunchSession`.
3. **Relay fallback** — PC relays via WebSocket `prepare-upload` push when punch fails
   (`relay-started` / `relay-error` acks) through `RelayService`.

Additional transfer features:

- **Session cancellation** — `POST /api/localsend/v2/cancel` with owner-token authorization; only
  the sender that prepared the session may cancel it (`ControlRoutes.kt`).
- **Receive storage** — incoming files persist under `~/Downloads/DeX` via `ReceiveStorage`
  (single source of truth for the path).
- **UPnP port mapping** — `DesktopUpnpService`; always on by design, no settings surface.
- **Public address resolution** — `PublicAddressService` for WAN reachability advertisement.
- **Progress state** — `TransferStateMonitor` / `TransferState` feed the Active Transfer Dashboard
  and `PullProgressDock`.

## 5. Desktop sending, drag-and-drop

- **`DesktopFileSendService.sendFiles()`** — transmits files to a connected device.
- **Device column drop target** — external files dragged from Windows Explorer / macOS Finder onto
  the Device section (hover lift with accent border, immediate send on drop, warning toast when no
  device is connected). While dragging, the expanded History canvas blurs with a centered
  "Drop on a device to send" badge.
- **Native OS drag-out** — `dragAndDropSource` with `FileListTransferable` (`javaFileListFlavor` +
  `stringFlavor`); dragging history files out of DeX drops real files into Explorer / Finder /
  external apps.

## 6. Transfer history

- Grid view with thumbnails, skeleton loading states, `Open Downloads Folder`, refresh (F5,
  re-scans the directory and clears thumbnail caches).
- **Multi-selection engine**:
  - Single click — focus and highlight one card.
  - Ctrl/Cmd + click — toggle individual items in/out of the selection.
  - Shift + click — contiguous range from the active anchor item.
  - Rubberband marquee drag on the empty canvas (Ctrl/Cmd unions with previous selection).
  - Keyboard: Ctrl/Cmd + A select all, Escape or empty-canvas click clears, Delete/Backspace opens
    a count-guarded confirmation dialog.
- **Context menus** — item menu with batch-adaptive titles (`Open (N items)`, `Send N items to
  Phone`, `Delete (N items)`); canvas menu with `Open Downloads Folder`, `Refresh Listing` (F5),
  `Clear All History` (destructive, confirmed).
- **Deletion semantics** — removes DataStore records only; physical files stay on disk.

## 7. Remote file explorer (phone browsing the PC, over WebSocket)

- `list-shared-folders` / `browse-folder` / `pull-files` / `grant-shared-folder`
  (`FileExplorerRoutes` + `FileExplorerService`), request-scoped replies carrying `requestId`
  resolved via `DexRequestStore`.
- UI: `FileExplorerPanel` / `FileExplorerViewModel` with skeleton placeholders and grid items.
- **Reverse pull** — the desktop can request the phone to push specified files back via standard
  LocalSend upload; progress surfaces in `PullProgressDock`.

## 8. Screen mirroring

- `mirror-start` / `mirror-stop` over the WebSocket; binary frames follow on the same socket.
- `MirrorWindow` — portrait/landscape rotation toggle; incoming frames are decoded off the UI
  thread (a malformed frame keeps the last good bitmap instead of crashing composition).

## 9. Clipboard sync

- `set-clipboard` WebSocket push; the receiver remembers its own last push to avoid echo loops.
- Desktop-side native interop via `ClipboardSyncService` (JNA).

## 10. Google account (desktop)

- **Browser OAuth sign-in** — Google sign-in with the redirect routed through the app's loopback
  listener on port 48425; the verified email becomes the desktop's identity (identity hash).
- **`LoopbackControlApi`** — one lazy in-process HTTP client for sign-in / email / sign-out calls
  against the app's own loopback control plane (28425).
- **Profile header** — renders the real Google avatar (fetched and decoded off the UI thread) with
  a bundled placeholder fallback; honest signed-out state, no fabricated fallbacks.

## 11. Desktop shell and UX

- **Floating dock card** — `DockedWindowStateController` with placement kinematics, magnetic
  snapping, focus-loss guard, and drag-release detection via the low-level `WH_MOUSE_LL` mouse hook
  (`GlobalMouseButtonHook`) on Windows with a polling fallback on macOS.
- **Wiggle-to-open** — `WiggleToOpenService`: shake the mouse while holding a button to re-show the
  dock (reversal-threshold based, false-trigger resistant).
- **Global shortcut** — `Win+Shift+D` toggles the dock card (`GlobalShortcutService`,
  `WH_KEYBOARD_LL` hook, single-hook guard, Windows only).
- **Corner overlay host** — `CornerOverlayHost` + `OverlayManager`: notification banners, message
  toasts, transfer overlays, overlay sound service (`OverlaySoundService`), with Do-Not-Disturb
  semantics (DND suppresses; full silence means closing the app).
- **In-app panels** — Main Menu Column, Top Actions Panel, Quick Action Bar, Device List Panel
  (animated connect sequence), Device Status Panel, Bottom Dock Panel, drag pill handle.
- **Inbound pairing dialog** — AirDrop-style modal alert with Decline/Accept pills.
- **Desktop shutdown coordination** — `DesktopShutdownCoordinator` sequences teardown with a
  bounded pending DataStore flush.
- **Overlay testing playground** — internal dev surface for overlay previews.

## 12. Settings panel

1. **Connection** — Do Not Disturb switch, UPnP port forwarding toggle.
2. **Dev Tools** — ADB connect and auto-connect hotspot.
3. **Account** — Google sign-in state, profile, sign-out.
4. **Appearance** — theme: System / Dark / Light.
5. **Interaction** — Wiggle-to-Open toggle plus a read-only global shortcut reference card.

## 13. Design system (`core/designsystem`)

- Theme re-based 1:1 onto the Android app's palette (seven seeds per mode; monochrome accent;
  dark surfaceContainer ladder #111318 / #1E1E20 / #2F3033).
- Frosted-glass surfaces (`frostedSurface` with directional shiny glare rim), `bubbleFluidity`
  physics, glass overlay components (alert dialogs, confirmation popups, stacked screens,
  notification stack, message toasts).
- Ambient smoke haze background (`AmbientSmokeBackground`, static composition ported from Android).
- Fluent icon set (generated from `scripts/fluent_icons.json`) and Lottie device-connected
  animations (`device_connected.json`, `watch_connected.json`).

## 14. Persistence (`core/data`, DataStore)

- `DeviceConfig` — identity (fingerprint, email, googleSub, identity hash rotation), DND state.
- `DeviceManager` — paired fingerprints, tokens (`TokenCodec`), first-pair times.
- `TransferHistory` — transfer records.
- `PcMemory` — last PC fingerprint/IP/ports.
- `WallpaperState` — shared wallpaper revision state. NOTE: no desktop-side consumer is wired yet;
  the live wallpaper watcher/apply services live in the C# `DeXShareTarget/` companion.
- `PunchState`, `TransferState`, `ProtocolDto`, `DeXPorts`, `HashUtils`.

## 15. Platform layer (`composeApp/.../platform`)

- `DesktopEnvironment` — centralized OS detection (Windows / macOS).
- `ScreenBoundsHelper`, `DisplayCoordinateSpace`, `DockCardMetrics`, `TaskbarWorkAreaProvider` —
  multi-display geometry and dock placement.
- `GlobalMouseButtonHook` (WH_MOUSE_LL), `MouseInputProvider`, `ShiftKeyState` — native input.
- `KermitSlf4jBridge` — logging (Kermit over slf4j).
- `AdbManager` — resolves adb from PATH, else downloads and extracts Google platform-tools
  (Windows/macOS), cached for the process lifetime.

## 16. Security hardening (as implemented)

- TLS certificate generation for the HTTPS listener (`core/network/.../security`).
- Punch rendezvous restricted to trusted sessions; constant-time token and nonce comparisons.
- Loopback-only control plane: `/local/settings/*` and sign-in triggers are served only on
  `127.0.0.1` (28425/48425), never on the LAN-facing listener.
- Plaintext HTTP fallback (48426) serves downloads only — never the full application surface.
- Session maps bounded against unbounded-growth DoS (advisor-plan hardening, all DONE).

## 17. Companion projects (not part of the desktop codebase)

- **DeX Android app** (`DeX/app`) — LocalSend-compatible receiver, discovery (NSD + UDP multicast),
  WebSocket client, background upload/download workers, NAT punch send worker, screen-mirroring
  capture (media projection), Google Credential Manager sign-in, wallpaper preview, share-target
  activity, onboarding and permission flows.
- **DeXShareTarget** (C#/.NET, Windows) — Windows share-target integration, wallpaper watcher /
  wallpaper service, Google OAuth through its Kestrel localhost API. A second, retired copy lives
  in the read-only `Archived_Legacy_WPF/` archive.

## 18. Engineering

- Kotlin Multiplatform modules with a single JVM target named `desktop` (`jvm("desktop")`);
  source sets are only `commonMain`, `desktopMain`, `desktopTest`.
- Quality gates: `spotlessCheck` formatting, desktop test suites (`:composeApp:desktopTest`,
  `:core:network:desktopTest`), pre-commit hooks (`.githooks`).
- Packaging: MSIX build + signing pipeline for Windows (`composeApp/packaging/windows/`).
- Process: `advisor-plans/` ledger for risky changes, `docs/` references, `CHANGELOG.md` entries
  with `[fix]` / `[minor]` / `[major]` tags.

## Canonical verification commands

```
.\gradlew spotlessCheck                 # formatting gate
.\gradlew :composeApp:desktopTest       # main test suite (also runs pre-commit)
.\gradlew :composeApp:desktopJar        # produce runnable jar
.\gradlew :core:network:desktopTest     # network engine suite
```

