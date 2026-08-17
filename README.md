# DeX

> # ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️
>
> ## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
> 1. **DESKTOP ONLY.** The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**. The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code** (`composeApp` + shared `core/*` + `feature/*` modules). That is the ONLY target.
> 2. **The Android app (`DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is.
> 3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).
>
> ## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
> 1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason** (including "cleanup", "consolidation", "modularization", "refactoring", "modernization", "housekeeping").
> 2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.
> 3. **Only the USER may decide** when the legacy WPF/C#/PowerShell is archived. Until the user says so, it is untouchable.
> 4. **The ONLY archive procedure** (executed only when the user orders it): move the Compose/Kotlin Multiplatform code **UP one directory to `W:\CodeDeX\`**. Nothing else is archived, removed, or restructured.
> 5. **If in doubt — STOP and ASK. Do not act.**
>
> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays untouched until the user says otherwise.**

## Features
- **Zero-Touch Connection:** Auto-connects when your PC joins your mobile hotspot.
- **Native Windows Share:** Right-click any file in Windows Explorer -> Share -> Send straight to your phone.
- **Pull Downloads:** One-click sync from your phone's `/sdcard/Download` straight to your PC.

## Previews
![Tray UI](images/tray-ui.png)
<!-- ![Share Target](images/share-ui.png) (Coming soon) -->

## Installation (Windows)

Because this app is not yet on the Microsoft Store, it uses a self-signed certificate. Windows will block the installation unless you trust the certificate first.

### Option 1: Auto-Updating Installer (Recommended)
1. Download `CodeDeX.cer` and install it to **Trusted Root Certification Authorities** (see Option 3 below for manual cert install, or run `Install-App.ps1` as Admin once to do it automatically).
2. Download and run `DeX.appinstaller`. 
3. This will install the app and automatically check for updates in the background on future launches!

### Option 2: Scripted Install
1. Download the latest release (`DeX.msix`, `CodeDeX.cer`, and `Install-App.ps1`).
2. Right-click `Install-App.ps1` and select **Run with PowerShell**.
3. Accept the Admin prompt. It will install the certificate and the app automatically.

### Option 3: The Manual Way
1. Download `DeX.msix` and `CodeDeX.cer`.
2. Double-click `CodeDeX.cer`.
3. Click **Install Certificate...**
4. Select **Local Machine** -> Next.
5. Select **Place all certificates in the following store** -> Browse.
6. Select **Trusted Root Certification Authorities** -> OK -> Next -> Finish.
7. Double-click `DeX.msix` to install the app.
