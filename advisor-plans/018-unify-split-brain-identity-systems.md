# Plan 018: Unify split-brain identity and pairing systems

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 7f8c1de..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/IdentityManager.kt core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `7f8c1de`, 2026-08-21

## Why this matters

The system has two completely separate identity and pairing data stores: `DeviceManager.kt`/`DeviceConfig.kt` (using AndroidX DataStore for the UI) and `IdentityManager.kt` (using raw JSON file parsing in `jvmMain`). Ktor routes (`ShareRoutes.kt`) authorize incoming file uploads against `IdentityManager`, but the UI establishes pairings via `DeviceManager`. This causes all authorized uploads to fail with 403 Forbidden because `IdentityManager` remains empty. Consolidating this to `DeviceConfig` + `DeviceManager` fixes core functionality.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/IdentityManager.kt` — implements identity via `identity.json`, `paired_devices.json`, etc.
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt` — references `IdentityManager.isIdentityToken(token)` and `IdentityManager.pairedTokens[req.info.fingerprint] == token`.

Excerpt from `ShareRoutes.kt:99-105`:
```kotlin
                val isAutoTrusted = IdentityManager.isIdentityToken(token)
                val isPaired = !token.isNullOrEmpty() && IdentityManager.pairedTokens[req.info.fingerprint] == token

                if (!isAutoTrusted && !isPaired) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopJar`           | exit 0              |
| Tests     | `./gradlew :composeApp:desktopTest`  | all pass            |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/IdentityManager.kt` (to be deleted)
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt`
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/ServerEngine.kt` (or wherever routes are configured, to inject DeviceConfig, if necessary)
- Any other Ktor routes in `jvmMain` referencing `IdentityManager`

**Out of scope**:
- Modifications to `DeviceConfig.kt` or `DeviceManager.kt`. They already exist and work.

## Git workflow

- Branch: `advisor/018-unify-split-brain-identity-systems`
- Commit message: `[tech-debt] Replace IdentityManager with AuthState and DeviceConfig`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Migrate ShareRoutes.kt to use AuthState and DeviceConfig

Since `DeviceConfig` is a KoinComponent or injected elsewhere, we need to access the `DeviceConfig.googleSub` / `DeviceConfig.identityHash` and `com.dexstudios.dex.auth.AuthState` (which backs `DeviceManager`).

In `ShareRoutes.kt`, replace the `IdentityManager` checks in `/prepare-upload`:

```kotlin
                val koin = org.koin.core.context.GlobalContext.get()
                val deviceConfig = koin.get<com.dexstudios.dex.core.network.DeviceConfig>()
                
                val isAutoTrusted = !token.isNullOrEmpty() && (token == deviceConfig.identityHash || (deviceConfig.googleSub.isNotEmpty() && token == deviceConfig.googleSub))
                
                val pairedTokens = com.dexstudios.dex.auth.AuthState.pairedTokens.value
                val isPaired = !token.isNullOrEmpty() && pairedTokens[req.info.fingerprint] == token
```

Remove `import com.dexstudios.dex.core.network.auth.IdentityManager` from `ShareRoutes.kt`.

**Verify**: Make these replacements and ensure there are no compilation errors in `ShareRoutes.kt`.

### Step 2: Delete IdentityManager.kt and fix other references

Delete `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/auth/IdentityManager.kt`.

If any other files (like `WebSocketConnectionManager.kt` or `ServerEngine.kt`) reference `IdentityManager.initialize()`, remove those calls. Koin will handle `DeviceConfig` initialization.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `IdentityManager.kt` is deleted.
- [ ] `ShareRoutes.kt` uses `DeviceConfig` and `AuthState` instead.
- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:
- `GlobalContext.get()` is not available or throws errors in Ktor routes (you may need to inject it via route parameters).
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Any future server routes requiring authentication must use `AuthState` (for pairing state) and `DeviceConfig` (for identity state).
