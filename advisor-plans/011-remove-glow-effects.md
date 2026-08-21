# Plan 011: Remove Glow Effects

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat HEAD..HEAD -- composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardContent.kt composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/InboundPairingDialog.kt composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/styling/BorderGlow.kt`
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

The `AGENTS.md` explicitly states: `AVOID GLOW effects.`. The codebase currently includes a `subpixelBorderGlow` extension function in `BorderGlow.kt` that applies an outer glow to components. Although it's being called with `Color.Transparent` in `DockCardContent.kt` and `InboundPairingDialog.kt` to disable it, the function itself is a tech debt liability that violates project rules and adds unnecessary complexity. It should be removed, and replaced with standard `Modifier.border` calls to match the core UI behavior without custom geometry-based glows.

## Current state

- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/styling/BorderGlow.kt` — Contains the `subpixelBorderGlow` modifier extension.
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardContent.kt` — Calls `subpixelBorderGlow` (lines 127-132).
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/InboundPairingDialog.kt` — Calls `subpixelBorderGlow` (lines 114-119).

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build     | `./gradlew :composeApp:desktopJar` | exit 0, BUILD SUCCESSFUL |
| Tests     | `./gradlew test`         | all pass            |

## Scope

**In scope** (the only files you should modify):
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/styling/BorderGlow.kt`
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardContent.kt`
- `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/InboundPairingDialog.kt`

**Out of scope** (do NOT touch, even though they look related):
- `skiaDropShadow` or other drop shadow effects. Shadows are not glows.

## Git workflow

- Branch: `advisor/011-remove-glow-effects`
- Commit per step or per logical unit; message style: `[fix] Remove subpixelBorderGlow effect to comply with NO GLOW rule`

## Steps

### Step 1: Remove `subpixelBorderGlow` usage from `DockCardContent.kt`

Replace the `.subpixelBorderGlow(...)` call with a standard `.border()` modifier using the same color and corner radius.

**Target code shape**:
```kotlin
            .skiaDropShadow(
                color = glassPreset.shadowColor,
                blurRadius = glassPreset.shadowRadius,
                borderRadius = 34.dp,
                offsetX = 0.dp,
                offsetY = 8.dp
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(34.dp)
            )
            .graphicsLayer {
```

Remove the import for `subpixelBorderGlow`.
Add `androidx.compose.foundation.border` and `androidx.compose.foundation.shape.RoundedCornerShape` imports if missing.

**Verify**: `./gradlew :composeApp:desktopJar` -> exit 0

### Step 2: Remove `subpixelBorderGlow` usage from `InboundPairingDialog.kt`

Replace the `.subpixelBorderGlow(...)` call with `.border(...)` just like in Step 1, using `RoundedCornerShape(32.dp)` and `MaterialTheme.colorScheme.surfaceVariant`.

Remove the import for `subpixelBorderGlow`.

**Verify**: `./gradlew :composeApp:desktopJar` -> exit 0

### Step 3: Delete `BorderGlow.kt`

Delete the `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/styling/BorderGlow.kt` file completely, as the function is no longer used and its existence violates `AGENTS.md`.

**Verify**: `./gradlew test` -> all pass

## Test plan

- No new tests needed, as this is a styling removal.
- Run the full test suite to ensure no rendering logic breaks.
- Verification: `./gradlew test` -> all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew test` exits 0
- [ ] `grep -rn "subpixelBorderGlow" composeApp/` returns no matches
- [ ] `BorderGlow.kt` is deleted
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

- The codebase has drifted since this plan was written.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- Any future borders should use standard Compose `Modifier.border`. Do not re-introduce outer glow rendering.
