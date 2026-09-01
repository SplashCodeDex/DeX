# Plan 033 — iOS Foundation: KMP Framework + Xcode Project (Phase 1A)

> Status: TODO — GATED: requires a Mac (Xcode) — purchase is a user decision
> Depends on: 027–029 (domain slices; the layers iOS consumes)
> Effort: M (~2 weeks solo, Mac required)

## Why

`core/*` modules compile for JVM only today. iOS needs them as a Swift-consumable
framework. This phase adds Apple targets and the Xcode project skeleton WITHOUT any app
UI — the goal is a green pipeline: Kotlin compiles for `iosArm64`/`iosSimulatorArm64`,
SPM export works, a Swift test file calls the domain engine from Xcode.

## Scope

1. **Apple targets on `core/protocol`, `core/data`, `core/domain`** (pure modules):
   `iosArm64` + `iosSimulatorArm64`. `HashUtils`/time actuals via platform APIs
   (Kotlin/Native `crypto`/`platform.CoreFoundation` or a tiny expect/actual — verify
   current recommended practice via stale-knowledge protocol FIRST).
   `core/network` client halves are assessed for iOS CIO/URLSession compatibility;
   whatever does not port cleanly stays desktop-only and iOS talks to the domain +
   its own URLSession transport adapter. NO watchos targets yet (plan 039 gate).
2. **SPM export**: `api()`-level framework export per current KMP practice (XCFramework
   or SPM direct consumption — verify the current recommended path; KMP tooling moves
   fast, research before choosing).
3. **`iosApp/` Xcode project**: app + test targets, the framework wired, a smoke test
   (e.g. drive `PairingEngine` state machine from Swift; assert PIN format/TTL).
4. **CI**: macOS runner builds the framework + runs iOS sim tests (repo is private —
   watch the macOS-minutes budget; local-Mac builds are the fallback).
5. **Signing/distribution scaffolding**: bundle ID, team, none of the store work yet.

## STOP conditions

- `composeApp` untouched — zero desktop changes allowed in this phase (flagship
  stability law).
- No Swift business logic — the smoke test exercises KMP code; if Swift "needs" logic,
  that logic belongs in `core/domain`.
- If Kotlin/Native memory/concurrency model forces `core/domain` design changes, STOP
  and document before adjusting — domain semantics are contract law (026).
- Apple Developer Program enrollment ($99/yr) is a user decision — flag it, do not
  assume.

## Verification

```
.\gradlew :core:domain:iosSimulatorArm64Test   # or the current KMP iOS test path
.\gradlew :core:protocol:iosSimulatorArm64Test
# Xcode: iosApp tests green on iOS simulator
```
