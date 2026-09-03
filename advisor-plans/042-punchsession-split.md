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

## WP0 audit (completed 2026-09-03, execution record)

- **Field→concern map**: `pinnedTrustManager` + `sslContext` (lazy) were used ONLY by
  `registerEndpoint` (TLS endpoint reflection to the PC) → moved into
  `PunchSocketConnector`. `serverSocket` is mutated by lifecycle (start/stop) → the
  connector observes it via `serverSocketProvider`; cancellation parity via
  `isActive` provider; foreign inbound connections re-launched by PunchSession's own
  scope via `onForeignConnection` (identical cancellation semantics — the old
  `scope.launch { handleIncoming(accepted) }`).
- **Consumer map**: `AppModule.kt` (`single { PunchSession(get(), get(), get(),
  androidContext()) }`), `DexService.kt` (`start()`/`stop()`), `PunchSendWorker.kt`
  (`sendTo`). Public surface unchanged: `start() / stop() / sendTo(...)`.
- **Wire law verified**: line-protocol framing bytes identical (`\n`-terminated UTF-8,
  64 KiB hostile-line drop); punch timers (12s deadline, 800ms connect, 250ms retry,
  120s register refresh, 60s prune, 10s/30s/60s read timeouts) preserved verbatim;
  64 KiB stream buffer unchanged.
- **Extractions executed**: `PunchLineProtocol` (framing), `PunchSocketConnector`
  (registerEndpoint + punch + closeQuietly + SSL/pinning), `PunchTransferChannel`
  (TransferOutcome + runTransfer + streamBytes). `PunchSession` = lifecycle +
  receiver + sendTo orchestration (511 → 322 lines).
- **Build gate**: `:app:assembleDebug` + `:app:testDebugUnitTest` GREEN
  (incl. PunchResumeStateTest, PairingCoordinatorTest, MessageHandlerTest,
  TransferEdgeCasesTestSuite).
- **Remaining for DONE**: user-driven manual soak (LAN pairing, punch LAN/WAN send,
  batch download, cancel mid-transfer, symmetric-NAT error path).

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
