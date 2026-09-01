# Plan 031 — Sync Backend: Self-Hosted Metadata Sync (Phase 0-B)

> Status: IN PROGRESS (decision settled 2026-08-31: self-hosted on the Hetzner VPS)
> Depends on: 025 (DONE); benefits from 027–029 but can start in parallel on the server side
> Effort: L (2–3 weeks solo)

## Why

The ecosystem promise — "new device signs in with the same Google account and instantly
inherits trust, history, settings" — needs an async metadata sync layer. Decision
(2026-08-31 review, user-approved direction): **self-hosted on the existing Hetzner VPS**
rather than Firestore, because (a) there is no official Firebase SDK for JVM/Compose
Desktop (the flagship platform), (b) Firebase Auth UID is a different identity
namespace than the `googleSub` the entire trust model is keyed on, and (c) the VPS is
already paid for and under our control. Push notifications remain on FCM/APNs (free,
best-in-class) — plan 032.

## Scope

1. **Sync server surface** (lives in `server/` with plan 032, this plan defines the
   client contract):
   - Collections: `devices` (roster: fingerprint, alias, deviceType, platform, push
     token), `history` (transfer metadata), `settings` (user preferences).
   - Auth: Google ID Token verification (same issuer as the relay); the account
     subtree is the tenant boundary.
2. **`core/sync` module** (new; commonMain):
   - `SyncEngine`: offline-first — local DataStore is the source of truth; sync is
     asynchronous delta exchange over authenticated HTTPS (SSE or long-poll listeners;
     REST snapshot on first connect). Queue-and-flush on connectivity.
   - **Hybrid Logical Clock (HLC) conflict resolution** — NOT wall-clock LWW. A wrong
     phone clock must never win a conflict. Every synced record carries
     `{deviceId, hlc}`; HLC compare is total and monotonic.
   - Deletion semantics: tombstones with a compaction window (default 30 days) so
     deletes propagate before being garbage-collected.
3. **Privacy law (inviolable)**:
   - File CONTENT and clipboard CONTENT are NEVER synced. Only metadata.
   - Paired-token VALUES are never synced — fingerprint identifiers only (a signed-in
     new device inherits same-account trust via the existing identity-proof flow, not
     by downloading credentials).
4. **Ports**: `SyncTransport` (HTTPS client adapter), `SyncStorage` (per-record
   persistence adapter over DataStore in `core/data`).

## STOP conditions

- A record type not listed above (devices/history/settings) requires a user decision —
  do not add new synced surfaces silently.
- HLC implementation must be property-tested (monotonicity across clock-skew scenarios)
  before any real data flows.
- If server deployment costs exceed the CX22 envelope (memory/disk), STOP and re-plan
  with the user — never "optimize" by dropping tombstones or HLC.
- Desktop must stay fully functional with sync unreachable (offline-first is a
  release gate, not a nice-to-have).

## Verification

```
.\gradlew :core:sync:desktopTest        # HLC property tests + engine suite
.\gradlew :composeApp:desktopTest       # no-regression gate
```
On-device: airplane-mode queue → reconnect → convergence test.
