# Plan 017: Fix unbounded session maps Denial of Service

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 7f8c1de..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `7f8c1de`, 2026-08-21

## Why this matters

Upload sessions in `ShareRoutes.kt` are stored in a `ConcurrentHashMap` and only removed when the upload completes. A malicious actor on the local network could repeatedly call `/prepare-upload` without ever completing the upload, causing these maps to grow infinitely until the server crashes with an OutOfMemoryError (DoS). Adding an expiring cache or a scheduled cleanup job will prevent this.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt` — maintains `activeUploadSessions` and `activeUploadSessionsProgress`.

Excerpt from `ShareRoutes.kt:36-40`:
```kotlin
// Match the C# implementation state variables
val activeUploadSessions = ConcurrentHashMap<String, PrepareUploadRequestDto>()
val activeUploadSessionsProgress = ConcurrentHashMap<String, Int>()
private val shareRoutesFileLock = Any()
private val shareRouteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopJar`           | exit 0              |
| Tests     | `./gradlew :composeApp:desktopTest`  | all pass            |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt`

**Out of scope**:
- Modifications to `TransferStateMonitor` which is a separate tracking layer.

## Git workflow

- Branch: `advisor/017-fix-unbounded-session-maps-dos`
- Commit message: `[fix] Prevent DoS by cleaning up stale upload sessions`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add automated cleanup to session maps

Since Google Guava (`CacheBuilder`) is not guaranteed to be present, we can launch a periodic cleanup coroutine within `shareRouteScope` that sweeps out old sessions. We need to track the timestamp when sessions are created. 
Wrap the session data in a class that holds the timestamp.

Update the map definitions in `ShareRoutes.kt`:

1. Add a data class for timestamped sessions.
2. Update `activeUploadSessions` to store this wrapper.
3. Launch a repeating job in `shareRouteScope` to remove sessions older than 10 minutes.

```kotlin
data class SessionEntry(val request: PrepareUploadRequestDto, val createdAt: Long = System.currentTimeMillis())

val activeUploadSessions = ConcurrentHashMap<String, SessionEntry>()
val activeUploadSessionsProgress = ConcurrentHashMap<String, Int>()
private val shareRoutesFileLock = Any()
private val shareRouteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).apply {
    launch {
        while (true) {
            delay(60_000) // 1 minute
            val now = System.currentTimeMillis()
            val iterator = activeUploadSessions.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.createdAt > 10 * 60_000) { // 10 mins
                    iterator.remove()
                    activeUploadSessionsProgress.remove(entry.key)
                }
            }
        }
    }
}
```

Then update all usages of `activeUploadSessions`:
- In `/prepare-upload`: `activeUploadSessions[sessionId] = SessionEntry(req)`
- In `/upload`: `val sessionReq = activeUploadSessions[sessionId]?.request`

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew :composeApp:desktopTest` exits 0
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:
- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- If larger files are supported that pause mid-upload for >10 minutes, the cleanup logic will need to update `createdAt` or track `lastActivityAt` instead.
