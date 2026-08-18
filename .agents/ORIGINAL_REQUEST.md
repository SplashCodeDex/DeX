# Original User Request

## Initial Request — 2026-08-08T00:59:47Z

Build the UI for the remaining backend architecture features (Trusted Devices Manager, Shared Folders Manager, and Connection Handshake). Implementations must be well-factorized, highly modular, debuggable, and perfectly match the existing reusable design system (no hardcoded UI/UX).

Working directory: W:/CodeDeX/DeX/DeX
Integrity mode: development

## Requirements

### R1. Trusted Devices Menu (Bottom Sheet/Dialog)
Implement a UI (via Bottom Sheet or Dialog Overlay launched from the main view) to list and manage paired devices. Must wire up `DeviceManager.removePairedFingerprint` to allow users to forget trusted devices. Must use existing reusable components.

### R2. Manage Shared Folders Screen (Bottom Sheet/Dialog)
Implement a UI (via Bottom Sheet or Dialog Overlay launched from the main view) to list actively shared SAF folders. Must wire up `SafStorage.removeGrantedFolder` to allow users to revoke folder access. Must use existing reusable components.

### R3. Connection Handshake Flow
Update `MainScreenViewModel.sendHandshake` and the UI interaction layer so that tapping an "Untrusted" device triggers the pairing flow (via `ClientEngine.registerDevice`) instead of instantly opening the file picker.

## Acceptance Criteria

### Execution & Integration
- [ ] Code compiles successfully (`./gradlew assembleDebug` exits with 0).
- [ ] No new IDE inspection warnings or lint errors are introduced (`./gradlew lintDebug` passes).

### Architecture & Modularity
- [ ] UI is implemented strictly using existing design system components (e.g., `DeXPanel`, `DeXButton`, `DeXTextButton`) with zero hardcoded styling (colors, padding, shapes).
- [ ] The Trusted Devices and Manage Folders menus are implemented as Dialogs or Bottom Sheets, avoiding the need for dedicated navigation routing.
- [ ] Clicking a trusted device opens the file picker, while clicking an untrusted device triggers the `sendHandshake` protocol.

## Follow-up — 2026-08-16T22:24:30Z

Use a very large team of agents.

Analyze the existing WPF/C# codebase to extract the exact UI/UX specifications for the floating docked card interface (bottom-right docking above taskbar, expansion, file explorer, quick actions). Formulate a 1:1 visual and UX parity migration guide for Compose Multiplatform and append it to `UltimateMigrationPlan-WPF-Compose-UI.md`.

Working directory: w:\CodeDeX\DeX
Integrity mode: demo

## Requirements

### R1. Analyze the WPF UI Architecture
Examine the existing C# WPF code and tests to understand the exact mechanics of the floating card UI, specifically how it achieves bottom-right docking above the taskbar, handles expand/collapse states, and structures the file explorer and quick action buttons.

### R2. Detail Compose 1:1 Equivalents
Determine the precise Compose Multiplatform techniques and libraries required to achieve absolute 1:1 visual and UX parity. This must include window-level behaviors (transparent, undecorated, docking, always-on-top) and visual effects (liquid glass, shadows, rounded corners).

### R3. Update the Migration Plan
Append a comprehensive, highly detailed specification of these findings to `UltimateMigrationPlan-WPF-Compose-UI.md`, ensuring it serves as a strict blueprint for the eventual implementation.

## Acceptance Criteria

### Documentation Verification
- [ ] `UltimateMigrationPlan-WPF-Compose-UI.md` has been successfully modified to include a dedicated section for the floating card UI parity.
- [ ] The plan explicitly specifies how to achieve exact bottom-right docking (above the taskbar) and window sizing behaviors in Compose Desktop.
- [ ] The plan explicitly recommends pre-built Compose libraries or native interop techniques needed for complex visual effects (e.g., liquid glass/blur backdrop, window transparency).
- [ ] The plan maps the specific WPF quick actions and file explorer layout mechanisms to their Compose Multiplatform equivalents.

## Follow-up — 2026-08-17T00:14:34Z

Implement the complete 1:1 Floating Docked Card UI for DeX in Compose Multiplatform Desktop according to the specifications in `UltimateMigrationPlan-WPF-Compose-UI.md`.

Working directory: w:\CodeDeX\DeX\DeX
Integrity mode: development

## Requirements

### R1. Desktop Window & Shell Architecture
Configure `main.kt` and supporting window classes with:
- Undecorated, transparent, always-on-top Compose `Window`.
- AWT `UTILITY` window type suppression to hide the application from the Windows taskbar.
- Work area calculation (`ScreenBoundsHelper.kt` / `TaskbarWorkAreaProvider`) positioning the card resting 13px from the right edge and 38px above the taskbar.
- Focus loss deactivation listener with the complete 5-point safety guard (`!isPinned`, `!isShowingTransition`, `!isPairingActive`, `!isExpanded`, `!isModalDialogOpen`).
- System tray integration with click-to-toggle visibility and context menu.

### R2. Floating Dock Card & Animation Layer
Implement the core card layout in `FloatingDockCard.kt`, `DockCardContent.kt`, and `MainMenuColumn.kt`:
- Fixed transparent bounding canvas (1420x760 dp) with `Alignment.TopEnd` anchoring to avoid Direct3D swapchain recreation stutter.
- Compact state (300x500 dp) expanding to wide state (1054x695 dp) with `spring(dampingRatio = 0.65f, stiffness = 300f)` physics.
- 3-phase drag pill handler (`DragPillHandle.kt`) supporting dead-zone detection, active drag tracking, magnetic snapping, and double-click reset to resting position.
- Smooth pop-in/pop-out scale (0.85 -> 1.0) and translateY (15 -> 0 dp) animations.

### R3. Quick Actions, Device Lists & Expandable Panels
Build out the UI subcomponents:
- `QuickActionBar.kt` with quick action buttons (DND, Mirror, File Explorer, Clipboard) with press-sink / hover animations.
- Device discovery and paired devices list integrated with `MainScreenViewModel` state.
- `FileExplorerPanel.kt` and `SettingsPanel.kt` drawer panels sliding into view upon card expansion.
- Bottom profile avatar and Exit Engine button with shortcut bindings.

### R4. Visual Styling & Build Verification
Apply visual effects and verify compilation:
- Liquid glass styling using `io.github.kyant0:backdrop` or Skia backdrop blur shader with frosted tint and 34 dp corner radius.
- Verify clean compilation and packaging via Gradle (`./gradlew :composeApp:desktopJar` / `./gradlew :composeApp:compileKotlinDesktop`).

## Acceptance Criteria

### Window & Docking
- [ ] Window launches borderless, transparent, always-on-top, and does not show an icon in the Windows taskbar.
- [ ] Card rests accurately above the Windows taskbar at the bottom-right of the active display.
- [ ] System tray icon toggles card visibility cleanly.
- [ ] Clicking outside auto-dismisses the card unless guarded by pin, animation, pairing, or expanded state.

### UI & Layout Parity
- [ ] Compact card displays drag pill, 4 quick action buttons, status bar, discovered devices, your devices, and profile/exit footer.
- [ ] Drag pill allows repositioning and double-click resets position to default resting coordinates.
- [ ] Triggering File Explorer or Settings expands the card leftwards to reveal the expanded drawer.
- [ ] Visual appearance matches the WPF dark card with rounded corners (34 dp) and glass/frosted styling.

### Build & Compilation
- [ ] Kotlin desktop code compiles cleanly with zero unresolved references (`./gradlew :composeApp:desktopJar` succeeds).


