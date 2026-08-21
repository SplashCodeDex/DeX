# Plan 013: Centralize OS Detection Logic

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat HEAD..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `HEAD`, 2026-08-21

## Why this matters

The `AGENTS.md` explicitly states: `ALWAYS prioritize BEST PRACTICES, Modularization, refactorization and Centralization`. The codebase currently duplicates `System.getProperty("os.name").lowercase().contains("mac")` and similar checks for Windows across 10+ different files (`DockCardContent.kt`, `DockedWindowStateController.kt`, `FloatingDockCard.kt`, `MouseInputProvider.kt`, etc.). This causes maintenance overhead and violates DRY principles. We will centralize these platform checks into a single `DesktopEnvironment` object to improve modularity and maintainability.

## Current state

- OS checks are scattered:
  ```kotlin
  val isMacOS = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
  if (System.getProperty("os.name").lowercase().contains("windows")) { ... }
  ```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test`         | all pass            |

## Scope

**In scope** (the only files you should modify):
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/platform/DesktopEnvironment.kt` (create)
- All files in `composeApp` that contain `System.getProperty("os.name")` checks. (e.g. `GlobalShortcutService.kt`, `WiggleToOpenService.kt`, `MouseInputProvider.kt`, `TaskbarWorkAreaProvider.kt`, `DockCardContent.kt`, `DockedWindowStateController.kt`, `FloatingDockCard.kt`, `MainMenuColumn.kt`, `DockCardPhysics.kt`).

**Out of scope** (do NOT touch, even though they look related):
- Do not modify non-desktop modules if they use expect/actual. We only address `desktopMain` JVM `System.getProperty` calls here.

## Git workflow

- Branch: `advisor/013-centralize-os-detection`
- Commit per step or per logical unit; message style: `[refactor] Centralize OS detection logic into DesktopEnvironment`

## Steps

### Step 1: Create `DesktopEnvironment`

Create `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/platform/DesktopEnvironment.kt`:
```kotlin
package com.dexstudios.dex.platform

object DesktopEnvironment {
    private val osName = System.getProperty("os.name")?.lowercase() ?: ""

    val isWindows: Boolean = osName.contains("windows")
    val isMacOS: Boolean = osName.contains("mac")
    val isLinux: Boolean = osName.contains("linux")
}
```

### Step 2: Replace occurrences

Find all usages of `System.getProperty("os.name")` in `composeApp/src/desktopMain/kotlin/` and replace them with `DesktopEnvironment.isMacOS` or `DesktopEnvironment.isWindows` accordingly.

Examples of files to update:
- `com/dexstudios/dex/desktop/jna/GlobalShortcutService.kt`
- `com/dexstudios/dex/desktop/jna/WiggleToOpenService.kt`
- `com/dexstudios/dex/platform/MouseInputProvider.kt`
- `com/dexstudios/dex/platform/TaskbarWorkAreaProvider.kt`
- `com/dexstudios/dex/window/DockCardContent.kt`
- `com/dexstudios/dex/window/DockedWindowStateController.kt`
- `com/dexstudios/dex/window/FloatingDockCard.kt`
- `com/dexstudios/dex/window/MainMenuColumn.kt`
- `com/dexstudios/dex/window/kinematics/DockCardPhysics.kt`

**Verify**: `./gradlew :composeApp:desktopJar` -> exit 0

## Test plan

- Ensure no logic branches are inverted.
- Verification: `./gradlew test` -> all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `grep -rn 'System.getProperty("os.name")' composeApp/src/desktopMain` returns only the match in `DesktopEnvironment.kt`.
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

- The codebase has drifted since this plan was written.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- When porting to fully native Kotlin Multiplatform later, `DesktopEnvironment` can easily be swapped with `expect`/`actual` without touching 15 UI files.
