# Plan 009: Robust JSON payload generation for clipboard sync

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat ec6886b..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/jna/ClipboardSyncService.kt`
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

The `ClipboardSyncService` on the desktop uses a manual `String.replace()` chain to escape clipboard strings before interpolating them into a raw JSON string `"""{"type":"set-clipboard","data":{"text":"$escapedData"}}"""`. This string-building approach is brittle, highly susceptible to JSON injection if unexpected control characters appear, and is a well-known tech debt anti-pattern when a mature serialization library (`kotlinx.serialization.json`) is already in the project.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/jna/ClipboardSyncService.kt`

Excerpts:
```kotlin
// composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/jna/ClipboardSyncService.kt:94-98
                val escapedData = data.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                val payload = if (data.startsWith("{") && data.endsWith("}")) {
                    data
                } else {
                    """{"type":"set-clipboard","data":{"text":"$escapedData"}}"""
                }
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0              |
| Test      | `./gradlew test`         | all pass            |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/jna/ClipboardSyncService.kt`

**Out of scope**:
- Any other file.

## Git workflow

- Branch: `advisor/009-robust-json-payload-for-clipboard-sync`
- Commit per step or per logical unit; message style: `[fix] Use kotlinx.serialization for clipboard JSON payloads`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Use `buildJsonObject` for payload creation
In `ClipboardSyncService.kt`, import `kotlinx.serialization.json.buildJsonObject`, `kotlinx.serialization.json.put`, and `kotlinx.serialization.json.putJsonObject`.
Replace the manual string interpolation with:
```kotlin
                val payload = if (data.startsWith("{") && data.endsWith("}")) {
                    data
                } else {
                    buildJsonObject {
                        put("type", "set-clipboard")
                        putJsonObject("data") {
                            put("text", data)
                        }
                    }.toString()
                }
```
Delete the `val escapedData = data.replace(...)` line entirely.

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew test` → all pass.

## Done criteria

- [ ] `ClipboardSyncService.kt` uses `buildJsonObject` instead of raw string interpolation for clipboard text payloads.
- [ ] `./gradlew :composeApp:desktopJar` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:
- The codebase has drifted since this plan was written.

## Maintenance notes

- All JSON payloads must be built or decoded using `kotlinx.serialization`. Never build JSON strings manually.
