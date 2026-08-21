# Plan 006: Serialize WebSocket outgoing messages

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat ec6886b..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/WebSocketConnectionManager.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: MED
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `ec6886b`, 2026-08-21

## Why this matters

The `WebSocketConnectionManager.kt` currently calls `session.send(Frame.Text(json))` directly from multiple coroutines across different routes (e.g., FileExplorer, Clipboard, Pairing). Ktor's `WebSocketSession.send` is not thread-safe and can throw a `CancellationException` if invoked concurrently, crashing the WebSocket connection or silently dropping messages. Serializing sends via a Kotlin `Mutex` per session ensures robust, thread-safe communication.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/WebSocketConnectionManager.kt` — manages WebSocket connections.

Excerpts:
```kotlin
// core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/WebSocketConnectionManager.kt:11-12
object WebSocketConnectionManager {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
```

```kotlin
// core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/WebSocketConnectionManager.kt:22-30
    suspend fun sendRequest(fingerprint: String, json: String): Boolean {
        val session = sessions[fingerprint] ?: return false
        return try {
            session.send(Frame.Text(json))
            true
        } catch (e: Exception) {
            false
        }
    }
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0              |
| Test      | `./gradlew test`         | all pass            |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/WebSocketConnectionManager.kt`

**Out of scope**:
- `WebSocketRoutes.kt` or any other caller of `WebSocketConnectionManager`.

## Git workflow

- Branch: `advisor/006-serialize-websocket-outgoing-messages`
- Commit per step or per logical unit; message style: `[fix] Serialize WebSocket sends with Mutex in WebSocketConnectionManager`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Wrap WebSocketSession with a Mutex
In `WebSocketConnectionManager.kt`, define a private data class `SessionHolder` holding the `WebSocketSession` and a `Mutex`.
```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class SessionHolder(
    val session: WebSocketSession,
    val mutex: Mutex = Mutex()
)
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

### Step 2: Update the `sessions` map and registration
Change `private val sessions = ConcurrentHashMap<String, WebSocketSession>()` to `ConcurrentHashMap<String, SessionHolder>()`.
Update `register` to store `SessionHolder(session)`.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

### Step 3: Serialize `send` calls using the Mutex
In `sendRequest`, `broadcast`, and `broadcastToPaired`, wrap the `session.send` call in `mutex.withLock { ... }`.
For example, in `sendRequest`:
```kotlin
        val holder = sessions[fingerprint] ?: return false
        return try {
            holder.mutex.withLock {
                holder.session.send(Frame.Text(json))
            }
            true
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- No new tests strictly required as this is an internal concurrency bug fix.
- Verification: `./gradlew test` → all pass.

## Done criteria

- [ ] `sendRequest`, `broadcast`, and `broadcastToPaired` use `Mutex.withLock` to serialize sends.
- [ ] `./gradlew :composeApp:desktopJar` exits 0.
- [ ] `./gradlew test` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:
- The codebase has drifted since this plan was written.
- The `Mutex` implementation causes deadlocks in tests or build failures.

## Maintenance notes

- Future methods added to `WebSocketConnectionManager` that send frames MUST also use the `holder.mutex.withLock` block.
