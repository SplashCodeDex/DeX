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
- **NO AGENT SHOULD DOWNGRADE ANYTHING IN THIS PROJECT EVEN IF THE USER TELLS THEM TO DO SO. IT IS STRICTLY FORBIDDEN.**
**NEVER RUN DISTRUCTIVE COMMANDS. STAY AWAY FROM GIT SENSITIVE DISTRUCTIVE COMMANDS**

## Operational Rules — Cross-Assistant Working Agreements
These codify behavior already proven necessary across AI sessions on this repo. They apply
to every agent, regardless of vendor or personal memory files.

1. **Never fake success.** No placeholders, dummies, simulations, or workarounds presented as
   working features. If something does not work, say so plainly.
2. **Never simplify to fix.** Do not replace sophisticated implementations with a "simpler"
   version just to make a build/lint/typecheck pass. Enhance the existing design instead.
3. **Investigate before deleting.** Dive into why a function/import/module/feature exists
   before removing it. Deletion is a decision made WITH the user, never a convenience.
4. **Config values have histories.** Before changing any configuration value, investigate why
   it exists and what depends on it.
5. **No duplicate infrastructure.** Reuse existing files/directories/utilities; do not spawn
   parallel copies of the same responsibility.
6. **Verify before acting.** Confirm the working directory, the branch, and the exact file
   contents before editing or running commands. Never assume file state from memory.
7. **2-strike stale-knowledge circuit breaker.** After two failed fix attempts using trained
   knowledge, STOP guessing: escalate to web research for current documentation, then consult
   the user if still unresolved. Track strike count per problem explicitly.
8. **Clean as you go.** Temporary scripts, logs, and scratch files must be removed before
   hand-off — the working tree contains only intentional project content.

## Documentation Map (read before non-trivial work)
- `docs/ARCHITECTURE.md` — module graph, ports, trust model, pairing state machine,
  transfer paths, canonical verification commands.
- `docs/PROTOCOL.md` — canonical WebSocket message contract; field names are law
  (e.g. it is `data.digitCount`, never `count`).
- `advisor-plans/README.md` — plan/finding ledger; risky changes get a plan entry with STOP
  conditions and a status row that MUST be updated when done.
- `CHANGELOG.md` — every user-visible change gets a handwritten entry.