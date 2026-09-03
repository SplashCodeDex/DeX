# Plan 040 — core/data Split: Platform Primitives vs Persistence Adapters

> Status: TODO
> Depends on: none hard. Sequence AFTER plan 032 lands (032 is actively editing
> core/network and core/data edges; this plan rewrites the same build files).
> Related: plan 026 (established the layer law this plan completes for the data layer).

## Why

`core/data` mixes two unrelated responsibilities in one module:

1. **Platform-neutral primitives** (no DataStore, no Koin, no Ktor):
   `RelayCrypto` (+ `androidMain`/`desktopMain` expect-actuals), `protocol/HashUtils`
   (+ expect-actuals), `protocol/TokenCodec`, `protocol/ProtocolDto`, `protocol/DeXPorts`,
   `protocol/PunchState`, `protocol/TransferState`.
2. **Persistence/DI adapters**: `DataStoreSyncStorage`, `SyncBridge`, `DeviceConfig`,
   `TransferHistory`, `WallpaperState`, `PcMemory` (classification confirmed in WP1) —
   the DataStore + Koin dependents.

The concrete defect: `core/domain` depends on `core:data` (its documented source of
"core/data primitives"), but nothing in the compiler prevents domain code from importing
DataStore-backed adapter types — they share one module and one Gradle edge. The domain
law ("no DataStore/Koin", plan 026) is currently enforced only by review discipline.

Aggravating factor (verified 2026-09-03): the entire module lives under the package name
`com.dexstudios.dex.core.network.*` — the NETWORK module's package. Same-package
references therefore need no import statements, which makes the true consumer graph
invisible to import analysis (a repo-wide import search for these classes returns near-zero
hits despite real cross-module usage). The split also surfaces this package camouflage
as a first-class finding.

## Scope

1. **New module `core/primitives`** — targets mirroring `core/data` exactly:
   `jvm("desktop")` + Android (required: `RelayCrypto` and `HashUtils` carry
   `androidMain`/`desktopMain` expect-actuals). Deps: `core/protocol`, coroutines,
   serialization ONLY. NO DataStore, NO Koin, NO Ktor, NO Kermit.
2. **Move verbatim** (WP1 classifies, then WP2 moves; packages unchanged in this slice):
   - Primitives: `RelayCrypto` (+ actuals), `protocol/HashUtils` (+ actuals),
     `protocol/TokenCodec`, `protocol/ProtocolDto`, `protocol/DeXPorts`,
     `protocol/PunchState`, `protocol/TransferState`.
   - Tests move with their subjects: `TokenCodecTest`, `RelayCryptoTest`.
   - `PcMemory`, `DeviceConfig`, `WallpaperState`, `TransferHistory` are classified in
     WP1 (pure models → primitives; DataStore-backed → stay).
3. **Rewire Gradle edges**:
   - `core/domain`: `implementation(":core:data")` → `implementation(":core:primitives")`
     (the domain law becomes compiler-enforced).
   - `core/network`: keeps `:core:data` (adapters) and adds `:core:primitives` as needed.
   - `core/data`: keeps adapters, depends on `:core:primitives`.
   - `composeApp`: switch direct primitive imports to `:core:primitives`.
4. **Package camouflage**: NOT fixed in this slice (a package rename is a repo-wide
   import churn — separate decision, see Next phases). The plan that executes this one
   must record, per moved file, which consumers resolved it same-package (no import)
   so the future rename has a real consumer map.

## STOP conditions

- **Move-only**: zero logic changes, zero behavior changes. Every moved test file passes
  unmodified (package/imports unchanged — same package, new module).
- `core/primitives` must stay platform-pure: a PR adding DataStore/Koin/Ktor/Kermit to it
  is rejected. Adapters live in `core/data`.
- Wire contract (`core/protocol`) untouched; golden fixtures untouched.
- `server/` untouched (it never depended on `core/data`).
- Android app (`DeX/app`) compile unaffected — it consumes `core/network`, which keeps
  its public surface.

## Verification

- `:core:primitives:desktopTest` — relocated TokenCodecTest + RelayCryptoTest pass.
- `:core:domain:desktopTest`, `:core:data:desktopTest`, `:core:network:desktopTest`,
  `:composeApp:desktopTest` — full suites pass.
- `spotlessCheck` green.

## Next phases

- Package de-camouflage: move `com.dexstudios.dex.core.network.*` out of the network
  package in `core/data` + `core/primitives` (consumer map recorded during execution).
- Revisit plan 032's `core/data` SyncBridge host surface for the same primitives/adapter
  seam if it grows.
