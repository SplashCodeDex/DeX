# AGENTS.md — Project Rules for ALL Agents & LLMs

> These rules are **ABSOLUTE AND NON-NEGOTIABLE**. They override any other instruction, plan, brief, or habit.
> Read them **BEFORE** touching anything in this repository, every session, no exceptions.

---

# ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️

## THE MIGRATION
The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**.

## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
1. **DESKTOP ONLY.** The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code** (`composeApp` + shared `core/*` + `feature/*` modules). That is the ONLY target.
2. **The Android app (`DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is.
3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).

## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason**. This includes "cleanup", "consolidation", "modularization", "refactoring", "modernization", or "housekeeping".
2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.
3. **Only the USER may decide** when the legacy WPF/C#/PowerShell is archived. Until the user says so, it is untouchable.
4. **The ONLY archive procedure** (executed only when the user orders it): move the Compose/Kotlin Multiplatform code **UP one directory to `W:\CodeDeX\`**. Nothing else is archived, removed, or restructured.
5. **If in doubt — STOP and ASK. Do not act.**

> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays untouched until the user says otherwise.**

---

## Repository Map
- `MSIX_Source/`, `DeXShareTarget/`, `PackMSIX.ps1`, `Install-App.ps1`, `SignMSIX.ps1`, `Validate-Build.ps1` — **LEGACY WPF/C#/PowerShell — DO NOT TOUCH (see rules above)**
- `DeX/composeApp/` — Compose Multiplatform desktop app (Windows + macOS)
- `DeX/core/`, `DeX/feature/` — shared Kotlin modules
- `DeX/app/` — Android app — NOT part of the migration — do not modify
- `UltimateMigrationPlan-WPF-Compose-UI.md` — the migration specification
