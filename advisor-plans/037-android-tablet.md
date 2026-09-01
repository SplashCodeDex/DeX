# Plan 037 — Android Tablet: Adaptive Dashboard (Phase 2B)

> Status: TODO
> Depends on: 030 (Android on shared core)
> Effort: M (2–3 weeks solo)

## Why

The Android app is phone-shaped today. On tablets (and foldables), the ecosystem
promise is a dashboard layout — same shared core, adaptive Compose shell. This runs on
the post-030 unified codebase, which is why it gates on it.

## Scope

1. **Adaptive scaffold**: current Material 3 adaptive/navigation APIs
   (`NavigationSuiteScaffold` / window-size classes or the current successor — verify
   against the newest Compose guidance, research trigger). Dashboard panes: roster,
   transfers, history, settings.
2. **Foldable support**: window-size-class transitions (fold/posture), layout that
   re-arranges rather than re-renders; hinge/posture APIs only where they add value.
3. **Drag-and-drop**: multi-window + cross-app drops as send sources.
4. **Verification on form factors**: phone layout MUST NOT regress (same APK) —
   every change is behind size-class checks, verified on a phone profile.

## STOP conditions

- No layout-only workarounds for logic issues — bugs found go to `core/domain`.
- The tablet shell must consume the SAME `core/domain` use cases the phone shell does
  (drift law).
- Foldable posture handling that cannot be tested locally is SHIPPED DISABLED, not
  assumed — never fake device support.

## Verification

`.\gradlew :app:assembleDebug` + emulator tablet/foldable profiles + phone-profile
regression pass; on-device if hardware available.
