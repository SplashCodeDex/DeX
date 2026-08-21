# Plan 019: Replace hardcoded Windows OS-specific binaries in cross-platform codebase

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 7f8c1de..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `7f8c1de`, 2026-08-21

## Why this matters

The Compose Multiplatform desktop app targets both Windows and macOS. However, `AdbManager.kt` hardcodes the download URL for the Windows version of `platform-tools` (`platform-tools-latest-windows.zip`) and looks specifically for `adb.exe`. When run on a Mac, the app will download Windows executables, fail to run them, and silently fail Android device communication. By adding OS detection, the app can correctly provision `platform-tools` across platforms.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt` — manages ADB binary download and execution.

Excerpt from `AdbManager.kt:15-17`:
```kotlin
    private val adbExeFile = File(toolsDir, "platform-tools/adb.exe")

    private const val ADB_DOWNLOAD_URL = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopJar`           | exit 0              |
| Tests     | `./gradlew :composeApp:desktopTest`  | all pass            |

## Scope

**In scope**:
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/desktop/AdbManager.kt`

**Out of scope**:
- Modifications to ADB connection logic or the fallback logic.

## Git workflow

- Branch: `advisor/019-replace-hardcoded-windows-binaries`
- Commit message: `[tech-debt] Add macOS support for ADB provisioning in AdbManager`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add OS detection for URL and executable name

Replace the hardcoded `adbExeFile` and `ADB_DOWNLOAD_URL` with dynamic properties based on the OS.

```kotlin
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val adbExeName = if (isWindows) "adb.exe" else "adb"
    private val adbExeFile = File(toolsDir, "platform-tools/$adbExeName")
    
    private val downloadOsSuffix = if (isWindows) "windows" else "darwin"
    private val ADB_DOWNLOAD_URL = "https://dl.google.com/android/repository/platform-tools-latest-$downloadOsSuffix.zip"
```

### Step 2: Ensure executable permissions on Unix

When extracting the ZIP file, the extracted binaries will lose their executable flag. On macOS/Linux, we must explicitly restore it.

In the `ZipInputStream` extraction loop, immediately after closing the file output stream (`zis.copyTo(fos)`), add a check for the `adb` executable:

```kotlin
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        if (!isWindows && entry.name.endsWith("adb")) {
                            newFile.setExecutable(true)
                        }
                    }
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `AdbManager.kt` dynamically determines the correct download URL (`windows` vs `darwin`).
- [ ] `AdbManager.kt` looks for `adb` vs `adb.exe` depending on the OS.
- [ ] On non-Windows platforms, `setExecutable(true)` is called on the extracted `adb` binary.
- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:
- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- If Linux support is ever added, `downloadOsSuffix` will need to support `"linux"` as well.
