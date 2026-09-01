# Plan 039 — watchOS: Swift Watch App (Phase 3B — DEFERRED)

> Status: TODO — DEFERRED until iOS (033–035) is mature AND user demand exists
> Depends on: 034 (DONE), explicit user GO (demand gate)
> Effort: L (7–10 weeks solo) — do NOT start without the GO

## Why

Apple Watch mirrors the Wear OS value (accept/reject, roster, glance) for the iOS side,
via SwiftUI watchOS consuming the KMP `watchos*` targets. Deferred per the approved
ecosystem brief: wear-first, watchOS only once iOS is shipped and real users ask.

## Scope (only after GO)

1. **`watchosArm64`/`watchosSimulatorArm64` targets** on the pure `core/*` modules
   (same pattern as 033; this is why 033 keeps the target list lean).
2. **SwiftUI watch app**: roster, transfer status, accept/reject with haptics, Digital
   Crown navigation.
3. **Complications** (connected-device count, last transfer) + Live Activities-style
   transfer progress where the API allows (verify current watchOS API set — research
   trigger).
4. **Watch↔iPhone data flow**: WatchConnectivity for actions, synced cache for state —
   the watch does not talk to the relay directly in this phase.

## STOP conditions

- The demand gate is real: no speculative build "because it's next in the doc".
- Same drift laws: `core/protocol`/`core/domain` or nothing.
- Complication update budgets respected (watchOS is stricter than Wear — even small
  violations are review-rejection risks).
- watchOS memory limits: keep the watch binary lean; if shared-core deadcode inflates
   it, STOP and reassess (minimization strategy is a user decision).

## Verification

Watch simulator + real watch: complication renders, accept/reject round trip, crown
navigation, battery sanity over a day of real use.
