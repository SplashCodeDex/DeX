# Plan 042 — PunchSession Split (Android, plan 024 Phase 4)

> Status: TODO
> Depends on: plan 024 (Phases 1-3 DONE). Effort: L. Risk: HIGH (wire-adjacent).
> Soak testing REQUIRED before DONE (manual, user-driven).

## Why

`DeX/app/.../network/PunchSession.kt` is 511 lines doing four unrelated jobs in one
class (verified structure 2026-09-03):

1. Lifecycle orchestration: `start()` / `stop()`
2. UDP rendezvous: `registerEndpoint()`, `punch(ip, port, isCancelled)` (hole-punching,
   socket acquisition, cancellation)
3. TCP data plane: `receiveLoop()`, `handleIncoming()`, `sendTo()`, `runTransfer()`,
   `streamBytes()`, `enum TransferOutcome { SUCCESS, REJECTED, DROP }`
4. Line-protocol framing: `writeLine()` / `readLine()` (the punch handshake framing)

Any future change (quic fallback tuning, resume logic, protocol hardening) touches all
four concerns. Plan 024 Phase 1 already declared the punch framing wire law: the
line-protocol framing must move VERBATIM (its reject frames were intentionally left as
literals in Phase 1).

## Scope

1. **WP0 audit (gates everything)**: map every field/constant to its concern; confirm
   the punch line-protocol literals; record the concurrency model (which dispatchers,
   which sockets shared). Output appended to this file before moving anything.
2. **Extract verbatim, one extraction per commit**:
   - `PunchLineProtocol` (object): `writeLine` / `readLine` — framing only.
   - `PunchSocketConnector`: `registerEndpoint` + `punch` + `closeQuietly` — rendezvous
     and socket acquisition, injected with `DeviceConfig` values it already uses.
   - `PunchTransferChannel`: `runTransfer` + `streamBytes` + `TransferOutcome` —
     the TCP transfer data path.
   - `PunchSession` remains the coordinator (lifecycle + `receiveLoop` +
     `handleIncoming` + `sendTo`), delegating to the three.
3. DI re-wiring in `AppModule` if constructor shapes change (Koin `viewModelOf` /
   singletons — keep injection manual-friendly).

## STOP conditions

- Zero wire-visible change: punch line-protocol framing bytes, refresh/prune/window
  timers (120s/60s/12s — stay as Duration literals per plan 024 Phase 1 note), UDP
  payload fields. Tests that assert framing must pass unmodified.
- No behavior change: cancellation semantics, socket reuse, `TransferOutcome`
  decisions identical. If a seam turns out entangled, STOP and consult — do not
  reshape behavior to make extraction clean.
- `Archived_Legacy_WPF/` and desktop modules untouched.

## Verification

- `:app:assembleDebug` + `:app:testDebugUnitTest` (incl. `PunchResumeStateTest`,
  `PairingCoordinatorTest`, `MessageHandlerTest`) green after every commit.
- Manual soak (user-driven, REQUIRED): LAN pairing, punch LAN send, punch WAN send,
  batch download, cancel mid-transfer, fail with symmetric NAT (clear error path).
