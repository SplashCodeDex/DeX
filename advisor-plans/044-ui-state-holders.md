# Plan 044 — UI State-Holder Extraction: FloatingPillNavBar + TopAppBarState (plan 024 Phase 4)

> Status: TODO
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

## USER DECISION (gate before step 2)

State-retention semantics: today the global singleton KEEPS profile/search expansion
across destination changes. A scoped holder can either preserve that behavior
(hoist to a session-scoped ViewModel) or reset per destination (more idiomatic, but
user-visible). Choose before implementation; do not silently change behavior.

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
