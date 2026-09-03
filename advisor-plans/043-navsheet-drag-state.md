# Plan 043 — NavBottomSheet Drag-State Extraction (Android, plan 024 Phase 4)

> Status: TODO
> Depends on: plan 024 (Phases 1-3 DONE). Effort: M. Risk: HIGH (gesture/kinematics).
> Soak testing REQUIRED before DONE (manual, user-driven).

## Why

`DeX/app/.../ui/components/NavBottomSheet.kt` is 569 lines. Verified structure:
`enum SheetTier(fraction)`, `enum SheetExpandedMode`, the `NavBottomSheet` composable
carrying nested stateful closures (`tierHeight()`, `expandTo(tier, triggerHaptic)`,
`collapseToHalf(triggerHaptic)`), scrim math
(`baseScrimAlpha = ((expansionFraction - 0.15f) / 0.85f).coerceIn(0,1) * 0.75f`),
and a private `DragHandle`. The drag/tier engine lives inside composition, so it is
untestable in isolation and every recomposition re-runs the state machinery.

## Scope

1. **WP0 audit**: catalogue the animation specs, velocity thresholds, tier fractions,
   haptic triggers, and how `expansionFraction` feeds the parent content lambda and
   the onboarding lock path (`if (!showOnboarding)`).
2. **Extract** a `rememberNavSheetState(...)` holder class: tier math, expansion
   animation, drag velocity logic, haptic triggers, expanded-mode resolution. The
   composable becomes a thin renderer consuming the holder.
3. Unit-test the holder: tier transitions, velocity thresholds, scrim alpha values —
   pure functions once extracted (the reason this is worth doing).

## STOP conditions

- ZERO visual/kinematic change: tier fractions, animation specs, scrim formula
  (byte-identical values), haptic timing, `SheetTier`/`SheetExpandedMode` semantics.
- The onboarding dormancy path must keep its exact behavior (sheet locked while
  onboarding owns the screen).
- The backdrop RenderNode recursion fix from the changelog (self-referential
  `.layerBackdrop` removal in `MorphingSheetNavPill`) must NOT be regressed —
  re-verify backdrop wiring after the move.
- Soak gates DONE: drag between all tiers, half-expand/collapse, haptics, onboarding
  first-run lock, scrim dimming at >50%, rotation/configuration change.

## Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`
  (ModifiersStabilityTest et al.) green.
- New unit tests for the extracted holder (tier math + scrim values as golden
  numbers captured from the pre-refactor code).
