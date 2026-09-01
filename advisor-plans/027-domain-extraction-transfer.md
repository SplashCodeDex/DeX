# Plan 027 — core/domain Slice 2: Transfer Use-Case Extraction (Phase 0-A2)

> Status: DONE (executed 2026-09-01; follow-ups noted below)
> Depends on: 025 (DONE), 026 (DONE)
> Effort: M (1–2 weeks solo)

## Why

Transfer orchestration is split across four places, all inside `core/network` and all
threaded with Ktor types: `ClientEngine` (send path: prepare-upload offer → upload with
QUIC/HTTP fallback), `DesktopPullService` (receive path: batch download with `.part`
staging + length verification), `MessageHandler.handlePrepareUpload` (inbound offer
dispatch), and `TransferState`/`TransferStateMonitor` (prompt/progress state). Wear and
iOS peers need the session state machine (offer → accept/reject → per-file progress →
resume/done/fail) without any of that. This slice puts the state machine in the domain
and leaves byte-pumping in the network layer.

## Scope

1. **`core/domain/transfer`**:
   - `TransferOffer` — pure domain model (file manifest, sender identity fields,
     session id). Mapped FROM `PrepareUploadRequestDto` at the network boundary; the
     domain never sees a DTO.
   - `TransferSessionState` sealed interface: `PendingOffer` / `Accepted` /
     `InProgress` (per-file byte progress) / `Completed` / `Failed(reason)` /
     `Cancelled`. One active session per peer fingerprint — matching today's
     `DexRequestStore` / `TransferState.pendingPrompts` semantics.
   - `TransferUseCase`: `offerReceived`, `accept`, `reject`, `cancel`,
     `observeProgress`, `observeSessions`.
2. **Ports**:
   - `TransferTransport` — upload/download byte delivery. QUIC/HTTPS/HTTP-fallback
     implementation stays in `core/network` (adapter).
   - `TransferHistoryPort` — persists finished sessions through `TransferHistory`
     (`core/data`) with the exact current entry shape
     (name/size/direction/uri/peerDevice/status).
3. **Rewire**: `MessageHandler.handlePrepareUpload`, `DesktopPullService`, and
   `ClientEngine`'s outbound offer path delegate state transitions to the use case.
   Network layer keeps: connection management, protocol frames, byte streaming.
4. **Tests**: offer/accept/reject/cancel/progress transition suite in
   `core/domain` desktopTest; existing
   `ClientEngineTransferContractTest` semantics preserved (it may move or stay as the
   network-level adapter test).

## STOP conditions

- Wire format unchanged — `core/protocol` golden fixtures untouched.
- `core/domain` gains no Ktor/DataStore/Koin imports (module declares none; a PR adding
  any is rejected).
- Desktop receive path behavior preserved exactly: `.part` staging, atomic rename,
  Content-Length/manifest verification, partial-file deletion on failure
  (`DesktopPullService` invariants).
- `TransferHistory` entry fields unchanged (history UI and sync plan 031 depend on them).
- If a step cannot be done without changing wire behavior, STOP and consult the user.

## Verification

```
.\gradlew :core:domain:desktopTest :core:network:desktopTest :composeApp:desktopTest
.\gradlew spotlessCheck
```

## Definition of done

Both transfer paths (send + receive) driven by domain state machine; all suites green;
plan row flipped DONE with follow-ups noted.

## What actually shipped (2026-09-01)

- `core/domain/transfer`: `TransferSession` (verbatim legacy shape), `TransferFile`,
  `TransferOffer`, `TransferProgress`, `TransferOutcome`, `TransferUseCase` registry
  (register/reportProgress/markComplete/completeSession-with-linger/remove), plus
  history direction/status constants. 9-test contract suite incl. the pinned
  **markComplete-never-auto-removes** rule (desktop callers own entry lifetimes —
  legacy monitor contract).
- `core/network` `TransferStateMonitor` is now a facade over the domain use case —
  public API byte-identical, all call sites compile untouched. ONE process-wide
  instance via Koin (NetworkModule) with getOrNull fallback for partial-DI tests.
- `TransferHistoryRecorder` adapter: the only caller of `TransferHistory.log` in
  `core/network`; outcomes map to status "success"/"failed" via domain constants.
  ShareRoutes (upload success + failure), DesktopPullService (pull success + failure),
  and composeApp DesktopFileSendService (sent success + failed) all funnel through it.
- Repaired a real pre-existing defect surfaced by suite runs: `MessageHandler` used
  unowned `CoroutineScope(Dispatchers.IO).launch` fire-and-forget persistence
  (pair-accepted / unpair / trust-check downgrade) that raced test teardown and
  poisoned the next test class (`UncaughtExceptionsBeforeTest`). Handler now owns a
  supervised injectable scope; tests call `handler.shutdown()` in teardown.
- Verification: `:core:domain:desktopTest`, `:core:network:desktopTest`,
  `:composeApp:desktopTest`, `spotlessCheck` — all green.

## Follow-ups (deliberately NOT in this slice)

- `TransferTransport` port declared in the plan's original design but deferred: both
  desktop byte-pumps (ShareRoutes streaming, DesktopPullService) keep their loops and
  drive the registry — port lands with the first non-desktop peer that needs it (030/033).
- `ClientEngine` offer path still builds DTOs directly; its use-case mapping lands with
  028's registry (shared sender-side session minting).
