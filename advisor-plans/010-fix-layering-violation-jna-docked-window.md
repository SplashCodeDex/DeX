# Plan 010: Fix layering violation with JNA in DockedWindowStateController

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat ec6886b..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockedWindowStateController.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `ec6886b`, 2026-08-21

## Why this matters

`DockedWindowStateController` contains a direct JNA call to `com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01)` to determine if the left mouse button is held down. This is a severe layering violation, as it directly binds the UI state controller (which could theoretically be tested on macOS or Linux headless) to a Windows-specific native library. It breaks testability and violates cross-platform architecture in KMP.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockedWindowStateController.kt`

Excerpts:
```kotlin
// composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockedWindowStateController.kt:103-108
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            try {
                val lButton = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01).toInt()
                if ((lButton and 0x8000) != 0) {
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0              |
| Test      | `./gradlew :composeApp:desktopTest`| all pass            |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockedWindowStateController.kt`
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/platform/MouseInputProvider.kt` (NEW)

**Out of scope**:
- Physics calculations or other UI logics.

## Git workflow

- Branch: `advisor/010-fix-layering-violation-jna-docked-window`
- Commit per step or per logical unit; message style: `[fix] Extract JNA mouse calls to MouseInputProvider`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create `MouseInputProvider` interface and Windows implementation
Create `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/platform/MouseInputProvider.kt` with:
```kotlin
package com.dexstudios.dex.platform

interface MouseInputProvider {
    fun isLeftMouseButtonDown(): Boolean
}

object DesktopMouseInputProvider : MouseInputProvider {
    override fun isLeftMouseButtonDown(): Boolean {
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            try {
                val lButton = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01).toInt()
                return (lButton and 0x8000) != 0
            } catch (e: Throwable) {
                return false
            }
        }
        return false
    }
}
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

### Step 2: Inject `MouseInputProvider` into `DockedWindowStateController`
Update the `DockedWindowStateController` constructor to accept `val mouseInputProvider: MouseInputProvider = DesktopMouseInputProvider` as an optional parameter.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

### Step 3: Refactor `DockedWindowStateController`
Replace the direct JNA calls in `shouldDismissOnFocusLoss` and `deferHideOnDragDrop` with calls to `mouseInputProvider.isLeftMouseButtonDown()`.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `DockedWindowStateController` no longer references `com.sun.jna.*` classes directly.
- [ ] `./gradlew :composeApp:desktopJar` exits 0.
- [ ] `./gradlew :composeApp:desktopTest` exits 0.
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:
- The codebase has drifted since this plan was written.

## Maintenance notes

- Any future OS-specific native calls must go through a platform abstraction in `com.dexstudios.dex.platform`.
