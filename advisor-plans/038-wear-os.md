# Plan 038 — Wear OS: Notification Actions First, Then Watch App (Phase 3A)

> Status: TODO
> Depends on: 031 (sync) + 032 (server push) — the watch needs the notification pipe
> Effort: M–L (4–6 weeks solo; staged 1→3)

## Why

The wrist value is "Phone X wants to send a file — Accept / Reject on the watch" +
roster/transfer-status glances. Staged so value ships at each step; the watch app
becomes necessary only after notification actions prove demand.

## Scope — staged

**Stage 1 (1–2 wks): notification actions.** Rich notifications with action buttons
bridged to the watch by the OS. Requires: the push path (server → FCM → phone →
actionable notification) and a tiny request-decision API back to the server. Zero watch
code — this alone delivers ~80% of the wrist value.
**Stage 2 (2–3 wks): Compose for Wear OS companion app.** `wearApp/` module consuming
`core/protocol` + `core/domain` (roster view via sync, transfer status, rotary input,
circular layouts). Data flows phone→watch via the Wear Data Layer or direct sync —
decide at execution with data sizes in hand; keep the watch face OFF the network
directly where possible (battery).
**Stage 3 (1 wk): Tiles + complications.** Tile: roster/transfer-status glance.
Complication: connected-device count / last transfer. Both read from the sync cache —
no live sockets.

## STOP conditions

- Watch UI never gets its own protocol strings — `core/protocol` or nothing (drift law).
- Tile/complication data freshness respects the platform's update budgets — no
  battery-draining polling loops (tile update cadence limits are contract, not
  suggestion).
- A standalone watch network stack (watch talking directly to relay) is OUT unless the
  user explicitly expands scope — Wear apps here are companions first.
- Wear OS testing on a real watch before DONE (emulators are weak for rotary/tiles) —
  hardware purchase is a user decision (~$150–250).

## Verification

```
.\gradlew :wearApp:assembleDebug
```
On-watch: notification accept/reject round trip, tile refresh, complication updates,
rotary navigation.
