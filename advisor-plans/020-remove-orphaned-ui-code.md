# Plan 020: Remove Orphaned UI Code + Unwired Feature Modules

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the next
> step. If anything in the "STOP conditions" section occurs, stop and report —
> do not improvise. When done, update the status row for this plan in
> `advisor-plans/README.md`.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW-MEDIUM (large surface, all reference-verified zero-wired-usage)
- **Depends on**: none
- **Category**: tech-debt / dead-code
- **Planned at**: commit `6466e3d`, 2026-08-24
- **Authorization**: user verdict after visual gallery review ("REMOVE THEM ALL")

## Why this matters

The desktop app carries an entire unwired UI layer: two compiled-but-never-imported
feature modules, a glass component family whose last wired consumer was removed in
10.1.13.0, backward-compat shims nobody imports, and several dead surfaces. They cost
build time, invite drift, and mislead contributors. Every deletion below was verified
zero-referenced from wired code before inclusion.

## Scope

**Deleted outright** (zero references from wired code):
- `core/designsystem`: `components/DeXButtons.kt`, `components/DeXPanel.kt`,
  `components/DeXScrollbar.kt` (+`.jvm.kt`), `components/HoverState.kt` (+`.jvm.kt`),
  `components/FloatingPillNavBar.kt`, `state/UIState.kt`,
  `components/glass/*` (5 files)
- `composeApp`: commonMain `App.kt`, `mirror/MirrorScreen.kt`, `mirror/ImageUtils.kt`
  (+ jvm actual); desktopMain `window/PinPairingPanel.kt`, `window/DockCardAnimations.kt`,
  `window/ScreenBoundsHelper.kt`, `window/components/DownloadDockToast.kt`,
  `window/styling/SkiaDropShadow.kt`
- `composeApp/src/desktopTest/theme/Milestone4ThemeAndStylingTest.kt` and
  `Milestone4AdversarialStressTest.kt` (they pin the deleted glass API; color-token
  truth remains in the archived WPF source)
- Modules: `feature/discovery`, `feature/settings` (dirs + settings.gradle.kts include
  + composeApp dependencies)

**Edited**:
- `core/designsystem/theme/Theme.kt` — drop `LocalBackdrop` + kyant import (last use)
- `core/designsystem/build.gradle.kts` — drop `api(libs.backdrop)`
- `gradle/libs.versions.toml` — drop `backdrop` entries
- `settings.gradle.kts`, `composeApp/build.gradle.kts` — unhook feature modules
- `docs/ARCHITECTURE.md` — module graph corrected

**Explicitly KEPT**: `BubbleFluidity.kt` (wired everywhere), `FilePicker` expect/actual
(candidate home for the future picker-service centralization), `DeXAnimatedIcons.kt`
(wired), `compottie`/`coil` deps (wired).

## Verification

```
gradlew :composeApp:desktopTest :core:data:desktopTest :core:network:desktopTest spotlessCheck
grep -rn "LiquidGlass\|DeXGlassPresets\|LocalBackdrop\|feature.discovery\|feature.settings" composeApp/src core/designsystem/src  # expect no hits outside docs
```

## STOP conditions

- Any wired file fails to compile after deletions (means a reference was missed).
- Test suite failures unrelated to the deleted test files.

## Done criteria

- [ ] All scoped files/dirs gone; grep clean
- [ ] Full gate suite green (verified standalone via worktree against HEAD+diff)
- [ ] ARCHITECTURE.md matches reality
- [ ] README status row updated