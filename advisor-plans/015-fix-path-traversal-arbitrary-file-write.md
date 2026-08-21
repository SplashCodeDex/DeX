# Plan 015: Fix path traversal vulnerability in ShareRoutes

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

The file upload endpoint allows attackers to specify absolute paths using drive letters (like `C:/Windows/...`) because the `safeRelative` check only filters `..` but ignores absolute path prefixes. This allows an attacker to write malware to the Startup folder or overwrite sensitive system files on the host PC. By validating that the resolved path is strictly a descendant of the target downloads directory, we close this vulnerability.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/server/routes/ShareRoutes.kt` — handles file uploads and contains the path traversal vulnerability.

Excerpt from `ShareRoutes.kt:161-174`:
```kotlin
            val safeRelative = fileMeta.relativePath?.let { path ->
                val sanitized = path.replace("\\", "/").trim('/')
                if (sanitized.contains("..")) null else sanitized
            }

            val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
            downloadsFolder.mkdirs()

            var destFile = if (safeRelative.isNullOrEmpty()) {
                File(downloadsFolder, safeFileName)
            } else {
                val file = File(downloadsFolder, safeRelative)
                file.parentFile?.mkdirs()
                file
            }
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
- Any modifications to the data transfer protocol or endpoint URLs.

## Git workflow

- Branch: `advisor/015-fix-path-traversal-arbitrary-file-write`
- Commit message: `[fix] Prevent path traversal vulnerability in ShareRoutes`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Secure path resolution in ShareRoutes.kt

Update the `destFile` calculation to use `java.nio.file.Path.resolve` and `normalize`, and explicitly check that the resolved path starts with the `downloadsFolder` path.

Replace lines 161-174 with:
```kotlin
            val downloadsFolder = File(System.getProperty("user.home"), "Downloads/DeX")
            downloadsFolder.mkdirs()

            var destFile = if (fileMeta.relativePath.isNullOrEmpty()) {
                File(downloadsFolder, safeFileName)
            } else {
                val relativePath = fileMeta.relativePath.replace("\\", "/")
                if (relativePath.contains("..")) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val resolvedPath = downloadsFolder.toPath().resolve(relativePath).normalize()
                if (!resolvedPath.startsWith(downloadsFolder.toPath())) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val file = resolvedPath.toFile()
                file.parentFile?.mkdirs()
                file
            }
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew :composeApp:desktopTest` exits 0
- [ ] Path resolution verifies that the result is inside `downloadsFolder`.
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:
- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Any future features related to custom download directories must still enforce this `startsWith` boundary check to prevent absolute path injection.
