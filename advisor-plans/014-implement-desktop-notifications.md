# Plan 014: Implement Desktop Notifications

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat HEAD..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/engine/DesktopPlatformEngine.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `HEAD`, 2026-08-21

## Why this matters

The `DesktopPlatformEngine` currently prints pairing request and file transfer notifications to `println()` instead of bubbling them to the user. As part of a desktop application, this prevents the user from knowing when an Android device attempts to pair or send a file if the UI is hidden. Implementing standard `java.awt.SystemTray` notifications provides actual feedback and closes the loop on core functionality.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/engine/DesktopPlatformEngine.kt`:
  ```kotlin
  override fun showPairingRequestNotification(alias: String) {
      println("[DesktopPlatformEngine] Pairing request from $alias")
      // TODO: Show desktop notification (java.awt.SystemTray or local push)
  }
  ```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :core:network:jvmJar` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test`         | all pass            |

## Scope

**In scope** (the only files you should modify):
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/engine/DesktopPlatformEngine.kt`

**Out of scope** (do NOT touch, even though they look related):
- Do not implement custom Compose-based toast UI. Stick to the native system tray notification as defined by the `TODO` and Java standard library.

## Git workflow

- Branch: `advisor/014-implement-desktop-notifications`
- Commit per step or per logical unit; message style: `[feature] Implement system tray notifications in DesktopPlatformEngine`

## Steps

### Step 1: Implement `java.awt.SystemTray` notifications

In `DesktopPlatformEngine.kt`, add a private helper method `showSystemNotification(title: String, message: String, type: java.awt.TrayIcon.MessageType)`.
```kotlin
    private fun showSystemNotification(title: String, message: String, type: java.awt.TrayIcon.MessageType) {
        if (!java.awt.SystemTray.isSupported()) return
        val tray = java.awt.SystemTray.getSystemTray()
        // If there is already a tray icon added, we can use it, else we need a temporary one
        val trayIcons = tray.trayIcons
        if (trayIcons.isNotEmpty()) {
            trayIcons.first().displayMessage(title, message, type)
        } else {
            // Optional: Create a temporary icon just to show the message, or rely on the fact that Main sets one up.
            // For now, if no icon exists, just println
            println("[$title] $message")
        }
    }
```
Update `showPairingRequestNotification` and `showIncomingFileNotification` to call this method. Remove the `// TODO` comments.

**Verify**: `./gradlew :core:network:jvmJar` -> exit 0

## Test plan

- Verification: `./gradlew test` -> all pass.

## Done criteria

- [ ] `./gradlew :core:network:jvmJar` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `TODO: Show desktop notification` comment is gone.
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

- The codebase has drifted since this plan was written.

## Maintenance notes

- If a proper native notification library is added later, this can be swapped out easily.
