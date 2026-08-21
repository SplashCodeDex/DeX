# Plan 004: Remove hardcoded Windows-specific javaHome

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 6c8ae4a..HEAD -- composeApp/build.gradle.kts`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx
- **Planned at**: commit `6c8ae4a`, 2026-08-21

## Why this matters

The `composeApp/build.gradle.kts` hardcodes `javaHome` to a strict Windows file path (`C:/Program Files/...`). This forcefully breaks the build for any macOS user or Windows user with a non-standard Java installation directory. Relying on Gradle's native toolchain auto-provisioning is the correct way to handle cross-platform JVM resolution.

## Current state

- `composeApp/build.gradle.kts`

Excerpt of current state:
```kotlin
// composeApp/build.gradle.kts:67
compose.desktop {
    application {
        mainClass = "com.dexstudios.dex.MainKt"
        javaHome = "C:/Program Files/Eclipse Adoptium/jdk-26.0.2.10-hotspot"
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopClasses` | exit 0              |

## Scope

**In scope**:
- `composeApp/build.gradle.kts`

**Out of scope**:
- Any other Gradle config changes.

## Git workflow

- Branch: `advisor/004-fix-hardcoded-javahome`
- Commit message: `[fix] Remove hardcoded Windows javaHome path in composeApp`

## Steps

### Step 1: Remove `javaHome`

In `composeApp/build.gradle.kts`, delete the line:
`javaHome = "C:/Program Files/Eclipse Adoptium/jdk-26.0.2.10-hotspot"`

Gradle and Compose Desktop will automatically use the JDK executing Gradle or the properly configured Java Toolchain.

**Verify**: `./gradlew :composeApp:desktopClasses` → `BUILD SUCCESSFUL`

## Test plan

- Ensure the application still builds successfully without the hardcoded path.

## Done criteria

- [ ] `./gradlew :composeApp:desktopClasses` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:

- Removing `javaHome` somehow causes the build to fail indicating a missing JDK.
