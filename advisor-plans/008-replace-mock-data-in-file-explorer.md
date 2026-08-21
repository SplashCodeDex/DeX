# Plan 008: Replace mock data in File Explorer

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat ec6886b..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/FileExplorerPanel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `ec6886b`, 2026-08-21

## Why this matters

The File Explorer UI (`FileExplorerPanel.kt`) contains hardcoded mock data for visual parity when the phone is disconnected or data hasn't loaded yet. This can confuse users if a connection drops and suddenly mock files like "DCIM" or "Downloads" appear, deceiving the user into thinking the connection is alive.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/FileExplorerPanel.kt`

Excerpts:
```kotlin
// composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/FileExplorerPanel.kt:241
                // Fallback mock items if phone is not connected yet (scaffolding visual parity)
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0              |
| Test      | `./gradlew :composeApp:desktopTest`| all pass            |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/FileExplorerPanel.kt`

**Out of scope**:
- Any actual network logic or UI layout outside the mock items removal.

## Git workflow

- Branch: `advisor/008-replace-mock-data-in-file-explorer`
- Commit per step or per logical unit; message style: `[fix] Remove hardcoded mock folders from FileExplorerPanel`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Remove mock data from `FileExplorerPanel.kt`
Locate the block of code starting with `// Fallback mock items if phone is not connected yet (scaffolding visual parity)`.
Instead of rendering mock items, render an empty state or a generic "No folders shared / Phone disconnected" placeholder text, or simply nothing (an empty list) if the phone is not connected. 
Remove the mock `FolderItem` or `SharedFolder` definitions that are hardcoded.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] Hardcoded fallback mock items are removed from `FileExplorerPanel.kt`.
- [ ] `./gradlew :composeApp:desktopJar` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:
- The codebase has drifted since this plan was written.

## Maintenance notes

- Any design for empty states should use the standard typography and colors from `MaterialTheme`.
