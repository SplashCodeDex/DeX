# Plan 003: Remove Android targets from Desktop-only modules

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 6c8ae4a..HEAD -- core/network/build.gradle.kts core/data/build.gradle.kts feature/discovery/build.gradle.kts`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `6c8ae4a`, 2026-08-21

## Why this matters

The `AGENTS.md` rules strictly dictate: "No Android target may be added to the Compose desktop app. It is desktop-only." However, several `core/` and `feature/` modules still define an `android { ... }` block and an `androidMain` source set. This forces macOS devs to download the Android SDK just to build a desktop app, inflates build times, and pulls in Android-specific dependencies. We must purge these targets to adhere to the strict invariants of the repository.

## Current state

- `core/network/build.gradle.kts` — Contains `android {}` and `alias(libs.plugins.android.multiplatform.library)`.
- `core/data/build.gradle.kts` — Contains `android {}`.
- `feature/discovery/build.gradle.kts` — Contains `android {}` (implied from audit).

Excerpt:
```kotlin
// core/network/build.gradle.kts:8
kotlin {
    android {
        namespace = "com.dexstudios.dex.core.network"
        compileSdk = 37
        minSdk = 24
        // ...
    }
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew build` | exit 0              |

## Scope

**In scope**:
- All `build.gradle.kts` files inside `core/` and `feature/`
- Any `androidMain` directories inside `core/` and `feature/` (to be deleted).

**Out of scope**:
- The standalone Android app at `DeX/DeX/` (it is explicitly out of scope for the desktop project).
- `composeApp/build.gradle.kts` (it already lacks Android targets).

## Git workflow

- Branch: `advisor/003-remove-android-targets`
- Commit message: `[major] Remove Android targets from KMP shared modules`

## Steps

### Step 1: Remove Android plugins and config blocks

In `core/network/build.gradle.kts`, `core/data/build.gradle.kts`, and `feature/discovery/build.gradle.kts`:
- Remove `alias(libs.plugins.android.multiplatform.library)` or `id("com.android.library")`.
- Remove the entire `android { ... }` block.
- Remove the `androidMain.dependencies { ... }` block.

### Step 2: Delete `androidMain` source directories

Delete the `src/androidMain` folders in `core/network/`, `core/data/`, and `feature/discovery/`.

> **Note**: Check if there are any `expect`/`actual` declarations where the `actual` was only provided in `androidMain`. If so, fold them into `jvmMain` or delete them entirely if they were truly Android-specific and aren't used in `commonMain`.

**Verify**: `./gradlew build` → `BUILD SUCCESSFUL`

## Test plan

- Run `./gradlew build` to confirm compilation without the Android targets.

## Done criteria

- [ ] `./gradlew build` exits 0.
- [ ] No `android {` blocks exist in `core/` or `feature/` `build.gradle.kts` files.
- [ ] No `androidMain` directories exist in `core/` or `feature/`.
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:

- Removing `androidMain` breaks `commonMain` code because an `expect` declaration lacks a `jvm` actual and you cannot trivially implement it for JVM.
