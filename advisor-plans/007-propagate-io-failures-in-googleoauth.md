# Plan 007: Propagate I/O failures in GoogleOAuth profile operations

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat ec6886b..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/GoogleOAuth.kt`
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

In `GoogleOAuth.kt`, there are several empty `catch (e: Exception) {}` blocks around critical I/O operations (like `saveProfile`, `signOut`, `log`, and `loadCredentials`). This silently swallows exceptions such as disk full or permission errors, leaving the app in an inconsistent state and making it impossible to debug why OAuth state isn't persisting. 

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/GoogleOAuth.kt` — manages OAuth.

Excerpts:
```kotlin
// core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/GoogleOAuth.kt:173-180
    fun saveProfile(profile: GoogleProfile) {
        try {
            if (!baseDirectory.exists()) {
                baseDirectory.mkdirs()
            }
            val file = File(baseDirectory, "google_profile.json")
            file.writeText(json.encodeToString(profile))
        } catch (e: Exception) {}
    }
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0              |
| Test      | `./gradlew test`         | all pass            |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/GoogleOAuth.kt`

**Out of scope**:
- Changing the return types of `saveProfile`, `signOut`, or the OAuth flow.

## Git workflow

- Branch: `advisor/007-propagate-io-failures-in-googleoauth`
- Commit per step or per logical unit; message style: `[fix] Log exception instead of swallowing in GoogleOAuth`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add error logging to `saveProfile` and `signOut`
In `saveProfile` and `signOut`, replace `catch (e: Exception) {}` with `catch (e: Exception) { println("GoogleOAuth error: ${e.message}") }`.
We don't need to throw since this might crash callers that didn't expect it, but we MUST not swallow silently.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

### Step 2: Add error logging to `log` and `loadCredentials`
In `log` and `loadCredentials`, replace `catch (e: Exception) {}` with a simple standard output print, e.g., `catch (e: Exception) { println("GoogleOAuth file I/O error: ${e.message}") }`.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew test` → all pass.

## Done criteria

- [ ] All empty `catch (e: Exception) {}` blocks in `GoogleOAuth.kt` are replaced with a `println` containing the exception message.
- [ ] `./gradlew :composeApp:desktopJar` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:
- The codebase has drifted since this plan was written.

## Maintenance notes

- Do not use empty catch blocks for I/O operations in the future.
