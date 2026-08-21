# Plan 012: Remove Hardcodings from Device List

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat HEAD..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/DeviceListPanel.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `HEAD`, 2026-08-21

## Why this matters

The `AGENTS.md` explicitly states: `AVOID Hardcodings`. The `DeviceListPanel.kt` file currently features hardcoded WAN profile scaffolds (`Ama Serwaa`, `Akua Donkor`, `Kwame Asante`) in `defaultWanPlaceholders()` and a hardcoded user profile row (`DeXStudios`, `dexify@dex.net`, `joe_avatar`) to fake a connected state. These dummy data values cause confusion during actual operations and violate the rule against hardcoding state. We must remove these placeholders so the UI reflects actual data provided by the engine.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/DeviceListPanel.kt`:
  - `fun defaultWanPlaceholders()` contains hardcoded devices.
  - The `DeviceListPanel` composable calls `ProfileListItemRow` and `LocalDeviceItemRow` with hardcoded string contents ("DeXStudios", "dexify@dex.net", "Windows").

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test`         | all pass            |

## Scope

**In scope** (the only files you should modify):
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/DeviceListPanel.kt`
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/MainMenuColumn.kt` (to remove calls to `defaultWanPlaceholders` if used there)

**Out of scope** (do NOT touch, even though they look related):
- Network engine data layers. Only remove the hardcodings from the UI presentation.

## Git workflow

- Branch: `advisor/012-remove-device-list-hardcodings`
- Commit per step or per logical unit; message style: `[fix] Remove hardcoded mock data in DeviceListPanel`

## Steps

### Step 1: Delete `defaultWanPlaceholders` function

In `DeviceListPanel.kt`, delete the `defaultWanPlaceholders` function and its mock data. Search for any callers of this function (e.g. in `MainMenuColumn.kt`) and replace them with an emptyList().

**Verify**: `./gradlew :composeApp:desktopJar` -> exit 0

### Step 2: Remove Hardcoded `ProfileListItemRow` details

Modify `ProfileListItemRow` in `DeviceListPanel.kt` to accept dynamic data instead of hardcoding "DeXStudios", "dexify@dex.net", and `joe_avatar`.
Wait, if there's no dynamic profile object available yet, you can remove the `ProfileListItemRow` usage completely from `DeviceListPanel`, or just accept strings as parameters and pass empty or actual system user strings from the caller. Since we don't have a profile system implemented in the engine UI model, simply remove the `ProfileListItemRow` composable and its invocation from `DeviceListPanel`.

Also, remove the `LocalDeviceItemRow` and its invocation if it's purely hardcoded. If you must keep a representation of the local machine, use `System.getProperty("user.name")` and `java.net.InetAddress.getLocalHost().hostName`, but it's safer to just remove the hardcoded fake rows entirely until the real local device model is injected. Let's delete the `ProfileListItemRow` and `LocalDeviceItemRow` composables and their `item { ... }` blocks from the `LazyColumn`.

**Verify**: `./gradlew :composeApp:desktopJar` -> exit 0

## Test plan

- No new tests needed.
- Verification: `./gradlew test` -> all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew test` exits 0
- [ ] Hardcoded names ("Ama Serwaa", "DeXStudios") are completely removed.
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

- The codebase has drifted since this plan was written.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Future local machine or profile details must be supplied dynamically by the `PairingEngine` or a similar state holder, not embedded in the View.
