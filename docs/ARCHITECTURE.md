# DeX Desktop — Architecture Reference

> Source of truth for AI assistants and contributors. Every fact here was verified against
> the live codebase. If code and this document disagree, **the code wins** — update this file.

## Module graph

```
composeApp (desktop app entry; jvm("desktop") target)
  ├─ feature/discovery    (device list UI)
  ├─ feature/history      (currently UNHOOKED from settings.gradle.kts — see CHANGELOG 10.1.10.0)
  ├─ feature/settings     (settings UI)
  ├─ core/designsystem    (theme, glass components, icons)
  ├─ core/network         (engines, Ktor server, protocol DTOs, auth)
  └─ core/data            (DataStore persistence, DeviceConfig, TransferHistory)
```

- `core/*` and `feature/*` are Kotlin Multiplatform libraries with a single JVM target named
  **`desktop`** (`jvm("desktop")`). Compiled source sets are therefore ONLY:
  `src/commonMain`, `src/desktopMain`, `src/desktopTest`.
  Never create `jvmMain` or any other source root in these modules — it will silently never compile.
- Test tasks: `:module:desktopTest`. Main jar: `:composeApp:desktopJar`.

## Network surface

| Port | Protocol | Binding | Purpose |
|------|----------|---------|---------|
| 48424 | HTTPS + WSS (`/ws`) | 0.0.0.0 | Primary: discovery info, LocalSend v2 transfers, WebSocket control channel |
| 48423 | QUIC | — | High-speed transfer streams (`DeXPorts.QUIC`) |
| 48426 | HTTP | 0.0.0.0 | TCP fallback pull/downloads (`DeXPorts.PULL`) |
| 28425 | HTTP | 127.0.0.1 | Loopback-only control endpoints |

Server wiring lives in `core/network/src/desktopMain/.../server/DeXServer.kt`; route groups under
`server/routes/` (device, share, control, webSocket, fileExplorer, clipboard, settings).

## Trust model (strict priority — highest wins)

A peer's bearer token is resolved in this order (see `ClientEngine.authToken`,
`WebSocketEngine.connectToPC`):

1. **googleSub** — Google account subject; same signed-in account on both sides = instant mutual trust.
2. **identityHash** — SHA-256 of the verified email (only when logged in).
3. **paired token** — persisted per-fingerprint token from a completed PIN pairing.

Same-account connections auto-persist trust on connect. Trust can be *downgraded* remotely:
`trust-check` (peer reports we are not trusted) and `unpair` remove local entries via
`DeviceManager.removePairedFingerprint`.

Persistence: `DeviceManager` (DataStore) → `paired_fingerprints` (set), `paired_tokens`
(`TokenCodec` map), `paired_times` (first-pair epoch, never overwritten). Mirrored into
`com.dexstudios.dex.auth.AuthState` StateFlows.

## Pairing

State machine owner: `com.dexstudios.dex.auth.PairingEngine` (single offer at a time, last-wins;
a superseded peer can never gain trust because PIN proof is fingerprint-bound and TTL-expired).

```
Idle ──initiatePairing()──────────────> QrPhase(expiresAt = now+60s)
Idle ──handleInboundPairingRequest()──> PinPhase(pin, expiresAt = now+60s)
PinPhase ──acceptInboundPairing(isOneTime)──> Success   (persists unless guest)
PinPhase ──rejectInboundPairing()───────────> Idle
Qr/Pin ──handlePairResponse(true)───────────> Success  (ignored if already resolved)
any PinPhase ──TTL elapsed──────────────────> Error("Pairing timed out")
```

Security invariants (do not weaken):
- A `pair-response accepted=true` is honored ONLY when the responder echoes the displayed PIN
  (`verifyInboundPin`). Bare assertions are rejected; the desktop user may still accept manually.
- PIN offers expire after 60 s (`PIN_TTL_MS`); expired PINs never verify.
- Already-paired auto-accept (re-pair after partial forget) intentionally stays pinless and
  therefore requires explicit desktop-side Accept.

Full wire contract: see [PROTOCOL.md](PROTOCOL.md).

## Transfer paths (sender perspective)

1. **LAN primary**: LocalSend v2 `prepare-upload` (HTTPS 48424) → per-file upload (QUIC 48423
   preferred, HTTP fallback). Auth via bearer token (see trust model).
2. **NAT punch**: same-account roster devices without LAN reachability — UDP endpoint exchange
   (`/punch/endpoint`, `endpoint-info`, `peer-endpoint`) then direct `PunchSession`.
3. **Relay fallback**: PC relays via WebSocket `prepare-upload` push when punch fails
   (`relay-started`/`relay-error` acks).

Received files persist under `~/Downloads/DeX` via `core/network/.../server/ReceiveStorage`
(single source of truth — do not hardcode the path elsewhere).

## Desktop shell

Entry: `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt` → floating dock card driven by
`window/DockedWindowStateController` (placement kinematics, magnetic snapping, focus-loss guard,
drag-release detection via `platform/GlobalMouseButtonHook` WH_MOUSE_LL on Windows with polling
fallback on macOS).

## Canonical verification commands

```
.\gradlew spotlessCheck                 # formatting gate
.\gradlew :composeApp:desktopTest       # main test suite (also runs pre-commit)
.\gradlew :composeApp:desktopJar        # produce runnable jar
.\gradlew :core:network:desktopTest     # network engine suite
```
