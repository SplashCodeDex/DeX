# Plan 030 — Android Shared-Core Integration (Phase 0-A5)

> Status: IN PROGRESS (Phase 1: Protocol Wire Unification DONE; Phase 2: Cryptographic Engine Unification DONE)
> Depends on: 024 (DONE)
> Effort: L (2–3 weeks solo)

## Why

The Android app (`DeX/app`) carries a parallel implementation of what now lives in the
shared core: its own `MessageHandler`, `ProtocolKeys` registry (lockstep by convention,
not by compile), discovery, and punch logic. Per the ecosystem architecture, Android
should consume `core/protocol` + `core/domain` (+ the client half of `core/network`)
directly — one brain, native shell. This phase merges the two brains.

## Scope

1. **Targets**: add the `android()` KMP target to `core/protocol`, `core/data`,
   `core/domain` (pure modules; `HashUtils` gets an `androidMain` actual). The client
   halves of `core/network` commonMain (Ktor CIO client, engines, MessageHandler) are
   already common — verify they compile for Android; `desktopMain` (Netty server, JmDns,
   JNA, `RelayService` staging, `DesktopPullService`) stays desktop-only forever.
2. **Wiring**: `DeX/app/build.gradle` consumes `project(":core:...")`; Android
   discovery (NSD/UDP) implements the shared `DiscoveryProbe`/registry feeds; the
   platform engine implements `IPlatformEngine` glue (notifications, clipboard) with
   Android specifics in-app.
3. **Protocol unification, staged file-by-file**:
   - `ProtocolKeys` becomes a thin deprecation shim (`typealias`/constants forwarding to
     `core/protocol`) so each call site migrates in its own commit.
   - Android `MessageHandler` is replaced by the shared one.
   - Lockstep rule ends: after the LAST call site migrates, `ProtocolKeys` deletion is a
     milestone gate requiring an explicit status flip here — never a drive-by cleanup.
4. **Ordering**: migrate the Android app in slices that each keep `:app:assembleDebug`
   green (its own build suite is the gate).

## STOP conditions

- `composeApp` NEVER gains an android target/source set (AGENTS.md law).
- Desktop suites stay green at every intermediate commit — a shared-core regression
  that breaks the flagship is a full stop and revert, not a "fix later".
- The Android app's own user-facing behavior must not regress: pairing, transfers,
  clipboard, file explorer flows all re-verified on-device before DONE.
- `DeX/app`'s `ProtocolKeys.PIN_LENGTH` (5) and the desktop `PairingEngine.PIN_LENGTH`
  (5) must unify into ONE constant — if they disagree at execution time, STOP and
  reconcile with the user; the wire/dialog contract is 5 slots.

## Verification

```
.\gradlew :app:assembleDebug          # Android gate
.\gradlew :core:domain:desktopTest :core:network:desktopTest :composeApp:desktopTest
.\gradlew spotlessCheck
```
