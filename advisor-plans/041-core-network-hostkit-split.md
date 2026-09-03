# Plan 041 — core/network Split: Client/Peer Surface vs Embedded Host Surface

> Status: TODO
> Depends on: plan 040 (both plans rewrite the same Gradle edges; 040 first keeps the
> diffs orthogonal). Sequence AFTER plan 032 lands (032 actively edits core/network
> desktopMain: WanRelayClient, routes, platform engine).
> Related: plans 026–029 (extracted the domain state machines; this plan splits what
> remained — the adapter mass).

## Why

After the domain extractions (026–029), `core/network` remains one module hosting two
different deployable concerns:

1. **Client/peer surface** (runs on every peer, desktop + Android):
   `ClientEngine`, `WebSocketEngine`, `MessageHandler`, `DiscoveryEngine`,
   `DeviceManager` + `DeviceManagerPairingGrantStore`, `AuthState`,
   `ClipboardSyncState`, `HardwareTelemetry`, `PlatformUtils`,
   `TransferSpeedCalculator`, the engine interfaces (`IDiscoveryService`,
   `IMirrorEngine`, `IQuicClient`, `IPlatformEngine`), `sync/HttpSyncTransport`,
   `di/NetworkModule`.
2. **Embedded host surface** (the desktop machine being controlled — Ktor SERVER):
   `server/DeXServer`, `server/AccessControl`, `server/ReceiveStorage`,
   `server/routes/` (Clipboard, Control, Device, FileExplorer, Settings, Share,
   wallpaper, WebSocket), `security/CertificateGenerator`, `security/DynamicKeyManager`,
   and the desktop-only services (`DesktopPullService`, `RelayService`,
   `TrustRevocationService`, `FileExplorerService`, `DesktopWallpaperService(+Watcher)`,
   `DesktopUpnpService`, `PublicAddressService`, `LoopbackControlApi`,
   `TransferHistoryRecorder`, `TransferStateMonitor`, `TransferCheckpointRegistry`,
   `JvmMirrorEngine`, `DesktopJmDnsService`, `DesktopUdpService`).

Consequences today: the module pulls BOTH Ktor client and Ktor server engines into one
dependency set; the Android target must carry (and carefully exclude) host-only code;
and `composeApp` reaches straight into host internals (`FileExplorerViewModel` imports
`core.network.server.WebSocketConnectionManager`, `TrustRevocationService` (services)
imports `server.WebSocketConnectionManager`, `ShareRoutes` imports `ReceiveStorage`) —
the seam exists in practice but is not structural, so nothing stops new host↔peer
entanglement.

## Scope

1. **WP0 — classification audit (gates everything)**: for every desktopMain file, record
   host-only vs peer-shared, and every cross-references (who imports whom). Known
   entanglements to resolve: `TrustRevocationService` → `server.WebSocketConnectionManager`;
   `FileExplorerService`/`ExplorerFileEntry` consumed by composeApp UI;
   `RelayService` used by both server routes and client WAN flow. Output: a movement
   table appended to this file before WP1 starts.
2. **New module `core/serverkit`** — JVM-only (`org.jetbrains.kotlin.jvm`, like `server/`).
   Deps: `:core:network` (peer surface), `:core:protocol`, Ktor SERVER, Netty, jmDNS as
   needed. It may NEVER be a dependency of `core/network` — `core/network` commonMain
   must not know it exists.
3. **WP1 — move the embedded host core verbatim**: `DeXServer`, `AccessControl`,
   `ReceiveStorage`, `security/`, `server/routes/` + their tests
   (`AccessControlTest`, `WebSocketConnectionManagerTest`, route tests).
4. **WP2 — move host-only services per the WP0 table** (split-providers, wallpaper,
   UPnP, pull/receive pipeline). Peer-shared services stay in `core/network` behind
   existing interfaces — do NOT invent new ports in this plan.
5. **WP3 — DI rewiring**: split `NetworkModule.jvm` wiring into network (peer) and
   serverkit (host) modules; `composeApp` registers both. UI import-path changes only.
6. **Explicitly untouched**: `core/network` desktopTest's `:server` dependency (relay
   contract tests run against the real server routes — that design stays); `server/`
   module (still protocol+sync only); all wire behavior.

## STOP conditions

- **Move-only**: zero logic changes. Every relocated test passes unmodified except
  import lines. `DeXServer` startup semantics byte-identical (plan 005 regression guard).
- `core/network` (any source set) must never depend on `:core:serverkit`. Enforced by
  review now; add an explicit assertion note to docs/ARCHITECTURE.md when done.
- `:core:serverkit` stays JVM-desktop-only — no Android target, so host code can never
  leak into the Android client build.
- Wire format unchanged; golden fixtures untouched; no new features, no "improvements"
  discovered mid-move (file them as findings instead).
- Desktop must not regress: full `:composeApp:desktopTest` + `:core:network:desktopTest`
  green before each WP merges.

## Verification

- `:core:serverkit:test` — relocated host tests pass.
- `:core:network:desktopTest` (incl. `:server` contract tests), `:composeApp:desktopTest`
  — full suites pass.
- `spotlessCheck` green; manual smoke: LAN pairing + transfer + file explorer +
  wallpaper + clipboard on desktop.

## Next phases

- Android `DeX/app` stops carrying host-surface exclusions once `core/serverkit` exists
  (fold into the plan 030 follow-up wave).
- If WP0 shows `server/` and `core/serverkit` duplicating route plumbing, file a finding
  for a shared server-support slice — do NOT merge the modules (server must stay
  deployable without desktop deps).
