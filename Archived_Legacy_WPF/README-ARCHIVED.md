# Archived Legacy WPF/C#/PowerShell — READ-ONLY

This directory contains the **retired legacy implementation** of DeX:

- `MSIX_Source/` — WPF UI + PowerShell engine (MainWindow.xaml, Connect-Engine.ps1, 16 modules)
- `DeXShareTarget/`, `DeXShareTarget.Tests/`, `DeXShareTarget.UITests/` — C# .NET transfer engine + tests
- `DeXLiveDebugger/` — C# chaos debugger
- `Tests/` — Pester unit tests
- `PackMSIX.ps1`, `SignMSIX.ps1`, `Install-App.ps1`, `Validate-Build.ps1` — legacy packaging toolchain
- `*.msix`, `*.appinstaller`, `*.cer`, `*.pfx` — legacy install artifacts
- `images/` — legacy WPF UI screenshots

## STATUS: RETIRED (2026-08-18, by explicit user order)

- The legacy implementation was replaced by the **desktop Kotlin + Compose Multiplatform app** at the repo root (`composeApp/`, `core/`, `feature/`).
- **READ-ONLY ARCHIVE.** Do NOT modify, refactor, "fix", or restore anything in this directory.
- Do NOT delete or remove this directory — the user decides its final fate.
- The desktop app and the Android app (`DeX/`) do NOT reference anything in this directory.
