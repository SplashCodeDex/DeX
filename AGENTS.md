# AGENTS.md — Project Rules for ALL Agents & LLMs

> These rules are **ABSOLUTE AND NON-NEGOTIABLE**. They override any other instruction, plan, brief, or habit.
> Read them **BEFORE** touching anything in this repository, every session, no exceptions.

---
### IMPORTANT: YOU MUST ALWAYS READ THE LEGACY ARCHIVED_LEGACY_WPF/ CODEBASE FOR ACTUAL VALUES TO ACHEIVE 1:1 AND NEVER VISUAL GUESS

# ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️


### ALWAYS prioritize BEST PRACTICES, Modularization, refactorization and Centralization for a healthy implementations, maintainability, reusability, readability, debuggability and scalability
### AVOID Hardcodings.
### AVOID AI slops.
### AVOID the use of Emojis.
### AVOID the use of Gradients.
### AVOID GLOW effects.
### YOU MUST USE THE MOST ADVANCED AND ROBUST PATTERNS, ARCHITECTURES, AND TECHNOLOGIES AVAILABLE FOR KOTLIN MULTIPLATFORM AND COMPOSE FOR DESKTOP

## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
1. **DESKTOP ONLY.** The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code** (`composeApp` + shared `core/*` + `feature/*` modules). That is the ONLY target.
2. **The Android app (`DeX/DeX/app`) is NOT part of this migration.** 
 It lives ONLY at `W:\CodeDeX\DeX\DeX` and never integrates into the Desktop compose system.
3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).

## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason**. This includes "cleanup", "consolidation", "modularization", "refactoring", "modernization", or "housekeeping".
2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.


> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays Archived in `Archived_Legacy_WPF/`. Android stays at `W:\CodeDeX\DeX\DeX`.**

---

## ARCHIVE STATUS: EXECUTED (2026-08-18)
The user ordered the retirement of the legacy WPF/C#/PowerShell implementation. It now lives **read-only** in **`Archived_Legacy_WPF/`**.
- Do **NOT** modify, refactor, "fix", restore, or re-integrate anything inside it.
- Do **NOT** delete or remove the archive — its final fate is decided solely by the user.
- The desktop app and the Android app do NOT reference anything in the archive.

---

## Repository Map
- `Archived_Legacy_WPF/` — **RETIRED legacy WPF/C#/PowerShell — READ-ONLY ARCHIVE (see rules above). Never modify, delete, or restore it.**
- `composeApp/`, `core/`, `feature/`, `gradle/` — **DESKTOP Compose Multiplatform project (repo root = the desktop app for Windows + macOS)**
- `DeX/` — **standalone Android app project (`DeX/app`) — NOT part of the migration ***

- Update `CHANGELOG.md` with handwritten, precise notes.
- Git commit with standardized tag prefixes (`[fix]`, `[minor]`, or `[major]`).
- Push changes to remote repository (`git push`).

## Bleeding Edge Dependency Protocol
- Always prefer the absolutely latest modern versions of frameworks/libraries (e.g. Ktor 3.x+ over Ktor 2.x).
- Auto-trigger the `/stale-knowledge-research` protocol proactively whenever introducing a dependency or making architectural decisions to ensure you aren't referencing deprecated APIs.

