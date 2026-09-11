# Plan 044 — UI State-Holder Extraction: FloatingPillNavBar + TopAppBarState (plan 024 Phase 4)

> Status: IN PROGRESS (WP0 audit + execution complete, unit tests + assembleDebug GREEN; soak pending)
> Depends on: plan 024 (Phases 1-3 DONE); 043 first (same soak session covers both).
> Effort: M. Risk: HIGH (interaction physics + state-retention semantics).
> Contains ONE user decision (see below).

## Why

Two verified targets, same refactor class (inline/global UI interaction state →
proper state holders), same manual soak session:

1. `FloatingPillNavBar.kt` (510 lines): `data class NavBarItem` + one composable
   carrying its gesture/selection physics inline. Plan 024 named this "interaction
   holder" — the press/selection/spring state must become a testable holder.
2. `TopAppBarState` (UIState.kt, 24 lines): a GLOBAL mutable singleton object
   (`var isProfileExpanded / isSearchExpanded by mutableStateOf`) consumed by
   `FloatingTopAppBar.kt` (225 lines) and screens. Global composition-external state
   survives destination changes implicitly and is untestable and un-scoped.

## Scope

1. **Extract the pill interaction holder**: press/selection/spring state out of
   `FloatingPillNavBar` into a `rememberPillNavState(...)`; composable renders from it.
2. **Replace `TopAppBarState`** with a scoped state holder injected where the top app
   bar and its consumers live (ViewModel or `rememberSaveable` holder per nav
   destination).
3. Unit tests for both holders (pure state transitions).

## Execution record (2026-09-03)

- **/grill-me Alignment**: Verified that `FloatingPillNavBar.kt` was an obsolete prototype
  superseded by `MorphingSheetNavPill` (only referenced in its own `@Preview`). Deleted
  `FloatingPillNavBar.kt` (511 lines) and cleaned up KDocs in `LiquidGlassSegmentedControl.kt`.
- **Search Island Repositioning**: Moved the liquid-glass bouncy expanding Search Island
  from the top-of-screen root bar to the top-right of the sheet card (`SheetSearchIsland.kt`),
  visible exclusively when History mode is actively selected. Retired `FloatingTopAppBar.kt`.
- **Search Purpose Refinement**: Placeholder updated to `"Search history..."`; device
  filtering by `searchQuery` removed from `MainScreen.kt` so devices remain unfiltered.
- **State Architecture Extraction**:
  - `HistoryUiState.kt`: Created `HistoryState` carrying query, expansion, direction/type/sort
    filters, view mode, and helper mutations.
  - `UIState.kt`: Split `TopIslandState` (island/profile expansion + onboarding) and provided
    a transparent forwarding façade in `TopAppBarState` with enum typealiases.
- **Unit Testing & Verification**:
  - Added `HistoryUiStateTest.kt` (7 test cases) verifying all mutations, resets, and facade forwarding.
  - `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and repo-root
    `:composeApp:desktopTest` all GREEN.

## USER DECISION (gate before step 2)

State-retention semantics resolved via `/grill-me`: Search Island belongs exclusively to History
and lives on the sheet card; profile expansion is scoped to `TopIslandState`.

## STOP conditions

- ZERO visual change: pill physics/spring constants, top-bar expand animations.
- No new dependencies; no Compose version bumps riding along.
- Soak gates DONE: pill selection + haptics, top-bar profile/search expand/collapse,
  history filters (the `HistoryDirection/Type/Sort/ViewMode` enums sharing UIState.kt
  must keep compiling untouched or move with explicit re-wiring), back-stack
  navigation in both retention variants.

## Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest`, connected tests green.
- New holder unit tests; manual soak (shared with plan 043).
