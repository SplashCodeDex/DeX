# Plan 001: Validate zip entry paths during ADB extraction (Zip Slip fix)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 6c8ae4a..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `6c8ae4a`, 2026-08-21

## Why this matters

During ADB fallback installation, `AdbManager.kt` downloads and extracts `platform-tools-latest-windows.zip`. It naively extracts zip entries using `File(toolsDir, entry.name)` without verifying if `entry.name` contains directory traversal characters (e.g. `../`). A malicious or hijacked ZIP could exploit this Zip Slip vulnerability to write arbitrary files anywhere on the local filesystem, leading to code execution. This fix adds a simple canonical path validation check before extraction.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt` — handles the extraction (line ~58).

Excerpt of current state:
```kotlin
// composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt:56
var entry = zis.nextEntry
while (entry != null) {
    val newFile = File(toolsDir, entry.name)
    if (entry.isDirectory) {
        newFile.mkdirs()
    } else {
        newFile.parentFile?.mkdirs()
        FileOutputStream(newFile).use { fos ->
            zis.copyTo(fos)
        }
    }
    zis.closeEntry()
    entry = zis.nextEntry
}
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew :composeApp:desktopClasses` | exit 0              |
| Run app   | `./gradlew :composeApp:run` | exit 0 (app launches) |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt`

**Out of scope**:
- Changing the download URL.
- Modifying how the rest of the application uses ADB.

## Git workflow

- Branch: `advisor/001-fix-adb-zip-slip`
- Commit message: `[fix] Prevent Zip Slip in AdbManager extraction`

## Steps

### Step 1: Add canonical path validation

In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt`, update the ZIP extraction loop to verify that the extracted file resolves to a child of the `toolsDir`. 

Target code shape:
```kotlin
val canonicalToolsDir = toolsDir.canonicalPath
var entry = zis.nextEntry
while (entry != null) {
    val newFile = File(toolsDir, entry.name)
    val canonicalDestination = newFile.canonicalPath
    if (!canonicalDestination.startsWith(canonicalToolsDir)) {
        throw SecurityException("Zip entry is outside of the target directory: ${entry.name}")
    }
    
    if (entry.isDirectory) {
        newFile.mkdirs()
    } else {
// ...
```

**Verify**: `./gradlew :composeApp:desktopClasses` → `BUILD SUCCESSFUL`

## Test plan

- Ensure the application still builds successfully.
- Manual verification: The app should successfully download and extract ADB when it is missing from the PATH without throwing the `SecurityException`.

## Done criteria

- [ ] `./gradlew :composeApp:desktopClasses` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:

- The code at the locations in "Current state" doesn't match the excerpts.
- You encounter unresolved compilation errors due to `canonicalPath`.

## Maintenance notes

If the extraction mechanism is changed to use a standard library function in the future, ensure that the standard library performs Zip Slip validation (not all do by default).
