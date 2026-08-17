# Project: WPF to Compose Multiplatform UI Migration Plan

> # ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️
>
> ## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
> 1. **DESKTOP ONLY.** The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**. The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code**. That is the ONLY target.
> 2. **The Android app (`DeX/DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is — it lives ONLY at `W:\CodeDeX\DeX\DeX` and never moves during archiving.
> 3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).
>
> ## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
> 1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason**.
> 2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.
> 3. **Only the USER may decide** when the legacy WPF/C#/PowerShell is archived. Until the user says so, it is untouchable.
> 4. **The ONLY archive procedure** (executed only when the user orders it): move the Compose/Kotlin Multiplatform code **UP one directory — from `W:\CodeDeX\DeX\DeX` to `W:\CodeDeX\DeX`**. The Android app stays at `W:\CodeDeX\DeX\DeX`. Nothing else is archived, removed, or restructured.
> 5. **If in doubt — STOP and ASK. Do not act.**
>
> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays untouched until the user says otherwise. Android stays at `W:\CodeDeX\DeX\DeX`.**

## Architecture
- Source UI: WPF / C# / PowerShell floating docked card interface with acrylic blur, custom window chrome, Win32 P/Invoke taskbar docking, elastic storyboards, quick action buttons, and embedded file explorer.
- Target UI: Compose Multiplatform (Desktop) using Skiko transparent undecorated windowing, `io.github.kyant0:backdrop` (v2.0.0) liquid glass rendering, Skia drop shadow shaders, Kotlin Coroutines & Jetpack Compose animation physics (spring/tween/easing), and Jetpack Compose component hierarchy.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Window Shell & Transparency | Undecorated, per-pixel alpha transparency, always-on-top, taskbar-hidden utility window | M1 | Survey |
| 2 | Bottom-Right Docking & Taskbar Work Area | Exact resting coordinates (13px right gap, 38px above taskbar) across multi-monitor & 4 taskbar orientations | M1 | Survey |
| 3 | Fixed Canvas & Zero-Flicker Resize | Fixed $1420 \times 760\text{ dp}$ transparent bounding canvas with internal layout animation avoiding swapchain recreation | M1 | Survey |
| 4 | Nudge-ForExpand Algorithm | Automatic screen edge detection and coordinate nudging ($800\text{ ms}$) when expanding in tight screen spaces | M1 | Survey |
| 5 | Drag, Magnetism & Double-Click Reset | 3-phase drag pipeline ($5\text{ px}$ deadzone, $20\text{ px}$ magnetic snap, $60\text{ px}$ clamp, $450\text{ ms}$ double-click reset) | M1 | Survey |
| 6 | Liquid Glass & Visual Effects | `io.github.kyant0:backdrop` v2.0.0 backdrop layers, lens refraction, vibrancy, noise, Skia drop shadow, border glow | M1 | Survey |
| 7 | Expand/Collapse State Machine | Contracted ($300 \times 500\text{ dp}$), File Explorer ($1054 \times 695\text{ dp}$), Settings ($675 \times 695\text{ dp}$), ElasticEase ($800\text{ ms}$), BackEase | M1 | Survey |
| 8 | Tactile Quick Action Buttons | 4 action pills ($56 \times 44\text{ dp}$) + dynamic close menu danger pill, hover/press/checked micro-animations | M1 | Survey |
| 9 | Embedded File Explorer | `LazyVerticalGrid` ($100 \times 105\text{ dp}$ cards), thumbnails, breadcrumb navigation, debounced search ($150\text{ ms}$), pull progress dock | M1 | Survey |
| 10 | Design System & Token Mappings | Complete Dark/Light token matrices, Segoe UI typography scales, corner radii ($34/20/16/12/8\text{ dp}$), elevation tokens | M1 | Survey |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Floating Docked Card Migration Blueprint | Author comprehensive 1:1 parity specification in `UltimateMigrationPlan-WPF-Compose-UI.md` | Survey | DONE |
| 2 | M2: Gate Verification & Forensic Audit | 2 Reviewers, 2 Challengers, and 1 Forensic Auditor gate verification | M1 | DONE |

## Interface Contracts & Layout
- Target File: `W:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md`
- Code Layout: Compose Multiplatform `composeApp` module under `w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin` and `commonMain\kotlin`.
- Libraries: `io.github.kyant0:backdrop:2.0.0`, Compose Multiplatform 1.8.x+, Kotlin Coroutines 1.10.x+, Java AWT / JNA Desktop interop.
