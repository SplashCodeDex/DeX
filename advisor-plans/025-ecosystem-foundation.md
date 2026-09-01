# Plan 025 — DeX Ecosystem Foundation: Shared Wire Contract

> Status: IN PROGRESS (Phase 0 "core/protocol" executed 2026-09-01)
> Origin: user-approved ecosystem direction (desktop + phone + tablet + watch + server),
> "Headless Core + Native Shells" architecture. This plan is the phase gate for every
> follow-on module (core/domain, core/sync, server/, wearApp/, iosApp/).

## Why

The wire contract (`{type, data}` envelope, message types, payload field names) was
restated as raw string literals at ~148 call sites across the desktop build, and
duplicated as a parallel `ProtocolKeys` registry in the Android app. This is the exact
`count` vs `digitCount` class of drift (see docs/PROTOCOL.md) and gets multiplicatively
worse as platforms are added (phone, watch, tablet, server). Centralizing the contract
into one leaf module consumed by every peer is the prerequisite for all ecosystem work.

## What was done (Phase 0)

1. **New module `core/protocol`** (leaf; only `api(libs.kotlinx.serialization.json)`):
   - `MessageTypes` — every WS message-type constant (append-only; renames are
     cross-peer release events).
   - `FieldNames` — envelope + payload field-name constants (digitCount, requestId, ...).
   - `ProtocolEnvelope` — canonical `{type, data}` builder + tolerant decode helpers.
   - `ProtocolGoldenFixtureTest` — golden JSON fixtures + frozen constant assertions;
     runs on every target `core/protocol` compiles for.
2. **Gradle wiring**: `settings.gradle.kts` include; `core:network` and `core:data`
   now `api(project(":core:protocol"))` so every consumer sees the contract.
3. **Desktop migration** (all literal protocol strings eliminated):
   - commonMain: `MessageHandler`, `WebSocketEngine`, `PairingEngine`
   - desktopMain: `WebSocketRoutes`, `FileExplorerRoutes`, `FileExplorerService`,
     `RelayService`, `DesktopWallpaperWatcherService`
   - composeApp: `ClipboardSyncService`, `MirrorWindow`, `MainMenuColumn`,
     `SettingsPanel`
4. **Governance**: AGENTS.md scope amended (desktop-only -> shared-core ecosystem,
   `core/protocol` declared wire-contract law); this plan entry filed.

## Verification

- `.\gradlew :core:protocol:desktopTest` — golden fixtures pass.
- `.\gradlew :core:network:desktopTest` — full engine suite (incl. 32-test
  MessageHandler contract suite) passes against the centralized registry.
- `.\gradlew :composeApp:desktopTest` — app suite passes.
- `.\gradlew spotlessCheck` — formatting gate (run before hand-off).

## STOP conditions

- Wire values may NEVER be edited to make a test pass. A fixture change is a
  deliberate, user-approved protocol revision shipped in the same commit across
  every peer and docs/PROTOCOL.md.
- `core/protocol` must remain a leaf: any PR adding a dependency beyond
  kotlinx.serialization is rejected. Transport, storage, DI, platform APIs live above.
- `composeApp` must never gain `androidMain`/`androidTarget()`.
- Android `ProtocolKeys` (DeX/app) is the parallel registry until plan 024's
  integration phase; both sides change together in one release or not at all.

## Next phases (each gets its own plan row before work starts)

- 0-A: `core/domain` — extract use cases from engines behind repository interfaces.
- 0-B: sync backend decision — self-hosted Ktor sync on Hetzner VPS recommended
  over Firestore (desktop JVM has no official Firebase SDK; identity namespace
  mismatch with googleSub trust tier).
- 0-C: `server/` relay — STREAMING pass-through redesign (no disk staging; CX22 has
  only ~40GB), E2EE relayed payloads, per-account quotas.
- 1: Android shared-core integration (merges `ProtocolKeys` into `core/protocol`
  consumption) — sequenced against plan 024 completion.
- 2+: Wear, tablets, iOS — each phase-gated on the core being ready.
