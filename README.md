# DeX

> # ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️
>
> ## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
> 1. **DESKTOP ONLY.** The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**. The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code** (`composeApp` + shared `core/*` + `feature/*` modules). That is the ONLY target.
> 2. **The Android app (`DeX/DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is — it lives ONLY at `W:\CodeDeX\DeX\DeX` and never moves during archiving.
> 3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).
>
> ## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
> 1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason** (including "cleanup", "consolidation", "modularization", "refactoring", "modernization", "housekeeping").
> 2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.
> 3. **Only the USER may decide** when the legacy WPF/C#/PowerShell is archived. Until the user says so, it is untouchable.
> 4. **The ONLY archive procedure** (executed only when the user orders it): move the Compose/Kotlin Multiplatform code **UP one directory — from `W:\CodeDeX\DeX\DeX` to `W:\CodeDeX\DeX`**. The Android app stays at `W:\CodeDeX\DeX\DeX`. Nothing else is archived, removed, or restructured.
> 5. **If in doubt — STOP and ASK. Do not act.**
>
> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays untouched until the user says otherwise. Android stays at `W:\CodeDeX\DeX\DeX`.**

## Overview

DeX is a cross-platform desktop utility that connects your PC to your phone — one shared **Kotlin + Compose Multiplatform** codebase for **Windows and macOS**.

## Features
- **Zero-Touch Connection:** Auto-connects when your PC joins your mobile hotspot.
- **Native Sharing:** Right-click any file in Explorer/Finder -> Share -> Send straight to your phone.
- **Pull Downloads:** One-click sync from your phone's `/sdcard/Download` straight to your PC.
- **Floating Dock Card UI:** Bottom-right docked card (Windows) / menu-bar card (macOS) with liquid glass, device lists, file explorer, PIN/QR pairing, and screen mirror.

## Building (Desktop — Windows & macOS)

Prerequisites: JDK 17+.

```bash
# Build
./gradlew :composeApp:desktopJar

# Run
./gradlew :composeApp:run

# Tests (58 desktop tests)
./gradlew :composeApp:desktopTest

# Package
./gradlew :composeApp:createDistributable   # runnable app
./gradlew :composeApp:packageMsi            # Windows installer
./gradlew :composeApp:packageDmg            # macOS disk image
```

## Android App

The Android companion app is a **separate standalone project** in [`DeX/`](DeX/) — open `DeX/` in Android Studio, or:

```bash
cd DeX
./gradlew :app:assembleDebug
```

## Legacy Implementation

The retired WPF / C# / PowerShell implementation lives (read-only) in [`Archived_Legacy_WPF/`](Archived_Legacy_WPF/) — do not modify, delete, or restore it.
