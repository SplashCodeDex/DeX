# Plan 036 — iPadOS: Adaptive Dashboard Layout (Phase 2A)

> Status: TODO
> Depends on: 034 (DONE — same Xcode target)
> Effort: S–M (1–2 weeks solo — nearly free after the phone app)

## Why

The iPhone app and the iPad app are ONE SwiftUI target with adaptive layout — the
decision that collapsed the original brief's Phase 2A from a separate project into
layout work. The iPad experience is a dashboard, not a stretched phone.

## Scope

1. **Adaptive layout**: size-class-driven dashboard (roster sidebar + transfer detail +
   history pane) using SwiftUI's current navigation/adaptive APIs (NavigationSplitView
   or successor — verify current best practice, research trigger).
2. **Multitasking**: Split View + Slide Over support (test in both), drag-and-drop
   between panes and to/from other iPad apps (files → DeX send flow), keyboard
   shortcuts (send, cancel, navigate).
3. **Transfer from iPad**: document picker + drag-in as send sources.
4. **App Store**: iPad screenshots + metadata delta submission.

## STOP conditions

- Zero shared-core changes — if iPad "needs" logic, it goes in `core/domain`.
- Do not fork a separate iPad target/bundle — one target is the entire point.
- Drag-and-drop must respect the same offer/accept/verify transfer semantics
  (03 domain law) — no "simplified" send path.

## Verification

iPad simulator + real device if available: all three multitasking modes, drag round-trip
with the desktop, keyboard shortcuts, dashboard at every size class.
