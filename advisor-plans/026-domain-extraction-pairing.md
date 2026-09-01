# Plan 026 — core/domain Extraction: Pairing Use-Case Layer

> Status: DONE (executed 2026-09-01)
> Parent: plan 025 Phase 0-A. Depends on: plan 025 wire contract (DONE).

## Why

`PairingEngine` — the pairing state machine (Idle/Qr/Pin/Success/Error, PIN TTLs,
fingerprint-bound proofs) — lives in `core/network/commonMain` under the `auth` package
and reaches directly into `DeviceManager` (DataStore persistence in `core/network`) and
`DiscoveredDevice`/wire DTOs. Per the ecosystem architecture (plan 025), this is
platform-neutral domain logic that Wear/iOS/watchOS peers must reuse verbatim; it cannot
keep living inside the transport module. This extraction establishes the `core/domain`
layer pattern (ports + use cases, zero platform deps) that every later extraction
(Transfer, Discovery, Clipboard use cases) follows.

## Scope (this phase — deliberately ONE vertical slice)

1. **New module `core/domain`** — `jvm("desktop")` target, deps: `core/protocol`,
   `coroutines` only. NO Ktor, NO DataStore, NO Koin, NO Kermit.
2. **Move verbatim**: `PairingEngine` + `PairingState` from
   `core/network/commonMain/.../auth/` to `core/domain/commonMain/.../pairing/`.
   Class body logic unchanged (state machine, TTLs, PIN minting, envelope building via
   core/protocol — all already migrated in plan 025).
3. **Introduce the `PairingGrantStore` port**: replace the engine's `persistentGrant`
   field's default (which calls `DeviceManager` + `HashUtils` — infrastructure) with a
   domain interface `PairingGrantStore { suspend fun grant(fingerprint): String }`.
   The engine keeps `persistentGrant` as an internal test seam delegating to the store
   (tests override it exactly as before — zero test churn).
4. **Adapter**: `DeviceManagerPairingGrantStore` in `core/network` implementing the port
   via `DeviceManager.savePairedFingerprint/savePairedToken` + `HashUtils.generateUUID`
   (the exact previous default behavior). Wired in `NetworkModule` via Koin.
5. **Rewire consumers** (import-path changes only): `NetworkModule`, `DeXServer`,
   `WebSocketRoutes` (core/network); `DockCardContent`, `FloatingDockCard`,
   `PinPairingPanel`, `InboundPairingDialog` (composeApp); test files re-homed
   (`PairingEngineTest` moves to `core/domain` desktopTest; stress-test stale imports
   updated).
6. **Consolidate the duplicated forget-device flow**: `MainMenuColumn` and
   `SettingsPanel` both hand-roll "send unpair frame + markUntrusted +
   removePairedFingerprint" — extracted into
   `core/network/desktopMain .../services/TrustRevocationService` used by both.

## STOP conditions

- `PairingEngine` semantics must remain byte-identical: TTL 60s, PIN 5-digit range
  10000..99999, fingerprint-bound verify, deliver-first-then-transition, one-offer
  last-wins, expiry-sweep guards. The 370-line `PairingEngineTest` is the contract —
  it moves with the engine and must pass unmodified (only package/imports change).
- `core/domain` must stay platform-pure: a PR adding Ktor/DataStore/Koin/Compose to it
  is rejected. Adapters live in `core/network` (or future platform modules).
- The `auth` package in `core/network` keeps `AuthState` (mirrors DeviceManager into
  StateFlows; heavily consumed by engines+UI) — do NOT move it in this slice; it is
  coupled to DeviceManager persistence and belongs to a later slice decision.
- Wire format unchanged; `PairingEngineTest`'s literal frame assertions ("pair-prompt"
  etc.) stay as independent cross-checks of the golden fixtures.

## Verification

- `:core:domain:desktopTest` — the relocated PairingEngineTest passes.
- `:core:network:desktopTest` + `:composeApp:desktopTest` — full suites pass.
- `spotlessCheck` green.

## Next phases

- 0-A2: TransferUseCase extraction (ClientEngine/DesktopPullService orchestration).
- 0-A3: DiscoveryUseCase + roster state.
- Then 0-B (sync backend decision) and 0-C (streaming relay) per plan 025.
