# Plan 005: Do not swallow exceptions on DeXServer startup

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 6c8ae4a..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: correctness
- **Planned at**: commit `6c8ae4a`, 2026-08-21

## Why this matters

The internal application server (`DeXServer`) is critical for DeX to function. Currently, in `main.kt`, if `DeXServer.start()` throws an exception (e.g., port already in use), it is caught silently and printed to the console. The app then continues to launch in a silently broken state. We must instead fail fast: show a native AWT dialog (since Compose hasn't initialized yet) or a simple error message, and gracefully exit the application.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt`

Excerpt of current state:
```kotlin
// composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt:58
try {
    DeXServer.start()
} catch (e: Exception) {
    println("DeXServer already running or failed to start: ")
}

application {
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopClasses` | exit 0              |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt`

**Out of scope**:
- `DeXServer` internals.

## Git workflow

- Branch: `advisor/005-fix-dexserver-startup`
- Commit message: `[fix] Prevent silent failure if DeXServer fails to start`

## Steps

### Step 1: Add a fatal error dialog on server failure

In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt`, update the `catch` block to use `javax.swing.JOptionPane` to show an error before calling `kotlin.system.exitProcess(1)`.

Target shape:
```kotlin
try {
    DeXServer.start()
} catch (e: Exception) {
    e.printStackTrace()
    javax.swing.JOptionPane.showMessageDialog(
        null,
        "Failed to start DeX internal server.\nEnsure port 48425 is not in use by another instance.\nError: ${e.message}",
        "DeX Startup Error",
        javax.swing.JOptionPane.ERROR_MESSAGE
    )
    kotlin.system.exitProcess(1)
}
```

**Verify**: `./gradlew :composeApp:desktopClasses` → `BUILD SUCCESSFUL`

## Test plan

- To manually test, you can run two instances of the application. The second instance should present the error dialog instead of launching with a broken network stack.

## Done criteria

- [ ] `./gradlew :composeApp:desktopClasses` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:

- Using `javax.swing.JOptionPane` throws HeadlessExceptions in some test runner configuration (if so, wrap it in a `try-catch`).
