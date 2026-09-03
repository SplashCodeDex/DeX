# Plan 043 — NavBottomSheet Drag-State Extraction (Android, plan 024 Phase 4)

> Status: IN PROGRESS (WP0 audit + NavSheetModel extraction DONE, unit tests + assembleDebug GREEN, soak pending)
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

## Execution record (2026-09-03)

- **Pure geometry & settle models extracted**: `DeX/app/.../ui/components/NavSheetModel.kt`
  houses `NavSheetGeometry` (50%/80%/100% detents, dynamic gap 6dp->3dp->0dp, dynamic
  radii, expansion fractions, nearest tier), `baseScrimAlphaFor` (golden formula),
  `SheetSettleAction`, and `NavSheetDecider` (verbatim thresholds: dismiss 50dp, fling
  velocity 350dp/s, drag commit 36dp, fling dismiss bound 30dp).
- **Renderer integration**: `NavBottomSheet.kt` simplified to delegate geometry,
  scrim calculation, and drag settling to the extracted pure models.
- **Unit test coverage**: `NavSheetModelTest.kt` with 13 comprehensive test cases covering
  geometry math, scrim golden values, fast flings (up/down/dismiss), directional commits
  from all tiers, and cancel recovery.
- **Build verification**: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`,
  `:app:assembleDebug`, and repo-root `:composeApp:desktopTest` all GREEN.

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
